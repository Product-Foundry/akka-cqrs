package com.productfoundry.akka.cqrs.confirm

import akka.actor._
import com.productfoundry.akka.cqrs.confirm.ConfirmablePublisher.{Passivate, RedeliverUnconfirmed}
import com.productfoundry.akka.cqrs.confirm.ConfirmationProtocol._

import scala.concurrent.duration.Duration
import scala.language.existentials

private class ConfirmablePublisher(confirmable: Confirmable, destinations: Set[ActorPath], timeout: Duration) extends Actor {

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

    case Confirm(deliveryId) =>
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
      self ! PoisonPill
    }
  }
}

case object ConfirmablePublisher {

  case object RedeliverUnconfirmed

  case class Passivate(confirmable: Confirmable)

}