package com.productfoundry.akka.cqrs

import akka.actor.{ActorRef, Props, Status}
import com.productfoundry.akka.PassivationConfig
import com.productfoundry.akka.cqrs.publish.ReliableCommitPublisher
import com.productfoundry.support.AggregateTestSupport
import CommandRequest._

class AggregateSpec extends AggregateTestSupport {

  implicit object TestAggregateFactory extends AggregateFactory[TestAggregate] {
    override def props(config: PassivationConfig): Props = {
      Props(new TestAggregate(config))
    }
  }

  implicit val supervisorFactory = domainContext.entitySupervisorFactory[TestAggregate]

  val supervisor: ActorRef = EntitySupervisor.forType[TestAggregate]

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

  "Aggregate revision check" must {

    "succeed with initial revision on create" in {
      supervisor ! TestAggregate.Create(TestId.generate()).withExpectedRevision(AggregateRevision.Initial)
      expectMsgType[AggregateStatus.Success]
    }

    "fail with other revision on create" in {
      val expected = AggregateRevision.Initial.next.next

      supervisor ! TestAggregate.Create(TestId.generate()).withExpectedRevision(expected)
      val failure = expectMsgType[AggregateStatus.Failure]
      failure.cause should be(RevisionConflict(expected, AggregateRevision.Initial))
    }

    "succeed on update" in new AggregateFixture {
      val expected = AggregateRevision.Initial.next
      supervisor ! TestAggregate.Count(testId).withExpectedRevision(expected)
      val status = expectMsgType[AggregateStatus.Success]
      status.result.aggregateRevision should be(expected.next)
    }

    "fail on update with wrong revision" in new AggregateFixture {
      val expected = AggregateRevision.Initial.next.next.next
      supervisor ! TestAggregate.Count(testId).withExpectedRevision(expected)

      val failure = expectMsgType[AggregateStatus.Failure]
      failure.cause should be(RevisionConflict(expected, AggregateRevision.Initial.next))
    }

    "provide empty differences if expected is higher than actual revision" in new AggregateFixture {
      val actual = AggregateRevision.Initial.next
      val expected = actual.next
      supervisor ! TestAggregate.Count(testId).withExpectedRevision(expected)
      expectMsgPF() {
        case AggregateStatus.Failure(conflict: RevisionConflict) =>
          conflict.expected should be(expected)
          conflict.actual should be(actual)
          conflict.commits should be(empty)
      }
    }

    "provide differences in revisions" in new AggregateFixture {
      val results = 1 to 10 map { _ =>
        supervisor ! TestAggregate.Count(testId)
        expectMsgType[AggregateStatus.Success].result
      }

      val actual = results.last.aggregateRevision
      val expected = AggregateRevision.Initial.next

      supervisor ! TestAggregate.Count(testId).withExpectedRevision(expected)
      expectMsgPF() {
        case AggregateStatus.Failure(conflict: RevisionConflict) =>
          conflict.expected should be(expected)
          conflict.actual should be(actual)
          conflict.commits.size should be(actual.value - expected.value)
          conflict.commits.zip(results).foreach { case (commit, result) =>
            commit.revision should be(result.aggregateRevision)
            commit.events should have size 1
            commit.events.head shouldBe a[TestAggregate.Counted]
          }
      }
    }
  }

  "Aggregate payload" must {

    "be unspecified" in new AggregateFixture {
      supervisor ! TestAggregate.Count(testId)
      val status = expectMsgType[AggregateStatus.Success]
      status.result.payload should be(Unit)
    }

    "be defined by aggregate" in new AggregateFixture {
      supervisor ! TestAggregate.CountWithPayload(testId)
      val status = expectMsgType[AggregateStatus.Success]
      status.result.payload should be(0L)
    }
  }

  "Aggregate headers" must {

    "be stored in commit" in new AggregateFixture {
      val headers = Map("a" -> "b")
      supervisor ! TestAggregate.Count(testId).withHeaders(headers)
      expectMsgType[AggregateStatus.Success]

      supervisor ! TestAggregate.Count(testId).withExpectedRevision(AggregateRevision.Initial.next)
      expectMsgPF() {
        case AggregateStatus.Failure(conflict: RevisionConflict) =>
          conflict.commits.size should be(1)
          conflict.commits.head.headers should be(headers)
      }
    }
  }

  trait AggregateFixture extends {
    val testId = TestId.generate()
    supervisor ! TestAggregate.Create(testId)
    expectMsgType[AggregateStatus.Success]
  }
}
