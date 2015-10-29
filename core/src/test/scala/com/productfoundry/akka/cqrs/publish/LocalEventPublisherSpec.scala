package com.productfoundry.akka.cqrs.publish

import akka.actor.{ActorRef, Props}
import akka.testkit.TestProbe
import com.productfoundry.akka.PassivationConfig
import com.productfoundry.akka.cqrs.DummyAggregate._
import com.productfoundry.akka.cqrs._
import com.productfoundry.support.AggregateTestSupport

class LocalEventPublisherSpec extends AggregateTestSupport {

  implicit object TestAggregateFactory extends AggregateFactory[DummyAggregate] {
    override def props(config: PassivationConfig): Props = {
      Props(new DummyAggregate(config) with LocalEventPublisher)
    }
  }

  implicit val supervisorFactory = domainContext.entitySupervisorFactory[DummyAggregate]

  val supervisor: ActorRef = EntitySupervisor.forType[DummyAggregate]
  
  "Local event publisher" must {
    
    "publish events" in new fixture {
      eventRecord.event should be(Created(testId))
      eventRecord.tag.revision should be(AggregateRevision(1L))
    }

    "include commander" in new fixture {
      eventPublication.notifyCommanderIfDefined("test")
      expectMsg("test")
    }
    
    "not have confirmation" in new fixture {
      eventPublication.confirmationOption should be('empty)
    }

    "not request confirmation" in new fixture {
      eventPublication.confirmIfRequested()
      expectNoMsg()
    }

    trait fixture extends {
      val publishedEventProbe = TestProbe()
      system.eventStream.subscribe(publishedEventProbe.ref, classOf[Any])

      val testId = DummyId.generate()
      supervisor ! Create(testId)
      expectMsgType[AggregateStatus.Success]

      val eventPublication = publishedEventProbe.expectMsgType[EventPublication]
      val eventRecord = eventPublication.eventRecord
    }
  }
}