package com.productfoundry.akka.cqrs

import akka.actor.{ActorRef, ActorSystem}
import akka.testkit.TestActor.{AutoPilot, KeepRunning, NoAutoPilot}
import akka.testkit._
import com.productfoundry.akka.cqrs.project.{DomainRevision, CommitHeaders, Projection, ProjectionProvider}
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}

import scala.concurrent.stm._
import scala.reflect.ClassTag

/**
 * Base spec for Aggregate factory unit tests.
 */
abstract class AggregateMockSupport(_system: ActorSystem)
  extends TestKit(_system)
  with ImplicitSender
  with WordSpecLike
  with Matchers
  with BeforeAndAfterAll {

  /**
   * Shut down the actor system after every suite.
   */
  override def afterAll(): Unit = {
    TestKit.shutdownActorSystem(system)
  }

  trait AggregateMockFixture[P <: Projection[P]] {

    val aggregateFactoryProbe = TestProbe()

    val autopilotProbe = TestProbe()

    val autoPilot: AutoPilot = new AutoPilot {
      override def run(sender: ActorRef, msg: Any): AutoPilot = TestActor.NoAutoPilot
    }

    autopilotProbe.setAutoPilot(new AutoPilot {
      override def run(sender: ActorRef, msg: Any): AutoPilot = {
        autoPilot.run(sender, msg) match {
          case NoAutoPilot => aggregateFactoryProbe.ref.tell(msg, sender)
          case KeepRunning =>
        }

        TestActor.KeepRunning
      }
    })

    /**
     * Aggregate factory is backed by a test probe.
     *
     * This allows intercepting updates, mocking responses and updating memory image.
     */
    val aggregateFactory = new AggregateFactoryProvider {
      override def apply[A <: Aggregate[_] : ClassTag]: ActorRef = autopilotProbe.ref
    }

    /**
     * Tracks aggregate revision for every individual spec.
     *
     * Revision is incremented for every given or updated event.
     */
    private val aggregateRevisionRef = Ref(AggregateRevision.Initial)

    def aggregateRevision: AggregateRevision = aggregateRevisionRef.single.get

    /**
     * Tracks domain revision for every individual spec.
     *
     * Revision is incremented for every given or updated event.
     */
    private val domainRevisionRef = Ref(DomainRevision.Initial)

    def domainRevision: DomainRevision = domainRevisionRef.single.get


    /**
     * Sets initial state.
     *
     * @param events to build application state from.
     * @tparam E Aggregate events.
     */
    def given[E <: AggregateEvent](events: E*): Unit = updateState(events: _*)

    /**
     * Updates application state.
     *
     * @param events to update state.
     * @tparam E Aggregate events.
     */
    def updateState[E <: AggregateEvent](events: E*): Unit = {
      atomic { implicit txn =>
        domainRevisionRef.transform(_.next)
        aggregateRevisionRef.transform(_.next)
        projectionRef.transform(_.project(CommitHeaders(domainRevisionRef(), aggregateRevisionRef(), System.currentTimeMillis(), Map.empty), events))
      }
    }

    /**
     * Mocks a successful update to an aggregate though its supervisor.
     *
     * @param events to generate as a result of the update.
     * @param payload to send back on success.
     * @tparam I aggregate id type.
     * @tparam E event type.
     */
    def mockUpdateSuccess[I <: AggregateId, E <: AggregateEvent](events: (I) => Seq[E], payload: Any = Unit): I = {
      val message = aggregateFactoryProbe.expectMsgType[AggregateMessage]
      val aggregateId = message.id.asInstanceOf[I]
      val updateEvents = events(aggregateId)
      require(updateEvents.nonEmpty, "At least one event is required after a successful update")
      updateState(updateEvents: _*)
      aggregateFactoryProbe.reply(AggregateResult.Success(CommitResult(aggregateRevision, domainRevision, payload)))
      aggregateId
    }

    /**
     * Mocks a failed update to an aggregate though its supervisor due to validation errors.
     * @param failures to reply as a result of the update.
     * @tparam E event type.
     */
    def mockUpdateFailure[E <: ValidationMessage](failure: E, failures: E*): Unit = {
      aggregateFactoryProbe.expectMsgType[AggregateMessage]
      aggregateFactoryProbe.reply(AggregateResult.Failure(ValidationError(failure, failures: _*)))
    }

    /**
     * Tracks application state for every individual spec.
     */
    val projectionRef: Ref[P]

    /**
     * Atomically provides application state using STM.
     */
    val projection: ProjectionProvider[P] = new ProjectionProvider[P] {

      override def getWithRevision(minimum: DomainRevision): (P, DomainRevision) = {
        atomic { implicit txn =>
          if (domainRevisionRef() < minimum) {
            retry
          } else {
            (projectionRef(), domainRevisionRef())
          }
        }
      }

      override def get: P = projectionRef.single.get
    }
  }

}
