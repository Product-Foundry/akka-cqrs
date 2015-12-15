package akka.cqrs

import bintray.BintrayKeys._
import com.typesafe.sbt.SbtMultiJvm.MultiJvmKeys.MultiJvm
import sbt.Keys._
import sbt._

object AkkaCqrsBuild extends Build {

  lazy val buildSettings = Dependencies.Versions ++ Seq(
    organization := "com.productfoundry",
    version := "0.1.37-SNAPSHOT"
  )

  lazy val root = Project(
    id = "akka-cqrs-root",
    base = file("."),
    settings = parentSettings,
    aggregate = Seq(inmem, core, cluster, test)
  )

  lazy val inmem = Project(
    id = "akka-cqrs-inmem",
    base = file("inmem")
  )

  lazy val core = Project(
    id = "akka-cqrs",
    base = file("core"),
    dependencies = Seq(inmem)
  )

  lazy val cluster = Project(
    id = "akka-cqrs-cluster",
    base = file("cluster"),
    dependencies = Seq(inmem, core)
  ).configs(MultiJvm)

  lazy val test = Project(
    id = "akka-cqrs-test",
    base = file("test"),
    dependencies = Seq(inmem, core, cluster)
  )

  override lazy val settings =
    super.settings ++
      buildSettings ++
      Seq(
        shellPrompt := { s => Project.extract(s).currentProject.id + " > " }
      ) ++
      resolverSettings

  lazy val baseSettings = Defaults.coreDefaultSettings

  lazy val bintraySettings = Seq(
    bintrayOrganization in bintray := Some("productfoundry")
  )

  lazy val parentSettings = baseSettings ++ Seq(
    publishArtifact := false
  )

  lazy val resolverSettings = {
    resolvers ++= Seq(
      "Typesafe repository" at "http://repo.typesafe.com/typesafe/releases/"
    )
  }

  lazy val defaultSettings = resolverSettings ++
    bintraySettings ++
    Protobuf.settings ++
    MultiJvmSettings.settings ++
    Seq(
      scalacOptions in Compile ++= Seq("-encoding", "UTF-8", "-target:jvm-1.8", "-feature", "-unchecked", "-Xlog-reflective-calls", "-Xlint"),
      javacOptions in compile ++= Seq("-encoding", "UTF-8", "-source", "1.8", "-target", "1.8", "-Xlint:unchecked"),
      javacOptions in doc ++= Seq("-encoding", "UTF-8", "-source", "1.8"),

      scalaVersion := "2.11.7",

      parallelExecution in Test := false,

      fork in Test := true
    )
}