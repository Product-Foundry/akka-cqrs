package com.productfoundry.akka.cqrs

import akka.actor.{Status, ActorRef, Props}
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

  implicit val domainContext = new LocalDomainContext(system)

  implicit object TestAggregateFactory extends AggregateFactory[TestAggregate] {
    override def props(config: PassivationConfig): Props = {
      Props(new TestAggregate(config))
    }
  }

  implicit val supervisorFactory = domainContext.entitySupervisorFactory[TestAggregate]

  val supervisor: ActorRef = EntitySupervisor.forType[TestAggregate]

  override protected def afterAll(): Unit = {
    TestKit.shutdownActorSystem(system)
    system.awaitTermination()
  }

  "Aggregate creation" must {

    "succeed" in {
      supervisor ! TestAggregate.Create(TestId.generate())
      expectMsgType[AggregateStatus.Success]
    }

    "have initial revision" in {
      supervisor ! TestAggregate.Create(TestId.generate())

      val success = expectMsgType[AggregateStatus.Success]
      success.result.aggregateRevision should be(AggregateRevision.Initial.next)
    }

    "fail for existing" in {
      val id = TestId.generate()

      supervisor ! TestAggregate.Create(id)
      expectMsgType[AggregateStatus.Success]

      supervisor ! TestAggregate.Create(id)
      val failure = expectMsgType[AggregateStatus.Failure]
      failure.cause should be(AggregateAlreadyInitialized)
    }

    "fail for deleted" in {
      val id = TestId.generate()

      supervisor ! TestAggregate.Create(id)
      expectMsgType[AggregateStatus.Success]

      supervisor ! TestAggregate.Delete(id)
      expectMsgType[AggregateStatus.Success]

      supervisor ! TestAggregate.Create(id)
      val failure = expectMsgType[AggregateStatus.Failure]
      failure.cause should be(AggregateDeleted)
    }
  }

  "Aggregate update" must {

    "succeed" in new AggregateFixture {
      supervisor ! TestAggregate.Count(testId)
      expectMsgType[AggregateStatus.Success]
    }

    "update revision" in new AggregateFixture {
      supervisor ! TestAggregate.Count(testId)
      val success = expectMsgType[AggregateStatus.Success]
      success.result.aggregateRevision should be(AggregateRevision.Initial.next.next)
    }

    "update state" in new AggregateFixture {
      supervisor ! TestAggregate.GetCount(testId)
      expectMsg(0)

      supervisor ! TestAggregate.Count(testId)
      expectMsgType[AggregateStatus.Success]

      supervisor ! TestAggregate.GetCount(testId)
      expectMsg(1)
    }

    "fail for unknown" in {
      supervisor ! TestAggregate.Count(TestId.generate())
      val failure = expectMsgType[AggregateStatus.Failure]
      failure.cause should be(AggregateNotInitialized)
    }

    "fail for deleted" in new AggregateFixture {
      supervisor ! TestAggregate.Delete(testId)
      expectMsgType[AggregateStatus.Success]

      supervisor ! TestAggregate.Count(testId)
      val failure = expectMsgType[AggregateStatus.Failure]
      failure.cause should be(AggregateDeleted)
    }
  }

  "Aggregate delete" must {

    "succeed" in new AggregateFixture {
      supervisor ! TestAggregate.Delete(testId)
      expectMsgType[AggregateStatus.Success]
    }

    "fail for deleted" in new AggregateFixture {
      supervisor ! TestAggregate.Delete(testId)
      expectMsgType[AggregateStatus.Success]

      supervisor ! TestAggregate.Delete(testId)
      val failure = expectMsgType[AggregateStatus.Failure]
      failure.cause should be(AggregateDeleted)
    }

    "fail for unknown" in new AggregateFixture {
      supervisor ! TestAggregate.Delete(TestId.generate())
      val failure = expectMsgType[AggregateStatus.Failure]
      failure.cause should be(AggregateNotInitialized)
    }
  }

  "Aggregate message" must {

    "succeed" in new AggregateFixture {
      supervisor ! TestAggregate.GetCount(testId)
      expectMsg(0)
    }

    "fail for deleted" in new AggregateFixture {
      supervisor ! TestAggregate.Delete(testId)
      expectMsgType[AggregateStatus.Success]

      supervisor ! TestAggregate.GetCount(testId)
      val failure = expectMsgType[Status.Failure]
      failure.cause shouldBe an[AggregateException]
    }

    "fail for unknown" in new AggregateFixture {
      supervisor ! TestAggregate.GetCount(TestId.generate())
      val failure = expectMsgType[Status.Failure]
      failure.cause shouldBe an[AggregateException]
    }
  }

  trait AggregateFixture extends {
    val testId = TestId.generate()
    supervisor ! TestAggregate.Create(testId)
    expectMsgType[AggregateStatus.Success]
  }
}
