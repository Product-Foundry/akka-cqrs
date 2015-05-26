package com.productfoundry.support

import akka.actor.ActorSystem
import com.typesafe.config.ConfigFactory

object TestConfig {
  val config = ConfigFactory.parseString(
    """
      |akka {
      |  loglevel = OFF
      |  persistence {
      |    journal {
      |      plugin = "in-memory-journal"
      |    }
      |    snapshot-store {
      |      plugin = "in-memory-snapshot-store"
      |    }
      |  }
      |}
    """.stripMargin
  )

  def testSystem = {
    ActorSystem("Tests", config)
  }
}
