package com.productfoundry.support

import akka.testkit.{ImplicitSender, TestKit}
import com.productfoundry.akka.cqrs.LocalDomainContext
import org.scalatest.concurrent.Eventually
import org.scalatest.time.{Millis, Second, Span}
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}

abstract class AggregateTestSupport
  extends TestKit(TestConfig.testSystem)
  with ImplicitSender
  with WordSpecLike
  with Matchers
  with BeforeAndAfterAll
  with Eventually {

  implicit override val patienceConfig = PatienceConfig(
    timeout = scaled(Span(1, Second)),
    interval = scaled(Span(10, Millis))
  )

  implicit val domainContext = new LocalDomainContext(system)

  override protected def afterAll(): Unit = {
    TestKit.shutdownActorSystem(system)
    system.awaitTermination()
  }
}
