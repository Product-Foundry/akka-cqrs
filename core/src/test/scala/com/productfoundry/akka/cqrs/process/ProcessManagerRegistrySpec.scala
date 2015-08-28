package com.productfoundry.akka.cqrs.process

import akka.util.Timeout
import com.productfoundry.akka.cqrs.EntityIdResolution._
import com.productfoundry.akka.cqrs._
import com.productfoundry.akka.cqrs.publish.EventPublication
import com.productfoundry.support.EntityTestSupport

import scala.concurrent.Await
import scala.concurrent.duration._

class ProcessManagerRegistrySpec
  extends EntityTestSupport
  with Fixtures {

  trait DummyEvent extends AggregateEvent {
    override type Id = DummyId
  }

  case class EventWithResolution(id: DummyId) extends DummyEvent

  case class EventToIgnore(id: DummyId) extends DummyEvent

  implicit object DummyProcessManagerIdResolution extends EntityIdResolution[DummyProcessManager] {

    private def entityIdFromEvent: EntityIdResolver = {
      case EventWithResolution(id) => id.toString
    }

    override def entityIdResolver: EntityIdResolver = {
      case publication: EventPublication if entityIdFromEvent.isDefinedAt(publication.eventRecord.event) =>
        entityIdFromEvent(publication.eventRecord.event)
    }
  }

  implicit def DummyProcessManagerFactory = DummyProcessManager.factory()

  val duration = 5.seconds

  implicit val timeout = Timeout(duration)

  "Process manager registry" must {

    "forward events" in new fixture {
      val event = EventWithResolution(DummyId.generate())
      registry.actor ! createPublication(event)

      expectMsgType[AggregateEvent] should be(event)
    }

    "ignore events" in new fixture {
      val event = EventToIgnore(DummyId.generate())
      registry.actor ! createPublication(event)

      expectNoMsg()
    }
  }

  trait fixture {
    val registry = ProcessManagerRegistry(system, domainContext)

    Await.result(registry.register[DummyProcessManager], duration)

    system.eventStream.subscribe(self, classOf[Any])

    def createPublication(event: AggregateEvent): EventPublication = {
      EventPublication(
        AggregateEventRecord(
          AggregateTag("", event.id.toString, AggregateRevision.Initial),
          AggregateEventHeaders(),
          event
        )
      )
    }
  }

}
