/*
 * commas, copyright 2016 Andy Scott
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */


val commas = (project in file("."))

name               := "commas"
scalaVersion       := "2.11.7"

homepage           :=
  Some(url("https://github.com/andyscott/scala-commas"))

libraryDependencies <++= scalaVersion(scalaVersion => Seq(
  "org.scala-lang" % "scala-compiler" % scalaVersion
))

scalacOptions in console in Compile += "-Xplugin:" + (packageBin in Compile).value
scalacOptions in Test += "-Xplugin:" + (packageBin in Compile).value


publishMavenStyle       := true
publishArtifact in Test := false
pomIncludeRepository    := Function.const(false)

publishTo := Some(if (isSnapshot.value) Opts.resolver.sonatypeSnapshots else Opts.resolver.sonatypeStaging)

pomExtra := (
  <scm>
    <url>git@github.com:andyscott/scala-commas.git</url>
    <connection>scm:git:git@github.com:andyscott/scala-commas.git</connection>
  </scm>
  <developers>
    <developer>
      <id>andyscott</id>
      <name>Andy Scott</name>
      <url>http://github.com/andyscott/</url>
    </developer>
  </developers>
)
