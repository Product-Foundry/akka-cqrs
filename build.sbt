import bintray.Keys._
import com.typesafe.sbt.SbtGit._

organization := "com.productfoundry"

name := "akka-cqrs"

version := "0.1"

// Git

versionWithGit

git.baseVersion := "0.1"

// Bintray

bintrayPublishSettings

repository in bintray := "maven"

licenses += ("Apache-2.0", url("https://www.apache.org/licenses/LICENSE-2.0.html"))

bintrayOrganization in bintray := Some("productfoundry")

// Resolvers

resolvers += "Typesafe repository" at "http://repo.typesafe.com/typesafe/releases/"


// Build

scalaVersion := "2.11.5"

fork in Test := true

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
)

parallelExecution in Test := false

libraryDependencies ++= Seq(
  "com.typesafe.akka"      %% "akka-persistence-experimental"     % "2.3.9",
  "com.typesafe.akka"      %% "akka-testkit"                      % "2.3.9",
  "org.scala-stm"          %% "scala-stm"                         % "0.7",
  "org.scalaz"             %% "scalaz-core"                       % "7.0.6",
  "org.specs2"             %% "specs2-core"                       % "2.3.13"   % "test",
  "org.specs2"             %% "specs2-mock"                       % "2.3.13"   % "test",
  "org.specs2"             %% "specs2-matcher-extra"              % "2.3.13"   % "test",
  "org.specs2"             %% "specs2-scalacheck"                 % "2.3.13"   % "test",
  "org.scalacheck"         %% "scalacheck"                        % "1.11.6"   % "test"
)

