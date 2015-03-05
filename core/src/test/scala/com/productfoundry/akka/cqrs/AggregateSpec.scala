package com.productfoundry.akka.cqrs

import akka.actor.{ActorRef, Props}
import akka.testkit.{ImplicitSender, TestKit}
import com.productfoundry.akka.PassivationConfig
import com.productfoundry.support.TestConfig
import org.scalatest.concurrent.Eventually
import org.scalatest.time.{Millis, Second, Span}
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}

class AggregateSpec
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

  implicit val entitySystem = new LocalDomainContext(system)

  override protected def afterAll(): Unit = {
    TestKit.shutdownActorSystem(system)
    system.awaitTermination()
  }

  "Aggregate" must {

    "be created" in new TestAggregateFixture {
      supervisor ! TestAggregate.Create(AggregateId.generate())
      expectMsgType[AggregateStatus.Success]
    }

    "update aggregate revision on create" in new TestAggregateFixture {
      supervisor ! TestAggregate.Create(AggregateId.generate())
      val commitResult = expectMsgType[AggregateStatus.Success].result
      assert(commitResult.aggregateRevision === AggregateRevision.Initial.next)
    }

    "update aggregate revision on update" in new TestAggregateFixture {
      val entityId = AggregateId.generate()
      supervisor ! TestAggregate.Create(entityId)
      expectMsgType[AggregateStatus.Success]

      supervisor ! TestAggregate.Count(entityId)
      val commitResult = expectMsgType[AggregateStatus.Success].result
      assert(commitResult.aggregateRevision === AggregateRevision.Initial.next.next)
    }

    "update aggregate state" in new TestAggregateFixture {
      val entityId = AggregateId.generate()
      supervisor ! TestAggregate.Create(entityId)
      expectMsgType[AggregateStatus.Success]

      supervisor ! TestAggregate.GetCount(entityId)
      expectMsg(0)

      supervisor ! TestAggregate.Count(entityId)
      expectMsgType[AggregateStatus.Success]

      supervisor ! TestAggregate.GetCount(entityId)
      expectMsg(1)
    }
  }

  trait TestAggregateFixture {
    implicit object TestAggregateFactory extends AggregateFactory[TestAggregate] {
      override def props(config: PassivationConfig): Props = {
        Props(new TestAggregate(config))
      }
    }

    implicit val supervisorFactory = entitySystem.entitySupervisorFactory[TestAggregate]

    val supervisor: ActorRef = EntitySupervisor.forType[TestAggregate]
  }
}
