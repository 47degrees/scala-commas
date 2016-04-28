/*
 * commas, Copyright 2016 Andy Scott
 */

package fail.sauce.commas

import scala.tools.nsc.{ Global ⇒ NscGlobal, SubComponent, Phase }
import scala.tools.nsc.plugins.{ Plugin ⇒ NscPlugin }
import scala.tools.nsc.ast.parser.{ SyntaxAnalyzer ⇒ NscSyntaxAnalyzer }

/** Trailing comma plugin. */
class TrailingCommaPlugin(val global: NscGlobal)
    extends NscPlugin with PhaseJacking {

  override val name = "trailing-commas"
  override val description = "adds support for trailing commas"
  override val components = Nil

  {
    val newSyntaxAnalyzer = new TrailingCommaSyntaxAnalyzer(global)

    // reflection is safe... right?
    hijackField("syntaxAnalyzer", newSyntaxAnalyzer)
    hijackPhase("parser", newSyntaxAnalyzer)

    if (global.syntaxAnalyzer != newSyntaxAnalyzer)
      sys.error("failed to hijack parser")
  }
}

/** Our customized syntax analyzer phase */
private[sauce] class TrailingCommaSyntaxAnalyzer(val global: NscGlobal)
    extends NscSyntaxAnalyzer {

  override val runsAfter = List[String]()
  override val runsRightAfter = None

  import global._

  // It would be great to override newUnitParser, except you can't. So
  // instead we override newPhase and copy all the transitive code until
  // newUnitParser is called.
  def newUnitParser(unit: CompilationUnit) = new UnitParser(unit) with TCParser

  // ripped/copied, see above
  override def newPhase(prev: Phase): StdPhase = new ParserPhase(prev)

  // ripped/copied, see above
  private[this] def initialUnitBody(unit: CompilationUnit): Tree = {
    if (unit.isJava) new JavaUnitParser(unit).parse()
    else if (currentRun.parsing.incompleteHandled) newUnitParser(unit).parse()
    else newUnitParser(unit).smartParse()
  }

  // ripped/copied, see above
  private[this] class ParserPhase(prev: Phase) extends StdPhase(prev) {
    override val checkable = false
    override val keepsTypeParams = false
    def apply(unit: CompilationUnit) {
      informProgress("parsing " + unit)
      if (unit.body == EmptyTree) unit.body = initialUnitBody(unit)
      if (settings.Yrangepos && !reporter.hasErrors) validatePositions(unit.body)
      if (settings.Ymemberpos.isSetByUser)
        new MemberPosReporter(unit) show (style = settings.Ymemberpos.value)
    }
  }

  /** Our changes to the Scala parser */
  sealed trait TCParser { self: Parser ⇒

    import scala.tools.nsc.ast.parser.Tokens._
    import scala.collection.mutable.ListBuffer

    // The basis for trait is Paul Phillip's fork of Scala, located at
    //  https://github.com/paulp/policy

    // trailing commas for arguments
    override def argumentExprs(): List[Tree] = {
      def args(): List[Tree] = commaSeparatedOrTrailing(
        RPAREN,
        if (isIdent) treeInfo.assignmentToMaybeNamedArg(expr()) else expr()
      )
      in.token match {
        case LBRACE ⇒ List(blockExpr())
        case LPAREN ⇒ inParens(if (in.token == RPAREN) Nil else args())
        case _      ⇒ Nil
      }
    }

    // trailing commas for imports selectors
    override def importSelectors(): List[ImportSelector] = {
      val selectors = inBracesOrNil(commaSeparatedOrTrailing(
        RBRACE, importSelector()))
      selectors.init foreach {
        case ImportSelector(nme.WILDCARD, pos, _, _) ⇒
          syntaxError(pos, "Wildcard import must be in last position")
        case _ ⇒ ()
      }
      selectors
    }

    // This is from Paul
    @inline private[this] final def commaSeparatedOrTrailing[T](
      end: Token, part: ⇒ T
    ): List[T] = {
      val ts = ListBuffer[T](part)
      while (in.token == COMMA) {
        accept(COMMA)
        if (in.token == end) // that was a trailing comma
          return ts.toList
        else
          ts += part
      }
      ts.toList
    }

  }

}

/** Hijacking helper for compiler phases. */
private[sauce] sealed trait PhaseJacking { self: NscPlugin ⇒

  /** Hijack a field from global */
  protected[this] final def hijackField[T](name: String, newValue: T): T = {
    val field = classOf[NscGlobal].getDeclaredField(name)
    field.setAccessible(true)
    val oldValue = field.get(global).asInstanceOf[T]
    field.set(global, newValue)
    oldValue
  }

  /** Hijack a phase from global */
  protected[this] final def hijackPhase(
    name: String, newPhase: SubComponent
  ): Option[SubComponent] = {

    val phasesSet = classOf[NscGlobal].getDeclaredMethod("phasesSet")
      .invoke(global).asInstanceOf[scala.collection.mutable.Set[SubComponent]]

    phasesSet.find(_.phaseName == name).map { oldPhase ⇒
      phasesSet -= oldPhase
      phasesSet += newPhase
      oldPhase
    }
  }

}
