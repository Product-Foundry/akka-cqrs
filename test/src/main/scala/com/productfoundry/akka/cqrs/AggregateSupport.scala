package com.productfoundry.akka.cqrs

import java.util.UUID

import akka.actor._
import com.productfoundry.akka.cqrs.AggregateStatus.AggregateStatus
import org.scalatest._

import scala.concurrent.stm._
import scala.reflect.ClassTag

/**
  * Base spec for testing aggregates.
  *
  * @param _system          test actor system.
  * @param aggregateClass   aggregate class.
  * @param aggregateFactory aggregate factory, typically defined in the Spec to mixin additional behavior.
  * @tparam A Aggregate type.
  */
abstract class AggregateSupport[A <: Aggregate](_system: ActorSystem)(implicit aggregateClass: ClassTag[A],
                                                                      aggregateFactory: AggregateFactory[A])
  extends EntitySupport(_system) {

  implicit def entityIdResolution: EntityIdResolution[A] = new AggregateIdResolution[A]()

  /**
    * Test local entities by default, requires implicit entity factory.
    */
  implicit val supervisorFactory: EntitySupervisorFactory[A] = new LocalEntityContext(system).entitySupervisorFactory[A]

  /**
    * Entity supervisor for the actor under test.
    */
  var supervisor: ActorRef = system.deadLetters

  /**
    * Commits are collected only if the LocalCommitPublisher is mixed into the actor under test.
    */
  def withCommitCollector[E](block: (LocalCommitCollector) => E): E = {
    block(commitCollector)
  }

  /**
    * Optionally collected commits.
    */
  var commitCollectorOption: Option[LocalCommitCollector] = None

  /**
    * Collected commits.
    */
  def commitCollector: LocalCommitCollector = commitCollectorOption.getOrElse(throw new IllegalArgumentException("Commit collector is not yet available"))

  /**
    * Initialize the supervisor.
    */
  before {
    supervisor = EntitySupervisor.forType[A]
    commitCollectorOption = Some(LocalCommitCollector(UUID.randomUUID().toString))
  }

  /**
    * Terminates all actors.
    */
  after {
    terminateConfirmed(supervisor)

    withCommitCollector { commitCollector =>
      terminateConfirmed(commitCollector.ref)
      commitCollectorOption = None
    }
  }

  /**
    * Dump commits on failure when collected.
    *
    * @param test to run.
    * @return outcome.
    */
  override protected def withFixture(test: NoArgTest): Outcome = {
    val outcome = super.withFixture(test)

    withCommitCollector { commitCollector =>
      if (outcome.isFailed) {
        commitCollector.dumpCommits()
      }
    }

    outcome
  }

  /**
    * Asserts a specified event is committed.
    *
    * @param event     that is expected.
    * @param CommitTag indicates commit type with events.
    */
  def expectEvent(event: AggregateEvent, headersOption: Option[CommitHeaders] = None)(implicit CommitTag: ClassTag[Commit]): Unit = {
    eventually {
      withCommitCollector { commitCollector =>
        val exists: Boolean = commitCollector.eventRecords.exists { eventRecord =>
          eventRecord.event == event && !headersOption.exists(headers => headers != eventRecord.headers)
        }

        if (!exists) {
          fail(s"Commit with event $event and headers $headersOption not found, does the aggregate under test have the LocalCommitPublisher mixin?")
        }
      }
    }
  }

  /**
    * Asserts an event is committed that matches the specified partial function.
    *
    * For all matching events, an assertion can be executed.
    *
    * @param eventRecordCheckFunction to match and assert events.
    */
  def expectEventRecordPF(eventRecordCheckFunction: PartialFunction[AggregateEventRecord, Unit]): Unit = {
    eventually {
      withCommitCollector { commitCollector =>
        val eventRecords = commitCollector.eventRecords
        val toCheck = eventRecords.filter(eventRecordCheckFunction.isDefinedAt)
        assert(toCheck.nonEmpty, s"No events match provided partial function: $eventRecords")
        toCheck.foreach(eventRecordCheckFunction)
      }
    }
  }

  /**
    * Asserts an event is committed that matches the specified partial function.
    *
    * For all matching events, an assertion can be executed.
    *
    * @param eventCheckFunction to match and assert events.
    */
  def expectEventPF(eventCheckFunction: PartialFunction[AggregateEvent, Unit]): Unit = {
    expectEventRecordPF {
      case eventRecord if eventCheckFunction.isDefinedAt(eventRecord.event) => eventCheckFunction(eventRecord.event)
    }
  }

  /**
    * Maps a matching event to a value.
    *
    * @param eventRecordMapFunction to map an event to a value.
    */
  def mapEventRecordPF[E](eventRecordMapFunction: PartialFunction[AggregateEventRecord, E]): E = {
    eventually {
      withCommitCollector { commitCollector =>
        val eventRecords = commitCollector.eventRecords
        val toCheck = eventRecords.filter(eventRecordMapFunction.isDefinedAt)
        assert(toCheck.size == 1, s"Other than 1 event matches provided partial function: $eventRecords")
        toCheck.map(eventRecordMapFunction).head
      }
    }
  }

  /**
    * Maps a matching event to a value.
    *
    * @param eventMapFunction to map an event to a value.
    */
  def mapEventPF[E](eventMapFunction: PartialFunction[AggregateEvent, E]): E = {
    mapEventRecordPF {
      case eventRecord if eventMapFunction.isDefinedAt(eventRecord.event) => eventMapFunction(eventRecord.event)
    }
  }

  /**
    * Asserts a success message is sent from the aggregate.
    *
    * @return the success message.
    */
  def expectMsgSuccess: AggregateStatus.Success = {
    expectMsgType[AggregateStatus.Success]
  }

  /**
    * Asserts a failure message is sent from the aggregate.
    *
    * @param t wrapped error type tag.
    * @tparam T wrapped error type.
    * @return the error wrapped in the failure message.
    */
  def expectMsgError[T](implicit t: ClassTag[T]): T = {
    expectMsgType[AggregateStatus.Failure].cause.asInstanceOf[T]
  }

  /**
    * Asserts a validation error is sent from the aggregate.
    *
    * @param message the expected validation message.
    */
  def expectMsgValidationError(message: ValidationMessage): Unit = {
    assertValidationError(message, expectMsgType[AggregateStatus])
  }

  /**
    * Asserts a status contains a failure message.
    *
    * @param message the expected failure message.
    * @param status  the status.
    */
  def assertValidationError(message: ValidationMessage, status: AggregateStatus): Unit = {
    status match {
      case success: AggregateStatus.Success =>
        fail(s"Unexpected success: $success")

      case AggregateStatus.Failure(cause) =>
        cause match {
          case ValidationError(messages) =>
            assert(Seq(message) === messages, s"Unexpected messages: $messages")

          case _ =>
            fail(s"Unexpected cause: $cause")
        }
    }
  }

  /**
    * Asserts a status contains a failure.
    *
    * @tparam C the expected failure class.
    * @param status the status.
    */
  def assertFailure[C: ClassTag](status: AggregateStatus): Unit = {
    status match {
      case success: AggregateStatus.Success => fail(s"Unexpected success: $success")
      case AggregateStatus.Failure(_: C) => // Success
      case AggregateStatus.Failure(cause) => fail(s"Unexpected cause: $cause")
    }
  }

  /**
    * Scoped fixture to setup aggregates and send messages.
    */
  trait AggregateFixture {
    /**
      * Use commands to initialize fixture state, asserts that all commands return success.
      *
      * Can be invoked multiple times.
      *
      * @param commands to send to aggregate, must succeed,
      */
    def given(commands: AggregateCommandMessage*): Unit = {
      commands.foreach { command =>
        supervisor ! command
        expectMsgSuccess
      }
    }

    /**
      * Executes the specified command and returns the status from the aggregate.
      *
      * @param cmd to execute.
      * @return status.
      */
    def command(cmd: AggregateCommandMessage): AggregateStatus = {
      atomic { implicit txn =>
        val statusOptionRef: Ref[Option[AggregateStatus]] = Ref(Option.empty)

        supervisor ! defaultHeadersOption.foldLeft(cmd.commandRequest)(_ withHeaders _)

        expectMsgPF() {
          case success: AggregateStatus.Success =>
            statusOptionRef.set(Some(success))

          case failure@AggregateStatus.Failure(_) =>
            statusOptionRef.set(Some(failure))
        }

        statusOptionRef().getOrElse(throw new RuntimeException("Unexpected status"))
      }
    }

    def defaultHeadersOption: Option[CommitHeaders] = None
  }

}
