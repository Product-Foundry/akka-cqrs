package akka.cqrs

import sbt.Keys._
import sbt._

import com.typesafe.sbt.SbtMultiJvm.MultiJvmKeys.MultiJvm

object MultiJvmSettings {

  val settings = Seq(
    compile in MultiJvm <<= (compile in MultiJvm) triggeredBy (compile in Test),

    parallelExecution in Test := false,

    executeTests in Test <<= (executeTests in Test, executeTests in MultiJvm) map {
      case (testResults, multiNodeResults)  =>
        val overall =
          if (testResults.overall.id < multiNodeResults.overall.id)
            multiNodeResults.overall
          else
            testResults.overall
        Tests.Output(overall,
          testResults.events ++ multiNodeResults.events,
          testResults.summaries ++ multiNodeResults.summaries)
    }
  )
}
