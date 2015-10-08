import bintray.Keys._
import sbt.Keys._

lazy val commonSettings = Seq(
  organization := "com.productfoundry",
  version := "0.1.24",

  scalaVersion := "2.11.7",

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
  resolvers += "Typesafe repository" at "http://repo.typesafe.com/typesafe/releases/"
)

lazy val akkaVersion = "2.3.14"

lazy val root = (project in file("."))
  .aggregate(inmem, core, test)
  .settings(commonSettings: _*)
  .settings(
    name := "akka-cqrs-root"
  )
  .settings(bintrayPublishSettings: _*)

lazy val inmem = project
  .settings(commonSettings: _*)
  .settings(
    name := "akka-cqrs-inmem",

    libraryDependencies ++= Seq(
      "com.typesafe.akka"      %% "akka-persistence-experimental"     % akkaVersion,
      "com.typesafe.akka"      %% "akka-persistence-tck-experimental" % akkaVersion % "test",
      "org.scalatest"          %% "scalatest"                         % "2.1.4"     % "test"
    )
  )
  .settings(bintrayPublishSettings: _*)

lazy val core = project
  .dependsOn(inmem)
  .settings(commonSettings: _*)
  .settings(
    name := "akka-cqrs",

    libraryDependencies ++= Seq(
      "com.typesafe.akka"      %% "akka-persistence-experimental"     % akkaVersion,
      "com.typesafe.akka"      %% "akka-cluster"                      % akkaVersion,
      "com.typesafe.play"      %% "play-json"                         % "2.4.3",
      "org.scala-stm"          %% "scala-stm"                         % "0.7",
      "org.scalaz"             %% "scalaz-core"                       % "7.0.6"     % "optional",
      "org.scalatest"          %% "scalatest"                         % "2.2.4"     % "test",
      "com.typesafe.akka"      %% "akka-testkit"                      % akkaVersion % "test",
      "org.scalacheck"         %% "scalacheck"                        % "1.12.2"    % "test"
    )
  )
  .settings(bintrayPublishSettings: _*)

lazy val test = project
  .dependsOn(core)
  .settings(commonSettings: _*)
  .settings(
    name := "akka-cqrs-test",

    libraryDependencies ++= Seq(
      "org.scalatest"          %% "scalatest"                         % "2.2.4",
      "com.typesafe.akka"      %% "akka-testkit"                      % akkaVersion,
      "org.scalacheck"         %% "scalacheck"                        % "1.12.2"
    )
  )
  .settings(bintrayPublishSettings: _*)

