package com.productfoundry.akka.cqrs

case class AggregateInvalidMessageException(message: AggregateCommandMessage) extends AggregateException(s"Unable to handle message $message")
