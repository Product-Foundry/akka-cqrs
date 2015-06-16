package com.productfoundry.akka.cqrs.process

import akka.actor.{ActorRef, Props}
import com.productfoundry.akka.PassivationConfig
import com.productfoundry.akka.cqrs.EntityIdResolution.EntityIdResolver
import com.productfoundry.akka.cqrs._
import com.productfoundry.akka.cqrs.process.DummyProcessManager.LogEvent
import com.productfoundry.akka.cqrs.publish.EventPublication
import com.productfoundry.akka.messaging.Confirmable.Confirm
import com.productfoundry.support.EntityTestSupport
import org.scalatest.prop.GeneratorDrivenPropertyChecks

import scala.concurrent.duration._

class ProcessManagerSpec extends EntityTestSupport with GeneratorDrivenPropertyChecks with Fixtures {

  implicit object DummyProcessManagerIdResolution extends EntityIdResolution[DummyProcessManager] {
    override def entityIdResolver: EntityIdResolver = {
      case msg: EventPublication => msg.eventRecord.event.id.toString
    }
  }

  implicit object DummyProcessManagerFactory extends ProcessManagerFactory[DummyProcessManager] {
    override def props(config: PassivationConfig): Props = {
      Props(new DummyProcessManager(config))
    }
  }

  implicit val supervisorFactory = domainContext.entitySupervisorFactory[DummyProcessManager]

  val supervisor: ActorRef = EntitySupervisor.forType[DummyProcessManager]

  "Process manager" must {

    "receive published events" in new ProcessManagerFixture {
      forAll { commit: Commit =>
        val publications = createUniquePublications(commit)

        publications.foreach { publication =>
          supervisor ! publication
        }

        val events = receiveN(publications.size).map(_.asInstanceOf[LogEvent].event)
        publications.map(_.eventRecord.event) should contain theSameElementsAs events
      }

      expectNoMsg()
    }

    "confirm received messages" in new ProcessManagerFixture {
      var nextDeliveryId = 1L

      forAll { commit: Commit =>
        val publications = createUniquePublications(commit)

        publications.foreach { publication =>
          supervisor ! publication.requestConfirmation(nextDeliveryId)
          nextDeliveryId = nextDeliveryId + 1
        }

        if (publications.nonEmpty) {
          val results = receiveN(publications.size * 2)
          val grouped = results.groupBy(_.getClass)

          val events = grouped(classOf[LogEvent]).map(event => event.asInstanceOf[LogEvent].event)
          publications.map(_.eventRecord.event) should contain theSameElementsAs events
          grouped(classOf[Confirm]).size should be(events.size)
        }

        expectNoMsg(100.millis)
      }
    }
  }

  trait ProcessManagerFixture {
    def createUniquePublications(commit: Commit): Seq[EventPublication] = {
      commit.records.map(EventPublication.apply).groupBy(_.deduplicationId).map(_._2.head).toSeq
    }
  }

}
