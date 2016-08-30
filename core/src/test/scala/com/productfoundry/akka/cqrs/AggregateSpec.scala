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

  implicit val supervisorFactory = entityContext.entitySupervisorFactory[DummyAggregate]

  val supervisor: ActorRef = EntitySupervisor.forType[DummyAggregate]

  "Aggregate creation" must {

    "succeed" in {
      supervisor ! Create(DummyId.generate())
      expectMsgType[AggregateStatus.Success]
    }

    "have initial revision" in {
      supervisor ! Create(DummyId.generate())

      val success = expectMsgType[AggregateStatus.Success]
      success.response.tag.revision should be(AggregateRevision(1L))
    }

    "fail for existing" in {
      val id = DummyId.generate()

      supervisor ! Create(id)
      expectMsgType[AggregateStatus.Success]

      supervisor ! Create(id)
      val failure = expectMsgType[Status.Failure]
      failure.cause shouldBe an[AggregateException]
    }

    "fail for deleted" in {
      val id = DummyId.generate()

      supervisor ! Create(id)
      expectMsgType[AggregateStatus.Success]

      supervisor ! Delete(id)
      expectMsgType[AggregateStatus.Success]

      supervisor ! Create(id)
      val failure = expectMsgType[AggregateStatus.Failure]
      failure.cause shouldBe an[AggregateDeleted]
    }

    "ignore noop" in {
      val id = DummyId.generate()

      supervisor ! NoOp(id)
      expectMsgType[AggregateStatus.Success].response.tag.revision should be (AggregateRevision.Initial)

      supervisor ! Create(id)
      expectMsgType[AggregateStatus.Success]
    }
  }

  "Aggregate update" must {

    "succeed" in new AggregateFixture {
      supervisor ! Count(testId)
      expectMsgType[AggregateStatus.Success]
    }

    "update revision" in new AggregateFixture {
      supervisor ! Count(testId)
      val success = expectMsgType[AggregateStatus.Success]
      success.response.tag.revision should be(2L)
    }

    "update state" in new AggregateFixture {
      supervisor ! GetCount(testId)
      expectMsg(0)

      supervisor ! Count(testId)
      expectMsgType[AggregateStatus.Success]

      supervisor ! GetCount(testId)
      expectMsg(1)
    }

    "ignore noop" in {
      val id = DummyId.generate()

      supervisor ! Create(id)
      expectMsgType[AggregateStatus.Success]

      supervisor ! NoOp(id)
      expectMsgType[AggregateStatus.Success].response.tag.revision should be (AggregateRevision(1))
    }

    "fail for unknown" in {
      supervisor ! Count(DummyId.generate())
      val failure = expectMsgType[Status.Failure]
      failure.cause shouldBe an[AggregateException]
    }

    "fail for deleted" in new AggregateFixture {
      supervisor ! Delete(testId)
      expectMsgType[AggregateStatus.Success]

      supervisor ! Count(testId)
      val failure = expectMsgType[Status.Failure]
      failure.cause shouldBe an[AggregateException]
    }
  }

  "Aggregate delete" must {

    "succeed" in new AggregateFixture {
      supervisor ! Delete(testId)
      expectMsgType[AggregateStatus.Success]
    }

    "fail for deleted" in new AggregateFixture {
      supervisor ! Delete(testId)
      expectMsgType[AggregateStatus.Success]

      supervisor ! Delete(testId)
      val failure = expectMsgType[AggregateStatus.Failure]
      failure.cause shouldBe an[AggregateDeleted]
    }

    "fail for unknown" in new AggregateFixture {
      supervisor ! Delete(DummyId.generate())
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
      expectMsgType[AggregateStatus.Success]

      supervisor ! GetCount(testId)
      val failure = expectMsgType[Status.Failure]
      failure.cause shouldBe an[AggregateException]
    }

    "fail for unknown" in new AggregateFixture {
      supervisor ! GetCount(DummyId.generate())
      val failure = expectMsgType[Status.Failure]
      failure.cause shouldBe an[AggregateException]
    }
  }

  "Aggregate revision check" must {

    "succeed with initial revision on create" in {
      supervisor ! Create(DummyId.generate()).withExpectedRevision(AggregateRevision.Initial)
      expectMsgType[AggregateStatus.Success]
    }

    "fail with other revision on create" in {
      val expected = AggregateRevision(2L)

      supervisor ! Create(DummyId.generate()).withExpectedRevision(expected)
      val failure = expectMsgType[AggregateStatus.Failure]
      failure.cause should be(RevisionConflict(expected, AggregateRevision.Initial))
    }

    "succeed on update" in new AggregateFixture {
      val expected = AggregateRevision(1L)
      supervisor ! Count(testId).withExpectedRevision(expected)
      val status = expectMsgType[AggregateStatus.Success]
      status.response.tag.revision should be(expected.next)
    }

    "fail on update with wrong revision" in new AggregateFixture {
      val expected = AggregateRevision(3L)
      supervisor ! Count(testId).withExpectedRevision(expected)

      val failure = expectMsgType[AggregateStatus.Failure]
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

      val status = expectMsgType[AggregateStatus.Success]
      status.response.tag.revision should be(expected.next)
    }

    "fail when enforced by command with wrong revision" in new AggregateFixture {
      val expected = AggregateRevision(3L)
      supervisor ! CountWithRequiredRevisionCheck(testId).withExpectedRevision(expected)

      val failure = expectMsgType[AggregateStatus.Failure]
      failure.cause should be(RevisionConflict(expected, AggregateRevision(1L)))
    }

    "provide empty differences if expected is higher than actual revision" in new AggregateFixture {
      val actual = AggregateRevision(1L)
      val expected = actual.next
      supervisor ! Count(testId).withExpectedRevision(expected)
      expectMsgPF() {
        case AggregateStatus.Failure(conflict: RevisionConflict) =>
          conflict.expected should be(expected)
          conflict.actual should be(actual)
      }
    }

    "provide differences in revisions" in new AggregateFixture {
      val tags = 1 to 10 map { _ =>
        supervisor ! Count(testId)
        expectMsgType[AggregateStatus.Success].response.tag
      }

      val actual = tags.last.revision
      val expected = AggregateRevision(1L)

      supervisor ! Count(testId).withExpectedRevision(expected)
      expectMsgPF() {
        case AggregateStatus.Failure(conflict: RevisionConflict) =>
          conflict.expected should be(expected)
          conflict.actual should be(actual)
      }
    }
  }

  "Aggregate validation" must {

    "report validation messages" in new AggregateFixture {
      supervisor ! Increment(testId, -1)
      val status = expectMsgType[AggregateStatus.Failure]
      status.cause should be(ValidationError(InvalidIncrement(-1)))
    }

    "be performed after revision check" in new AggregateFixture {
      supervisor ! Increment(testId, -1).withExpectedRevision(AggregateRevision.Initial)
      val status = expectMsgType[AggregateStatus.Failure]
      status.cause shouldBe a[RevisionConflict]
    }
  }

  "Aggregate payload" must {

    "be empty" in new AggregateFixture {
      supervisor ! Count(testId)
      val status = expectMsgType[AggregateStatus.Success]
      status.response.payload shouldBe empty
      status.response.headers shouldBe empty
    }

    "be defined by aggregate" in new AggregateFixture {
      supervisor ! CountWithPayload(testId)
      val status = expectMsgType[AggregateStatus.Success]
      status.response.payload shouldBe Some(0L)
    }
  }

  "Commit headers" must {

    "be stored" in new AggregateFixture {

      val headers = DummyHeaders(System.currentTimeMillis())
      supervisor ! Count(testId).withHeaders(headers)
      expectMsgType[AggregateStatus.Success]

      supervisor ! Count(testId).withExpectedRevision(AggregateRevision(1L))
      expectMsgPF() {
        case AggregateStatus.Failure(conflict: RevisionConflict) =>
      }
    }
  }

  "Aggregate snapshot" must {

    "succeed" in new AggregateFixture {
      supervisor ! Count(testId)
      expectMsgType[AggregateStatus.Success].response.tag.revision should be(AggregateRevision(2))

      supervisor ! Count(testId)
      expectMsgType[AggregateStatus.Success].response.tag.revision should be(AggregateRevision(3))
      supervisor ! Count(testId)
      expectMsgType[AggregateStatus.Success].response.tag.revision should be(AggregateRevision(4))

      supervisor ! Snapshot(testId)
      fishForMessage() {
        case SnapshotComplete => true
      }

      supervisor ! GetCount(testId)
      expectMsg(3)

      supervisor ! Count(testId)
      expectMsgType[AggregateStatus.Success].response.tag.revision should be(AggregateRevision(5))
    }
  }

  "Aggregate exceptions" must {

    "be recoverable" in new AggregateFixture {
      supervisor ! Create(testId)
      expectMsgType[Status.Failure]

      supervisor ! Count(testId)
      val success = expectMsgType[AggregateStatus.Success]
      success.response.tag.revision should be(AggregateRevision(2))
    }
  }

  trait AggregateFixture {
    val testId = DummyId.generate()
    supervisor ! Create(testId)
    expectMsgType[AggregateStatus.Success]
  }
}
