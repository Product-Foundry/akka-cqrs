package com.productfoundry.akka.cqrs.publish

import akka.actor.{ActorPath, ActorRef, Props, Status}
import akka.testkit.TestProbe
import com.productfoundry.akka.PassivationConfig
import com.productfoundry.akka.cqrs.DummyAggregate._
import com.productfoundry.akka.cqrs._
import com.productfoundry.support.AggregateTestSupport
import org.scalatest.BeforeAndAfterEach

import scala.concurrent.duration._
import scala.util.Random

class ReliableEventPublisherSpec extends AggregateTestSupport with BeforeAndAfterEach {

  val publishedEventProbe = TestProbe()

  val redeliver = 50.millis

  implicit object TestAggregateFactory extends AggregateFactory[DummyAggregate] {
    override def props(config: PassivationConfig): Props = {
      Props(new DummyAggregate(config) with ReliableEventPublisher {
        override def publishTarget: ActorPath = publishedEventProbe.ref.path

        override def redeliverInterval: FiniteDuration = redeliver
      })
    }
  }

  implicit val supervisorFactory = entityContext.entitySupervisorFactory[DummyAggregate]

  val supervisor: ActorRef = EntitySupervisor.forType[DummyAggregate]

  "Reliable event publisher" must {

    "publish event" in new fixture {
      val eventRecord = eventPublication.eventRecord
      eventRecord.event should be(Created(testId))
      eventRecord.tag.revision should be(AggregateRevision(1L))
    }

    "have confirmation" in new fixture {
      eventPublication.confirmationOption should be('nonEmpty)
    }

    "republish if not confirmed" in new fixture {
      supervisor ! Count(testId)
      expectMsgType[AggregateStatus.Success]

      val eventPublications = 1 to 5 map { _ =>
        publishedEventProbe.expectMsgType[EventPublication]
      }

      // Confirm any message
      eventPublications.toArray.apply(Random.nextInt(eventPublications.size)).confirmIfRequested()

      // All events should be identical
      eventPublications.size should be > 1
      eventPublications.toSet.size should be(1)
    }

    "republish after crash" in new fixture {
      supervisor ! Count(testId)
      expectMsgType[AggregateStatus.Success]

      // Commit should be published, but we are not confirming
      publishedEventProbe.expectMsgType[EventPublication]

      // Let the aggregate crash by sending an invalid command
      supervisor ! Create(testId)
      expectMsgType[Status.Failure]

      // Sending any message will restart the actor
      supervisor ! GetCount(testId)
      expectMsgType[Int]

      // Commit should be republished as part of the recovery process
      val republished = publishedEventProbe.expectMsgType[EventPublication]
      republished.confirmIfRequested()
      republished.eventRecord.tag.revision should be(AggregateRevision(2L))
    }

    "maintain revision order when publishing" in new fixture {
      // Send a lot of updates and make sure they are all successful
      val tags = 1 to 5 map { _ =>
        supervisor ! Count(testId)
        expectMsgType[AggregateStatus.Success].response.tag
      }

      // Force redelivery and make sure events on higher revisions are only published after the previous event
      tags.foreach { snapshot =>
        val publications = 1 to 3 map { _ =>
          publishedEventProbe.expectMsgType[EventPublication]
        }

        // We've waited long enough, simply confirm the first received message
        publications.head.confirmIfRequested()

        // The published events should match the expected revision and should all be identical
        publications.head.eventRecord.tag.revision should be(snapshot.revision)
        publications.toSet.size should be(1)
      }
    }

    trait fixture {
      val testId = DummyId.generate()
      supervisor ! Create(testId)
      expectMsgType[AggregateStatus.Success]

      val eventPublication = publishedEventProbe.expectMsgType[EventPublication]
      eventPublication.confirmIfRequested()
    }
  }

  override protected def afterEach(): Unit = {
    eventually {
      publishedEventProbe.expectNoMsg()
    }
  }
}