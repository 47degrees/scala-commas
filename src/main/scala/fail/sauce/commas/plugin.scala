/*
 * commas, Copyright 2016 Andy Scott
 */

package fail.sauce.commas

import scala.tools.nsc.{ Global ⇒ NscGlobal, SubComponent, Phase }
import scala.tools.nsc.plugins.{ Plugin ⇒ NscPlugin }
import scala.tools.nsc.ast.parser.{ SyntaxAnalyzer ⇒ NscSyntaxAnalyzer }

class TrailingCommaPlugin(val global: NscGlobal)
    extends NscPlugin with PhaseJacking {
  override val name = "trailing-commas"
  override val description = "adds support for trailing commas"

  override val components = Nil

  val newSyntaxAnalyzer = new TrailingCommaSyntaxAnalyzer(global)

  hijackField("syntaxAnalyzer", newSyntaxAnalyzer)
  hijackPhase("parser", newSyntaxAnalyzer)

  if (global.syntaxAnalyzer != newSyntaxAnalyzer)
    sys.error("failed to hijack parser")
}

private[sauce] class TrailingCommaSyntaxAnalyzer(val global: NscGlobal)
    extends NscSyntaxAnalyzer {

  val runsAfter = List[String]()
  val runsRightAfter = None

  import global._

  // a lot of this is ripped... all so that we can override this method
  def newUnitParser(unit: CompilationUnit) = new UnitParser(unit) with TCParser

  // ripped
  override def newPhase(prev: Phase): StdPhase = new ParserPhase(prev)

  // ripped
  private def initialUnitBody(unit: CompilationUnit): Tree = {
    if (unit.isJava) new JavaUnitParser(unit).parse()
    else if (currentRun.parsing.incompleteHandled) newUnitParser(unit).parse()
    else newUnitParser(unit).smartParse()
  }

  // ripped
  private class ParserPhase(prev: Phase) extends StdPhase(prev) {
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

  sealed trait TCParser { self: Parser ⇒

    // The basis for trait is Paul Phillip's fork of Scala, located at
    //  https://github.com/paulp/policy

    import scala.tools.nsc.ast.parser.Tokens._
    import scala.collection.mutable.ListBuffer

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

    // trailing commas for imports clauses

    // trailing commas for imports selectors
    override def importSelectors(): List[ImportSelector] = {
      val selectors = inBracesOrNil(commaSeparatedOrTrailing(RBRACE, importSelector()))
      selectors.init foreach {
        case ImportSelector(nme.WILDCARD, pos, _, _) ⇒ syntaxError(pos, "Wildcard import must be in last position")
        case _                                       ⇒ ()
      }
      selectors
    }

    // This is from Paul
    @inline private[this] final def commaSeparatedOrTrailing[T](end: Token, part: ⇒ T): List[T] = {
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

/** Hijacking compiler phases.
  */
private[sauce] sealed trait PhaseJacking { self: NscPlugin ⇒

  import scala.collection.mutable.{ Set ⇒ MutableSet }

  private lazy val globalClass: Class[_] = classOf[NscGlobal]

  def hijackField[T](name: String, newValue: T): T = {
    val field = globalClass.getDeclaredField(name)
    field.setAccessible(true)
    val oldValue = field.get(global).asInstanceOf[T]
    field.set(global, newValue)
    oldValue
  }

  private lazy val phasesSetMapGetter =
    classOf[NscGlobal].getDeclaredMethod("phasesSet")
  private lazy val phasesSet =
    phasesSetMapGetter.invoke(global).asInstanceOf[MutableSet[SubComponent]]

  def hijackPhase(name: String, newPhase: SubComponent): Option[SubComponent] =
    phasesSet.find(_.phaseName == name).map { oldPhase ⇒
      phasesSet -= oldPhase
      phasesSet += newPhase
      oldPhase
    }

}
