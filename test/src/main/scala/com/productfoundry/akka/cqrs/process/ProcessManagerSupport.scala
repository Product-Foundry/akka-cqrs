package com.productfoundry.akka.cqrs.process

import java.util.UUID

import akka.actor._
import akka.testkit.TestProbe
import com.productfoundry.akka.cqrs._
import com.productfoundry.akka.cqrs.publish.EventPublication

import scala.concurrent.Await
import scala.reflect.ClassTag

/**
  * Base spec for testing process managers.
  * @param _system test actor system.
  */
abstract class ProcessManagerSupport(_system: ActorSystem)
  extends EntitySupport(_system) {

  var domainContext: LocalDomainContext = null
  var processManagerRegistry: ProcessManagerRegistry = null

  before {
    domainContext = new LocalDomainContext(system, UUID.randomUUID().toString)
    processManagerRegistry = ProcessManagerRegistry(system, domainContext)
  }

  after {
    terminateConfirmed(
      processManagerRegistry.actor,
      domainContext.actor
    )

    domainContext = null
    processManagerRegistry = null
  }

  /**
    * Scoped fixture to setup aggregates and send messages while keeping track of revisions.
    */
  trait ProcessManagerFixture {
    val commandReceiver = TestProbe()

    val aggregateFactory = new AggregateFactoryProvider {
      override def apply[A <: Aggregate : ClassTag]: ActorRef = commandReceiver.ref
    }

    private var aggregateRevisionByName = Map.empty[String, AggregateRevision]

    private def aggregateRevision(name: String): AggregateRevision = {
      val revision = aggregateRevisionByName.getOrElse(name, AggregateRevision.Initial)
      aggregateRevisionByName = aggregateRevisionByName.updated(name, revision.next)
      revision
    }

    def createEventRecord(event: AggregateEvent,
                          nameOption: Option[String] = None,
                          headersOption: Option[AggregateEventHeaders] = None): AggregateEventRecord = {

      val aggregateName = nameOption.getOrElse(event.getClass.getSimpleName)

      AggregateEventRecord(
        AggregateTag(aggregateName, event.id.entityId, aggregateRevision(aggregateName)),
        headersOption,
        event
      )
    }

    def register[P <: ProcessManager : ProcessManagerCompanion : ClassTag](factory: ProcessManagerFactory[P]): Unit = {
      Await.result(processManagerRegistry.register(factory), executionTimeout.duration)
    }

    def publishEvents(events: AggregateEvent*): Unit = {
      publishEventRecords(events.map(event => createEventRecord(event)): _*)
    }

    def publishEventRecords(eventRecords: AggregateEventRecord*): Unit = {
      eventRecords.foreach { eventRecord =>
        val publication = EventPublication(eventRecord)
        processManagerRegistry.actor ! publication
      }
    }
  }
}
