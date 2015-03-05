package com.productfoundry.akka.cqrs

import akka.persistence.PersistentView

class DomainProjector(val persistenceId: String) extends PersistentView {

  override def viewId: String = s"$persistenceId-view"

  override def receive: Receive = {
    case msg =>
      println(s"Commit aggregator projector: $msg")
  }
}
