package com.productfoundry.akka.cqrs.publish

import akka.actor.{Status, ActorPath, ActorRef, Props}
import akka.testkit.TestProbe
import com.productfoundry.akka.PassivationConfig
import com.productfoundry.akka.cqrs.TestAggregate._
import com.productfoundry.akka.cqrs._
import com.productfoundry.support.AggregateTestSupport
import org.scalatest.BeforeAndAfterEach

import scala.concurrent.duration._
import scala.util.Random

class ReliableCommitPublisherSpec extends AggregateTestSupport with BeforeAndAfterEach {

  val publishedEventProbe = TestProbe()

  implicit object TestAggregateFactory extends AggregateFactory[TestAggregate] {
    override def props(config: PassivationConfig): Props = {
      Props(new TestAggregate(config) with ReliableCommitPublisher {
        override def publishTarget: ActorPath = publishedEventProbe.ref.path

        override def redeliverInterval: FiniteDuration = 500.millis
      })
    }
  }

  implicit val supervisorFactory = domainContext.entitySupervisorFactory[TestAggregate]

  val supervisor: ActorRef = EntitySupervisor.forType[TestAggregate]

  "Reliable commit publisher" must {

    "publish commit" in new fixture {
      val commit = commitPublication.commit
      commit.events should be(Seq(Created(testId)))
      commit.revision should be(AggregateRevision(1L))
    }

    "include commander" in new fixture {
      commitPublication.commanderOption should be(Some(self))
    }

    "have delivery id" in new fixture {
      commitPublication.deliveryIdOption should be('nonEmpty)
    }

    "republish if not confirmed" in new fixture {
      supervisor ! Count(testId)
      expectMsgType[AggregateResult.Success]

      val publications = 1 to 5 map { _ =>
        publishedEventProbe.expectMsgType[CommitPublication]
      }

      // Confirm any message
      publications.toArray.apply(Random.nextInt(publications.size)).confirmIfRequested()

      // All commits should be identical
      publications.size should be > 1
      publications.toSet.size should be(1)
    }

    "republish after crash" in new fixture {
      supervisor ! Count(testId)
      expectMsgType[AggregateResult.Success]

      // Commit should be published, but we are not confirming
      publishedEventProbe.expectMsgType[CommitPublication]

      // Let the aggregate crash by sending an invalid command
      supervisor ! Create(testId)
      expectMsgType[Status.Failure]

      // Sending any message will restart the actor
      supervisor ! GetCount(testId)
      expectMsgType[Int]

      // Commit should be republished as part of the recovery process
      val publication = publishedEventProbe.expectMsgType[CommitPublication]
      publication.confirmIfRequested()
      publication.commit.revision should be(AggregateRevision(2L))
    }

    "maintain revision order when publishing" in new fixture {
      // Send a lot of updates and make sure they are all successful
      val revisions = 1 to 5 map { _ =>
        supervisor ! Count(testId)
        expectMsgType[AggregateResult.Success].result.revision
      }

      // Force redelivery and make sure commits on higher revisions are only published after the previous commit
      revisions.foreach { revision =>
        val publications = 1 to 3 map { _ =>
          publishedEventProbe.expectMsgType[CommitPublication]
        }

        // We've waited long enough, simply confirm the first received message
        publications.head.confirmIfRequested()

        // The published commits should match the expected revision and should all be identical
        publications.head.commit.revision should be(revision)
        publications.toSet.size should be(1)
      }
    }

    trait fixture {
      val testId = TestId.generate()
      supervisor ! Create(testId)
      expectMsgType[AggregateResult.Success]

      val commitPublication = publishedEventProbe.expectMsgType[CommitPublication]
      commitPublication.confirmIfRequested()
    }
  }

  override protected def afterEach(): Unit = {
    publishedEventProbe.expectNoMsg(2.seconds)
  }
}