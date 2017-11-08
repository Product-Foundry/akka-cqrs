import sbt._

import scala.language.postfixOps

lazy val releaseVersion: String = "1"

lazy val resolverSettings = Seq(
  resolvers ++= Seq(
    "Typesafe repository" at "http://repo.typesafe.com/typesafe/releases/"
  ),

  // Experimental http://www.scala-sbt.org/0.13/docs/Cached-Resolution.html
  updateOptions := updateOptions.value.withCachedResolution(true)
)

lazy val compilerSettings = Seq(
  scalaVersion := "2.11.11",
  scalacOptions in Compile ++= Seq("-encoding", "UTF-8", "-target:jvm-1.8", "-feature", "-unchecked", "-deprecation", "-Xlint"),
  javacOptions in compile ++= Seq("-encoding", "UTF-8", "-source", "1.8", "-target", "1.8", "-Xlint:unchecked"),
  javacOptions in doc ++= Seq("-encoding", "UTF-8", "-source", "1.8")
)

lazy val bintraySettings = Seq(
  licenses += ("Apache-2.0", url("https://www.apache.org/licenses/LICENSE-2.0.html")),
  bintrayOrganization := Some("productfoundry")
)

lazy val defaultSettings =
  resolverSettings ++
    compilerSettings ++
    bintraySettings ++
    Defaults.itSettings ++
    Protobuf.settings ++
    Dependencies.versions ++
    Seq(
      version := releaseVersion,

      organization := "com.productfoundry",

      shellPrompt := { s => Project.extract(s).currentProject.id + " > " },

      fork := true,

      parallelExecution in Global := false
    )

lazy val inmem = (project in file("inmem"))
  .copy(id = "akka-cqrs-inmem")
  .settings(defaultSettings)
  .settings(Dependencies.inmem)

lazy val core = (project in file("core"))
  .copy(id = "akka-cqrs")
  .settings(defaultSettings)
  .settings(Dependencies.core)
  .dependsOn(inmem)

lazy val cluster = (project in file("cluster"))
  .copy(id = "akka-cqrs-cluster")
  .configs(MultiJvm)
  .settings(defaultSettings)
  .settings(MultiJvmSettings.settings)
  .settings(Dependencies.cluster)
  .dependsOn(inmem, core)

lazy val test = (project in file("test"))
  .copy(id = "akka-cqrs-test")
  .settings(defaultSettings)
  .settings(Dependencies.test)
  .dependsOn(inmem, core, cluster)

lazy val projects = Seq(
  inmem,
  core,
  cluster,
  test
)

lazy val root = (project in file("."))
  .copy(id = "akka-cqrs-root")
  .settings(defaultSettings)
  .dependsOn(projects.map(project => Project.classpathDependency(project)(Project.projectToRef)): _*)
  .aggregate(projects.map(Project.projectToRef): _*)
