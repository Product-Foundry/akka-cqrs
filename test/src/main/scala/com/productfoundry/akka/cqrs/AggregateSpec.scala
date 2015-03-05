package com.productfoundry.akka.cqrs

import akka.actor._
import akka.testkit.{ImplicitSender, TestKit}
import com.productfoundry.akka.cqrs.AggregateStatus.AggregateStatus
import org.scalatest._
import org.scalatest.concurrent.Eventually
import org.scalatest.time.{Millis, Second, Span}

import scala.concurrent.duration._
import scala.concurrent.stm._
import scala.reflect.ClassTag

/**
 * Base spec for testing aggregates.
 * @param _system test actor system.
 * @param aggregateClass aggregate class.
 * @param aggregateFactory aggregate factory, typically defined in the Spec to mixin additional behavior.
 * @tparam A Aggregate type.
 */
abstract class AggregateSpec[A <: Aggregate[_, _]](_system: ActorSystem)(implicit aggregateClass: ClassTag[A],
                                                                         aggregateFactory: AggregateFactory[A])
  extends TestKit(_system)
  with ImplicitSender
  with WordSpecLike
  with Matchers
  with BeforeAndAfterAll
  with BeforeAndAfter
  with Eventually {

  /**
   * System should be fast, so for fast test runs check assertions frequently.
   */
  implicit override val patienceConfig = PatienceConfig(
    timeout = scaled(Span(1, Second)),
    interval = scaled(Span(10, Millis))
  )

  /**
   * Test local entities by default, requires implicit entity factory.
   */
  implicit val supervisorFactory = new LocalDomainContext(system).entitySupervisorFactory[A]

  /**
   * Entity supervisor for the actor under test.
   */
  var supervisor: ActorRef = system.deadLetters

  /**
   * Commits are collected only if the LocalCommitPublisher is mixed into the actor under test.
   */
  def withCommitCollector(block: (LocalCommitCollector) => Unit): Unit = {
    for {
      commitCollector <- commitCollectorOption
    } yield {
      block(commitCollector)
    }
  }

  /**
   * Collected commits.
   */
  var commitCollectorOption: Option[LocalCommitCollector] = None

  /**
   * Initialize the supervisor.
   */
  before {
    supervisor = EntitySupervisor.forType[A]
    commitCollectorOption = Some(LocalCommitCollector())
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
   * Terminates specified actors and wait until termination is confirmed.
   * @param actors to terminate.
   */
  def terminateConfirmed(actors: ActorRef*): Unit = {
    actors.foreach { actor =>
      watch(actor)
      actor ! PoisonPill
      // wait until supervisor is terminated
      fishForMessage(1.seconds) {
        case Terminated(_) =>
          unwatch(actor)
          true
        case _ =>
          false
      }
    }
  }

  /**
   * Shut down the actor system after every suite.
   */
  override def afterAll(): Unit = {
    TestKit.shutdownActorSystem(system)
    system.awaitTermination()
  }

  /**
   * Asserts a specified event is committed.
   * @param event that is expected.
   * @param CommitTag indicates commit type with events.
   * @tparam E Domain event type.
   */
  def expectEvent[E <: DomainEvent](event: E)(implicit CommitTag: ClassTag[Commit[E]]): Unit = {
    eventually {
      withCommitCollector { commitCollector =>
        assert(commitCollector.events.contains(event), s"Commit with event $event not found, does the aggregate under test have the LocalCommitPublisher mixin?")
      }
    }
  }

  /**
   * Asserts a success message is sent from the aggregate.
   * @param t wrapped message type tag.
   * @tparam T wrapped message type.
   * @return the message wrapped in the success message.
   */
  def expectMsgSuccess[T](implicit t: ClassTag[T]): T = {
    expectMsgType[AggregateStatus.Success].result.asInstanceOf[T]
  }

  /**
   * Asserts a failure message is sent from the aggregate.
   * @param t wrapped error type tag.
   * @tparam T wrapped error type.
   * @return the error wrapped in the failure message.
   */
  def expectMsgError[T](implicit t: ClassTag[T]): T = {
    expectMsgType[AggregateStatus.Failure].cause.asInstanceOf[T]
  }

  /**
   * Asserts a validation error is sent from the aggregate.
   * @param message the expected validation message.
   */
  def expectMsgValidationError(message: ValidationMessage) = {
    assertValidationError(message, expectMsgType[AggregateStatus])
  }

  /**
   * Asserts a status contains a failure message.
   * @param message the expected failure message.
   * @param status the status.
   */
  def assertValidationError(message: ValidationMessage, status: AggregateStatus): Unit = {
    status match {
      case AggregateStatus.Success(success) =>
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
   * Scoped fixture to setup aggregates and send messages while keeping track of revisions.
   */
  trait AggregateFixture {
    val revisionRef = Ref(AggregateRevision.Initial)

    /**
     * Use commands to initialize fixture state, asserts that all commands return success.
     *
     * Can be invoked multiple times.
     *
     * @param commands to send to aggregate, must succeed,
     */
    def given(commands: Command*): Unit = {
      atomic { implicit txn =>
        revisionRef.transform { revision =>
          commands.foldLeft(revision) { case (rev, command) =>
            supervisor ! CommandMessage(rev, command)
            expectMsgSuccess[CommitResult].aggregateRevision
          }
        }
      }
    }

    /**
     * Executes the specified command and returns the status from the aggregate.
     *
     * @param cmd to execute.
     * @return status.
     */
    def command(cmd: Command): AggregateStatus = {
      atomic { implicit txn =>
        val statusOptionRef: Ref[Option[AggregateStatus]] = Ref(None)

        revisionRef.transform { revision =>
          supervisor ! CommandMessage(revision, cmd)
          expectMsgPF() {
            case success@AggregateStatus.Success(commitResult: CommitResult) =>
              statusOptionRef.set(Some(success))
              commitResult.aggregateRevision

            case failure@AggregateStatus.Failure(_) =>
              statusOptionRef.set(Some(failure))
              revision
          }
        }

        statusOptionRef().getOrElse(throw new RuntimeException("Unexpected status"))
      }
    }
  }
}