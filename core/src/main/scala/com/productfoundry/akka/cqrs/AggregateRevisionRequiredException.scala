package com.productfoundry.akka.cqrs

case class AggregateRevisionRequiredException(command: AggregateCommand)
  extends AggregateException(s"Revision required for command: ${command.getClass.getName}")
