package com.productfoundry.akka.cqrs.confirm

import akka.actor.ActorRef

case class Confirmation(target: ActorRef, deliveryId: Long)