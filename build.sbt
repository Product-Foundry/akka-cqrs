import bintray.Keys._
import com.typesafe.sbt.SbtGit._
import sbt.Keys._

lazy val commonSettings = Seq(
  organization := "com.productfoundry",
  version := "0.1.4",

  scalaVersion := "2.11.5",

  scalacOptions ++= Seq(
    "-encoding", "UTF-8",
    "-feature",
    "-unchecked",
    "-deprecation",
    "-Xlint",
    "-Yno-adapted-args",
    "-Ywarn-dead-code",
    "-Ywarn-numeric-widen",
    "-Yinline",
    "-Xfuture"
  ),

  // Bintray
  repository in bintray := "maven",
  licenses += ("Apache-2.0", url("https://www.apache.org/licenses/LICENSE-2.0.html")),
  bintrayOrganization in bintray := Some("productfoundry"),

  // Test execution
  parallelExecution in Test := false,
  fork in Test := true,

  // Resolvers
  resolvers += "Typesafe repository" at "http://repo.typesafe.com/typesafe/releases/",

  // Dependencies
  libraryDependencies ++= Seq(
    "com.typesafe.akka"      %% "akka-persistence-experimental"     % "2.3.9",
    "com.typesafe.akka"      %% "akka-testkit"                      % "2.3.9",
    "org.scala-stm"          %% "scala-stm"                         % "0.7",
    "org.scalaz"             %% "scalaz-core"                       % "7.0.6"    % "optional",
    "org.scalatest"          %% "scalatest"                         % "2.2.4"    % "test",
    "com.typesafe.akka"      %% "akka-testkit"                      % "2.3.9"    % "test",
    "org.scalacheck"         %% "scalacheck"                        % "1.12.2"   % "test"
  )
)

lazy val root = (project in file("."))
  .aggregate(core, test)
  .settings(commonSettings: _*)
  .settings(
    name := "akka-cqrs-root"
  )
  .settings(bintrayPublishSettings: _*)

lazy val core = project
  .settings(commonSettings: _*)
  .settings(
    name := "akka-cqrs"
  )
  .settings(bintrayPublishSettings: _*)

lazy val test = project
  .dependsOn(core)
  .settings(commonSettings: _*)
  .settings(
    name := "akka-cqrs-test",
    libraryDependencies ++= Seq(
      "org.scalatest"          %% "scalatest"                         % "2.2.4",
      "com.typesafe.akka"      %% "akka-testkit"                      % "2.3.9",
      "org.scalacheck"         %% "scalacheck"                        % "1.12.2"
    )
  )
  .settings(bintrayPublishSettings: _*)

