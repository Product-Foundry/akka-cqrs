package com.productfoundry.akka.cqrs.process

import akka.actor.Status.Success
import akka.actor._
import akka.pattern.ask
import akka.util.Timeout
import com.productfoundry.akka.cqrs.process.ProcessManagerRegistryActor.Register
import com.productfoundry.akka.cqrs.publish.{EventPublication, EventSubscriber}
import com.productfoundry.akka.cqrs.{EntityIdResolution, AggregateEventRecord, DomainContext}

import scala.concurrent.Future
import scala.language.existentials
import scala.reflect.ClassTag

object ProcessManagerRegistry {
  def apply(actorRefFactory: ActorRefFactory, domainContext: DomainContext) = {
    new ProcessManagerRegistry(actorRefFactory, domainContext)
  }
}

/**
 * Keeps track of all active process managers and their event mappings.
 */
class ProcessManagerRegistry(actorRefFactory: ActorRefFactory, domainContext: DomainContext) {

  val actor = actorRefFactory.actorOf(Props(new ProcessManagerRegistryActor))

  def register[P <: ProcessManager[_, _] : ProcessManagerFactory : EntityIdResolution : ClassTag](implicit timeout: Timeout): Future[Any] = {
    val supervisorFactory = domainContext.entitySupervisorFactory[P]
    val supervisorName = supervisorFactory.supervisorName
    val supervisorRef = supervisorFactory.getOrCreate
    val idResolution = implicitly[EntityIdResolution[P]]
    actor ? Register(supervisorName, supervisorRef, idResolution)
  }
}

object ProcessManagerRegistryActor {

  case class Register(supervisorName: String, supervisorRef: ActorRef, idResolution: EntityIdResolution[_<: ProcessManager[_, _]])
}

class ProcessManagerRegistryActor
  extends Actor
  with EventSubscriber
  with ActorLogging {

  import ProcessManagerRegistryActor._

  case class Registration(supervisorRef: ActorRef, idResolution: EntityIdResolution[_<: ProcessManager[_, _]])

  private var registrations = Map.empty[String, Registration]

  override def receive: Receive = receivePublishedEvent orElse {
    case Register(supervisorName, supervisorRef, idResolution) =>
      registrations = registrations.updated(supervisorName, Registration(supervisorRef, idResolution))
      sender() ! Success(Unit)
  }

  override def eventReceived: ReceiveEventRecord = {
    case eventRecord: AggregateEventRecord =>
      registrations.foreach { case (supervisorName, registration) =>
        val publication = EventPublication(eventRecord)
        if (registration.idResolution.entityIdResolver.isDefinedAt(publication)) {
          log.info("{} subscribed to event: {}", supervisorName, eventRecord)
          registration.supervisorRef ! publication
        } else {
          log.info("{} ignores event: {}", supervisorName, eventRecord)
        }
      }
  }
}