package com.productfoundry.akka.cqrs

/**
  * Exception indicating an unknown command is send to an aggregate.
  */
case class AggregateCommandUnknownException(command: Any)
  extends RuntimeException(command.toString)