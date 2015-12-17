package com.productfoundry.akka.cqrs

import java.util.UUID

import akka.actor.ActorSystem
import com.typesafe.config.ConfigFactory

object TestConfig {

  /**
    * Akka configuration for testing.
    */
  val config = ConfigFactory.parseString(
    s"""
       |akka {
       |  persistence {
       |    journal {
       |      plugin = "in-memory-journal"
       |    }
       |    snapshot-store {
       |      plugin = "in-memory-snapshot-store"
       |    }
       |  }
       |
       |  loggers = [
       |    "akka.testkit.TestEventListener",
       |  ]
       |
       |  loglevel = "ERROR"
       |}
    """.stripMargin
  )

  def system = {
    ActorSystem(s"Test-${UUID.randomUUID()}", config)
  }
}
