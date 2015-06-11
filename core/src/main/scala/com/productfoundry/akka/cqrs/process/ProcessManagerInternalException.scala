package com.productfoundry.akka.cqrs.process

/**
 * Exception indicating an internal problem with the process, probably caused by a programming error.
 */
case class ProcessManagerInternalException(message: String)
  extends ProcessManagerException(message)
