package com.productfoundry.akka.cqrs.process

import akka.actor.ActorRef
import com.productfoundry.akka.cqrs._
import com.productfoundry.akka.cqrs.publish.EventPublication
import com.productfoundry.akka.messaging.ConfirmDelivery
import com.productfoundry.support.EntityTestSupport
import org.scalatest.prop.GeneratorDrivenPropertyChecks

class ProcessManagerSpec extends EntityTestSupport with GeneratorDrivenPropertyChecks with Fixtures {

  implicit def DummyProcessManagerIdResolution = DummyProcessManager.idResolution

  implicit def DummyProcessManagerFactory = DummyProcessManager.factory()

  implicit val supervisorFactory = entityContext.entitySupervisorFactory[DummyProcessManager]

  val supervisor: ActorRef = EntitySupervisor.forType[DummyProcessManager]

  "Event publications" must {

    "be received" in new ProcessManagerFixture {
      forAll { commit: Commit =>
        val publications = createUniquePublications(commit)

        publications.foreach { publication =>
          supervisor ! publication
        }

        val events = receiveN(publications.size).map(_.asInstanceOf[AggregateEvent])
        publications.map(_.eventRecord.event) should contain theSameElementsAs events
      }

      expectNoMsg()
    }

    "be confirmed" in new ProcessManagerFixture {
      var nextDeliveryId = 1L

      forAll { commit: Commit =>
        val publications = createUniquePublications(commit)

        publications.foreach { publication =>
          supervisor ! publication.requestConfirmation(nextDeliveryId)
          nextDeliveryId = nextDeliveryId + 1
        }

        if (publications.nonEmpty) {
          val results = receiveN(publications.size * 2)
          val events = results.filter(p => classOf[AggregateEvent].isAssignableFrom(p.getClass))
          publications.map(_.eventRecord.event) should contain theSameElementsAs events

          val confirmations = results.filter(p => classOf[ConfirmDelivery].isAssignableFrom(p.getClass))
          confirmations.size should be(events.size)
        }
      }

      expectNoMsg()
    }

    "be deduplicated" in new ProcessManagerFixture {
      forAll { commit: Commit =>
        val publications = createUniquePublications(commit)

        publications.foreach { publication =>
          supervisor ! publication
          supervisor ! publication
        }

        val events = receiveN(publications.size)
        publications.map(_.eventRecord.event) should contain theSameElementsAs events
      }

      expectNoMsg()
    }
  }

  trait ProcessManagerFixture {

    system.eventStream.subscribe(self, classOf[Any])

    def createUniquePublications(commit: Commit): Seq[EventPublication] = {
      commit.records.map(eventRecord => EventPublication(eventRecord)).groupBy(_.eventRecord.tag).map(_._2.head).toSeq
    }
  }

}
