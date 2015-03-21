package com.productfoundry.akka.cqrs

import akka.actor.{ActorRef, ActorSystem}
import akka.testkit._
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}

import scala.concurrent.stm._
import scala.reflect.ClassTag

/**
 * Base spec for Aggregate factory unit tests.
 *
 * Provides a test actor system.
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

  trait AggregateFactoryFixture {

    private val aggregateFactoryProbe = TestProbe()

    /**
     * Aggregate factory is backed by a test probe.
     *
     * This allows intercepting updates, mocking responses and updating memory image.
     */
    val aggregateFactory = new AggregateFactoryProvider {
      override def apply[A <: Aggregate[_, _] : ClassTag]: ActorRef = aggregateFactoryProbe.ref
    }

    /**
     * Tracks domain revision for every individual spec.
     *
     * Revision is incremented for every given or updated event.
     */
    val domainRevisionRef = Ref(DomainRevision.Initial)

    // TODO [AK] Also track aggregate revisions

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
          domainRevisionRef.transform(_.next)
          update(event)
        }
      }
    }

    /**
     * Updates application state in a STM transaction.
     *
     * @param event to update state.
     * @tparam E Aggregate events.
     */
    def update[E <: AggregateEvent](event: E)(implicit txn: InTxn): Unit

    /**
     * Mocks a successful update to an aggregate though its supervisor.
     *
     * The events function must be used to update controller state. The application state is updated, so the controller
     * is able to wait for the projection to update and render a correct response.
     *
     * @param events to generate as a result of the update, gets aggregate ID from the controller update.
     * @tparam I aggregate id type.
     * @tparam E event type.
     */
    def mockUpdateSuccess[I <: AggregateId, E <: AggregateEvent](events: (I) => Seq[E]): Unit = {
      val message = aggregateFactoryProbe.expectMsgType[AggregateMessage]
      val updateEvents = events(message.id.asInstanceOf[I])
      require(updateEvents.nonEmpty, "At least one event is required after a successful update")
      updateState(updateEvents: _*)
      aggregateFactoryProbe.reply(AggregateStatus.Success(CommitResult(AggregateRevision.Initial, domainRevisionRef.single.get)))
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
  }
}
