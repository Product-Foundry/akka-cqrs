package com.productfoundry.support

import java.util.UUID

import akka.actor.ActorSystem
import akka.testkit.ImplicitSender
import akka.testkit.TestKit
import com.typesafe.config.ConfigFactory
import org.specs2.mutable.SpecificationLike

abstract class PersistentActorSpec
  extends TestKit(ActorSystem("system", PersistentActorSpec.memoryConfig))
          with ImplicitSender
          with SpecificationLike {

  def randomPersistenceId = UUID.randomUUID().toString
}

object PersistentActorSpec {

  val memoryConfig = ConfigFactory.parseString(
    s"""
      akka.actor.serialize-creators = off
      akka.actor.serialize-messages = off
      akka.persistence.publish-confirmations = on
      akka.persistence.publish-plugin-commands = on
      akka.persistence.journal.plugin = "akka.persistence.journal.inmem"
      akka.test.single-expect-default = 2s
      akka.persistence.view.auto-update-interval = 0.2s
     """
  )
}

