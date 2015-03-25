package com.productfoundry.akka.cqrs

import akka.actor.{ActorRef, ActorSystem}
import akka.testkit._
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

    /**
     * Aggregate factory is backed by a test probe.
     *
     * This allows intercepting updates, mocking responses and updating memory image.
     */
    val aggregateFactory = new AggregateFactoryProvider {
      override def apply[A <: Aggregate[_, _] : ClassTag]: ActorRef = aggregateFactoryProbe.ref
    }

    /**
     * Tracks aggregate revision for every individual spec.
     *
     * Revision is incremented for every given or updated event.
     */
    val aggregateRevisionRef = Ref(AggregateRevision.Initial)

    /**
     * Tracks domain revision for every individual spec.
     *
     * Revision is incremented for every given or updated event.
     */
    val domainRevisionRef = Ref(DomainRevision.Initial)

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
        events.foreach { event =>
          projectionRef.transform(_.project(aggregateRevisionRef.getAndTransform(_.next))(event))
          domainRevisionRef.transform(_.next)
        }
      }
    }

    /**
     * Mocks a successful update to an aggregate though its supervisor.
     *
     * @param events to generate as a result of the update.
     * @tparam I aggregate id type.
     * @tparam E event type.
     */
    def mockUpdateSuccess[I <: AggregateId, E <: AggregateEvent](events: (I) => Seq[E]): Unit = {
      val message = aggregateFactoryProbe.expectMsgType[AggregateMessage]
      val updateEvents = events(message.id.asInstanceOf[I])
      require(updateEvents.nonEmpty, "At least one event is required after a successful update")
      updateState(updateEvents: _*)
      aggregateFactoryProbe.reply(AggregateStatus.Success(CommitResult(aggregateRevisionRef.single.get, domainRevisionRef.single.get)))
    }

    /**
     * Mocks a failed update to an aggregate though its supervisor due to validation errors.
     * @param failures to reply as a result of the update.
     * @tparam E event type.
     */
    def mockUpdateFailure[E <: ValidationMessage](failure: E, failures: E*): Unit = {
      aggregateFactoryProbe.expectMsgType[AggregateMessage]
      aggregateFactoryProbe.reply(AggregateStatus.Failure(ValidationError(failure, failures: _*)))
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
