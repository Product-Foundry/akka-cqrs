package com.productfoundry.support

import java.util.UUID

import akka.testkit.{ImplicitSender, TestKit}
import org.scalatest.concurrent.Eventually
import org.scalatest.time.{Millis, Span}
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}

import scala.concurrent.Await
import scala.concurrent.duration._

abstract class PersistenceTestSupport
  extends TestKit(TestConfig.testSystem)
  with ImplicitSender
  with WordSpecLike
  with Matchers
  with BeforeAndAfterAll
  with Eventually {

  def randomPersistenceId = UUID.randomUUID.toString

  implicit override val patienceConfig = PatienceConfig(
    timeout = scaled(Span(500, Millis)),
    interval = scaled(Span(10, Millis))
  )

  override protected def afterAll(): Unit = {
    TestKit.shutdownActorSystem(system)
    Await.result(system.whenTerminated, 10.seconds)
  }
}
