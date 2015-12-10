package com.productfoundry.akka.cqrs.publish

import akka.actor._
import com.productfoundry.akka.cqrs.publish.FanoutPublisher.PublicationHandler._
import com.productfoundry.akka.cqrs.publish.FanoutPublisher._
import com.productfoundry.akka.messaging.{ConfirmDelivery, Confirmable}

import scala.concurrent.duration._
import scala.language.existentials

class FanoutPublisher(val timeout: Duration = 30.minutes) extends Actor with ActorLogging {

  private var subscribers: Set[ActorPath] = Set.empty

  private var publishersByConfirmable: Map[Confirmable, ActorRef] = Map.empty

  override def receive: Receive = {

    case Subscribe(subscriber) =>
      subscribers += subscriber

    case Unsubscribe(subscriber) =>
      subscribers -= subscriber

    case confirmable: Confirmable =>
      publishersByConfirmable.get(confirmable).fold {
        val publisher = context.actorOf(Props(classOf[PublicationHandler], confirmable, subscribers, timeout))
        publishersByConfirmable = publishersByConfirmable.updated(confirmable, publisher)
      } { publisher =>
        publisher ! PublicationHandler.RedeliverUnconfirmed
      }

    case Passivate(confirmable) =>
      log.warning("Timeout handling: {}", confirmable)
      publishersByConfirmable = publishersByConfirmable - confirmable
      sender() ! PoisonPill
  }
}

object FanoutPublisher {

  case class Subscribe(subscriber: ActorPath)

  case class Unsubscribe(subscriber: ActorPath)

  private class PublicationHandler(confirmable: Confirmable, destinations: Set[ActorPath], timeout: Duration) extends Actor {

    private var destinationsByDeliveryId: Map[Long, ActorPath] = Map(
      destinations.toSeq.zipWithIndex.map {
        case (destination, deliveryId) => deliveryId.toLong -> destination
      }: _*
    )

    override def preStart(): Unit = {
      publishUnconfirmed()

      context.setReceiveTimeout(timeout)
    }

    override def receive: Receive = {

      case ConfirmDelivery(deliveryId) =>
        destinationsByDeliveryId = destinationsByDeliveryId - deliveryId
        confirmIfCompleted()

      case RedeliverUnconfirmed =>
        publishUnconfirmed()

      case ReceiveTimeout =>
        context.parent ! Passivate(confirmable)
    }

    private def publishUnconfirmed(): Unit = {
      destinationsByDeliveryId.foreach { case (deliveryId, destinationPath) =>
        context.system.actorSelection(destinationPath) ! confirmable.requestConfirmation(deliveryId)
      }

      confirmIfCompleted()
    }

    private def confirmIfCompleted(): Unit = {
      if (destinationsByDeliveryId.isEmpty) {
        confirmable.confirmIfRequested()
        context.stop(self)
      }
    }
  }

  case object PublicationHandler {

    case object RedeliverUnconfirmed

    case class Passivate(confirmable: Confirmable)

  }

}
