package com.productfoundry.akka.cqrs

case class AggregateInvalidEventClassException(event: AggregateEvent) extends AggregateException(s"Unable to handle event $event in aggregate")
