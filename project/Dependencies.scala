package akka.cqrs

import sbt._
import Keys._

object Dependencies {

  lazy val akkaVersion = settingKey[String]("The version of Akka to use.")

  val Versions = Seq(
    akkaVersion := "2.4.6"
  )

  object Compile {

    val akkaPersistence = Def.setting { "com.typesafe.akka" %% "akka-persistence" % akkaVersion.value }

    val akkaSlf4j = Def.setting { "com.typesafe.akka" %% "akka-slf4j" % akkaVersion.value }

    val akkaCluster = Def.setting { "com.typesafe.akka" %% "akka-cluster" % akkaVersion.value }

    val akkaClusterSharding = Def.setting { "com.typesafe.akka" %% "akka-cluster-sharding" % akkaVersion.value }

    val akkaClusterTools = Def.setting { "com.typesafe.akka" %% "akka-cluster-tools" % akkaVersion.value }

    val protobuf = Def.setting { "com.google.protobuf" % "protobuf-java" % Protobuf.protocVersion.value }

    val stm = "org.scala-stm" %% "scala-stm" % "0.7"

  }

  object Test {

    val akkaPersistenceTck = Def.setting { "com.typesafe.akka" %% "akka-persistence-tck" % akkaVersion.value  % "test" }

    val akkaTestkit = Def.setting { "com.typesafe.akka" %% "akka-testkit" % akkaVersion.value  % "test" }

    val akkaMultiNodeTestkit = Def.setting { "com.typesafe.akka" %% "akka-multi-node-testkit" % akkaVersion.value % "test" }

    val leveldb = "org.iq80.leveldb" % "leveldb" % "0.7" % "test"

    val leveldbjni = "org.fusesource.leveldbjni" % "leveldbjni-all" % "1.8"

    val logback = "ch.qos.logback" % "logback-classic" % "1.1.3"

    val scalaCheck = "org.scalacheck" %% "scalacheck" % "1.12.5" % "test"

    val scalaTest = "org.scalatest" %% "scalatest" % "2.2.4" % "test"

  }

  import Compile._

  val l = libraryDependencies

  val inmem = l ++= Seq(akkaPersistence.value, akkaSlf4j.value, Test.akkaPersistenceTck.value, Test.scalaTest)

  val core = l ++= Seq(akkaPersistence.value, akkaSlf4j.value, protobuf.value, stm, Test.akkaTestkit.value, Test.scalaTest, Test.scalaCheck)

  val cluster = l ++= Seq(akkaCluster.value, akkaSlf4j.value, akkaClusterSharding.value, akkaClusterTools.value, Test.akkaTestkit.value, Test.akkaMultiNodeTestkit.value, Test.scalaTest, Test.scalaCheck, Test.leveldb, Test.leveldbjni, Test.logback)

  val test = l ++= Seq(Test.akkaTestkit.value.copy(configurations = Some("compile")), Test.scalaTest.copy(configurations = Some("compile")), Test.scalaCheck.copy(configurations = Some("compile")))

}