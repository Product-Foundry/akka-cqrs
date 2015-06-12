package com.productfoundry.akka.cqrs

import akka.actor.{ActorRef, Props, Status}
import com.productfoundry.akka.PassivationConfig
import com.productfoundry.akka.cqrs.CommandRequest._
import com.productfoundry.akka.cqrs.DummyAggregate._
import com.productfoundry.support.AggregateTestSupport

class AggregateSpec extends AggregateTestSupport {

  implicit object DummyAggregateFactory extends AggregateFactory[DummyAggregate] {
    override def props(config: PassivationConfig): Props = {
      Props(new DummyAggregate(config))
    }
  }

  implicit val supervisorFactory = domainContext.entitySupervisorFactory[DummyAggregate]

  val supervisor: ActorRef = EntitySupervisor.forType[DummyAggregate]

  "Aggregate creation" must {

    "succeed" in {
      supervisor ! Create(TestId.generate())
      expectMsgType[AggregateResult.Success]
    }

    "have initial revision" in {
      supervisor ! Create(TestId.generate())

      val success = expectMsgType[AggregateResult.Success]
      success.tag.revision should be(AggregateRevision(1L))
    }

    "fail for existing" in {
      val id = TestId.generate()

      supervisor ! Create(id)
      expectMsgType[AggregateResult.Success]

      supervisor ! Create(id)
      val failure = expectMsgType[Status.Failure]
      failure.cause shouldBe an[AggregateException]
    }

    "fail for deleted" in {
      val id = TestId.generate()

      supervisor ! Create(id)
      expectMsgType[AggregateResult.Success]

      supervisor ! Delete(id)
      expectMsgType[AggregateResult.Success]

      supervisor ! Create(id)
      val failure = expectMsgType[Status.Failure]
      failure.cause shouldBe an[AggregateDeletedException]
    }
  }

  "Aggregate update" must {

    "succeed" in new AggregateFixture {
      supervisor ! Count(testId)
      expectMsgType[AggregateResult.Success]
    }

    "update revision" in new AggregateFixture {
      supervisor ! Count(testId)
      val success = expectMsgType[AggregateResult.Success]
      success.tag.revision should be(2L)
    }

    "update state" in new AggregateFixture {
      supervisor ! GetCount(testId)
      expectMsg(0)

      supervisor ! Count(testId)
      expectMsgType[AggregateResult.Success]

      supervisor ! GetCount(testId)
      expectMsg(1)
    }

    "fail for unknown" in {
      supervisor ! Count(TestId.generate())
      val failure = expectMsgType[Status.Failure]
      failure.cause shouldBe an[AggregateException]
    }

    "fail for deleted" in new AggregateFixture {
      supervisor ! Delete(testId)
      expectMsgType[AggregateResult.Success]

      supervisor ! Count(testId)
      val failure = expectMsgType[Status.Failure]
      failure.cause shouldBe an[AggregateException]
    }
  }

  "Aggregate delete" must {

    "succeed" in new AggregateFixture {
      supervisor ! Delete(testId)
      expectMsgType[AggregateResult.Success]
    }

    "fail for deleted" in new AggregateFixture {
      supervisor ! Delete(testId)
      expectMsgType[AggregateResult.Success]

      supervisor ! Delete(testId)
      val failure = expectMsgType[Status.Failure]
      failure.cause shouldBe an[AggregateDeletedException]
    }

    "fail for unknown" in new AggregateFixture {
      supervisor ! Delete(TestId.generate())
      val failure = expectMsgType[Status.Failure]
      failure.cause shouldBe an[AggregateException]
    }
  }

  "Aggregate message" must {

    "succeed" in new AggregateFixture {
      supervisor ! GetCount(testId)
      expectMsg(0)
    }

    "fail for deleted" in new AggregateFixture {
      supervisor ! Delete(testId)
      expectMsgType[AggregateResult.Success]

      supervisor ! GetCount(testId)
      val failure = expectMsgType[Status.Failure]
      failure.cause shouldBe an[AggregateException]
    }

    "fail for unknown" in new AggregateFixture {
      supervisor ! GetCount(TestId.generate())
      val failure = expectMsgType[Status.Failure]
      failure.cause shouldBe an[AggregateException]
    }
  }

