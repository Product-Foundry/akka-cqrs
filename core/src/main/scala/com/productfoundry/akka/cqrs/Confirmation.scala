package com.productfoundry.akka.cqrs

import akka.actor.ActorRef

case class Confirmation(target: ActorRef, deliveryId: Long)