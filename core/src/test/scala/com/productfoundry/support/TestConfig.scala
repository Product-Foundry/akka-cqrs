package com.productfoundry.support

import akka.actor.ActorSystem
import com.typesafe.config.ConfigFactory

object TestConfig {
  val config = ConfigFactory.parseString(
    """
    akka.loggers = ["akka.testkit.TestEventListener"]
    akka.persistence.journal.plugin = "akka.persistence.journal.inmem"
    """
  )

  def testSystem = {
    ActorSystem("Tests", config)
  }
}
