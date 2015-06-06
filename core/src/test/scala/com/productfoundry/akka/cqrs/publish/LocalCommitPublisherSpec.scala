package com.productfoundry.akka.cqrs.publish

import akka.actor.{ActorRef, Props}
import akka.testkit.TestProbe
import com.productfoundry.akka.PassivationConfig
import com.productfoundry.akka.cqrs.TestAggregate._
import com.productfoundry.akka.cqrs._
import com.productfoundry.support.AggregateTestSupport

import scala.concurrent.duration._

class LocalCommitPublisherSpec extends AggregateTestSupport {

  implicit object TestAggregateFactory extends AggregateFactory[TestAggregate] {
    override def props(config: PassivationConfig): Props = {
      Props(new TestAggregate(config) with LocalCommitPublisher)
    }
  }

  implicit val supervisorFactory = domainContext.entitySupervisorFactory[TestAggregate]

  val supervisor: ActorRef = EntitySupervisor.forType[TestAggregate]
  
  "Local commit publisher" must {
    
    "publish commit" in new fixture {
      val commit = commitPublication.commit
      commit.records.map(_.event) should be(Seq(Created(testId)))
      commit.records.head.tag.revision should be(AggregateRevision(1L))
    }

    "include commander" in new fixture {
      commitPublication.commanderOption should be(Some(self))
    }
    
    "not have confirmation" in new fixture {
      commitPublication.confirmationOption should be('empty)
    }

    "not request confirmation" in new fixture {
      commitPublication.confirmIfRequested()
      expectNoMsg(100.millis)
    }

    trait fixture extends {
      val publishedEventProbe = TestProbe()
      system.eventStream.subscribe(publishedEventProbe.ref, classOf[Any])

      val testId = TestId.generate()
      supervisor ! Create(testId)
      expectMsgType[AggregateResult.Success]

      val commitPublication = publishedEventProbe.expectMsgType[CommitPublication]
    }
  }
}