  "Aggregate revision check" must {

    "succeed with initial revision on create" in {
      supervisor ! Create(TestId.generate()).withExpectedRevision(AggregateRevision.Initial)
      expectMsgType[AggregateResult.Success]
    }

    "fail with other revision on create" in {
      val expected = AggregateRevision(2L)

      supervisor ! Create(TestId.generate()).withExpectedRevision(expected)
      val failure = expectMsgType[AggregateResult.Failure]
      failure.cause should be(RevisionConflict(expected, AggregateRevision.Initial))
    }

    "succeed on update" in new AggregateFixture {
      val expected = AggregateRevision(1L)
      supervisor ! Count(testId).withExpectedRevision(expected)
      val status = expectMsgType[AggregateResult.Success]
      status.tag.revision should be(expected.next)
    }

    "fail on update with wrong revision" in new AggregateFixture {
      val expected = AggregateRevision(3L)
      supervisor ! Count(testId).withExpectedRevision(expected)

      val failure = expectMsgType[AggregateResult.Failure]
      failure.cause should be(RevisionConflict(expected, AggregateRevision(1L)))
    }

    "be enforced by command" in new AggregateFixture {
      supervisor ! CountWithRequiredRevisionCheck(testId)

      val failure = expectMsgType[Status.Failure]
      failure.cause shouldBe an[AggregateRevisionRequiredException]
    }

    "succeed when enforced by command" in new AggregateFixture {
      val expected = AggregateRevision(1L)
      supervisor ! CountWithRequiredRevisionCheck(testId).withExpectedRevision(expected)

      val status = expectMsgType[AggregateResult.Success]
      status.tag.revision should be(expected.next)
    }

    "fail when enforced by command with wrong revision" in new AggregateFixture {
      val expected = AggregateRevision(3L)
      supervisor ! CountWithRequiredRevisionCheck(testId).withExpectedRevision(expected)

      val failure = expectMsgType[AggregateResult.Failure]
      failure.cause should be(RevisionConflict(expected, AggregateRevision(1L)))
    }

    "provide empty differences if expected is higher than actual revision" in new AggregateFixture {
      val actual = AggregateRevision(1L)
      val expected = actual.next
      supervisor ! Count(testId).withExpectedRevision(expected)
      expectMsgPF() {
        case AggregateResult.Failure(conflict: RevisionConflict) =>
          conflict.expected should be(expected)
          conflict.actual should be(actual)
          conflict.recordsOption should be('empty)
      }
    }

    "provide differences in revisions" in new AggregateFixture {
      val tags = 1 to 10 map { _ =>
        supervisor ! Count(testId)
        expectMsgType[AggregateResult.Success].tag
      }

      val actual = tags.last.revision
      val expected = AggregateRevision(1L)

      supervisor ! Count(testId).withExpectedRevision(expected)
      expectMsgPF() {
        case AggregateResult.Failure(conflict: RevisionConflict) =>
          conflict.expected should be(expected)
          conflict.actual should be(actual)
          conflict.recordsOption.get.size should be(actual.value - expected.value)
      }
    }
  }

  "Aggregate validation" must {

    "report validation messages" in new AggregateFixture {
      supervisor ! Increment(testId, -1)
      val status = expectMsgType[AggregateResult.Failure]
      status.cause should be(ValidationError(InvalidIncrement(-1)))
    }

    "be performed after revision check" in new AggregateFixture {
      supervisor ! Increment(testId, -1).withExpectedRevision(AggregateRevision.Initial)
      val status = expectMsgType[AggregateResult.Failure]
      status.cause shouldBe a[RevisionConflict]
    }
  }

  "Aggregate payload" must {

    "be unspecified" in new AggregateFixture {
      supervisor ! Count(testId)
      val status = expectMsgType[AggregateResult.Success]
      status.response should be(Unit)
    }

    "be defined by aggregate" in new AggregateFixture {
      supervisor ! CountWithPayload(testId)
      val status = expectMsgType[AggregateResult.Success]
      status.response should be(0L)
    }
  }

  "Aggregate metadata" must {

    "be stored in commit" in new AggregateFixture {
      val metadata = Map("a" -> "b")
      supervisor ! Count(testId).withMetadata(metadata)
      expectMsgType[AggregateResult.Success]

      supervisor ! Count(testId).withExpectedRevision(AggregateRevision(1L))
      expectMsgPF() {
        case AggregateResult.Failure(conflict: RevisionConflict) =>
          conflict.recordsOption.get.size should be(1)
          conflict.recordsOption.get.head.headers.metadata should be(metadata)
      }
    }
  }

  "Aggregate exceptions" must {

    "be recoverable" in new AggregateFixture {
      supervisor ! Create(testId)
      expectMsgType[Status.Failure]

      supervisor ! Count(testId)
      val success = expectMsgType[AggregateResult.Success]
      success.tag.revision should be(AggregateRevision(2))
    }
  }

  trait AggregateFixture {
    val testId = TestId.generate()
    supervisor ! Create(testId)
    expectMsgType[AggregateResult.Success]
  }
}
