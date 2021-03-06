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
      |  actor {
      |    serialize-creators = off
      |    serialize-messages = off
      |  }
      |  test {
      |    single-expect-default = 1s
      |  }
      |}
    """.stripMargin
  )

  def testSystem = {
    ActorSystem("Tests", config)
  }
}
