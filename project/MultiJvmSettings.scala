import sbt.Keys._
import sbt._

import com.typesafe.sbt.SbtMultiJvm.MultiJvmKeys.MultiJvm

object MultiJvmSettings {

  val settings = Seq(
    compile in MultiJvm <<= (compile in MultiJvm) triggeredBy (compile in Test),

    parallelExecution in Test := false
  )
}
