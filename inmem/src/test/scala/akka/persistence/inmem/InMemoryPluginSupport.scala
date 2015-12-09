package akka.persistence.inmem

import com.typesafe.config.ConfigFactory

object InMemoryPluginSupport {

  lazy val config = ConfigFactory.parseString(
    """
      |akka.persistence.journal.plugin = "in-memory-journal"
      |akka.persistence.snapshot-store.plugin = "in-memory-snapshot-store"
      |akka.test.single-expect-default = 1s
      |akka.loglevel=ERROR
    """.stripMargin)
}