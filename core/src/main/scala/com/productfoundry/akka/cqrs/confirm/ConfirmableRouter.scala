package com.productfoundry.akka.cqrs.confirm

import akka.actor._
import com.productfoundry.akka.cqrs.confirm.ConfirmablePublisher._
import com.productfoundry.akka.cqrs.confirm.ConfirmableRouter._

import scala.concurrent.duration._

class ConfirmableRouter(val timeout: Duration = 30.minutes) extends Actor with ActorLogging {

  private var subscribers: Set[ActorPath] = Set.empty

  private var publishersByConfirmable: Map[Confirmable, ActorRef] = Map.empty

  override def receive: Receive = {

    case Subscribe(subscriber) =>
      subscribers += subscriber

    case Unsubscribe(subscriber) =>
      subscribers -= subscriber

    case confirmable: Confirmable =>
      publishersByConfirmable.get(confirmable).fold {
        val publisher = context.actorOf(Props(new ConfirmablePublisher(confirmable, subscribers, timeout)))
        publishersByConfirmable = publishersByConfirmable.updated(confirmable, publisher)
      } { publisher =>
        publisher ! ConfirmablePublisher.RedeliverUnconfirmed
      }

    case Passivate(confirmable) =>
      log.warning("Timeout handling: {}", confirmable)
      publishersByConfirmable = publishersByConfirmable - confirmable
      sender() ! PoisonPill
  }
}

object ConfirmableRouter {

  case class Subscribe(subscriber: ActorPath)

  case class Unsubscribe(subscriber: ActorPath)

}
