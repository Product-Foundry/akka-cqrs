package com.productfoundry.akka.cqrs

/**
 * Exception indicating a critical aggregate error.
 * @param message of the exception.
 */
abstract class AggregateException(message: String) extends RuntimeException(message)