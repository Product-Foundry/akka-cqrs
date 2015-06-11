package com.productfoundry.akka.cqrs.process

/**
 * Exception indicating a critical process error.
 * @param message of the exception.
 */
abstract class ProcessManagerException(message: String) extends RuntimeException(message)