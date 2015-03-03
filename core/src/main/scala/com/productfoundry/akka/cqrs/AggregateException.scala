package com.productfoundry.akka.cqrs

case class AggregateException(message: String) extends RuntimeException(message)