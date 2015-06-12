package com.productfoundry.akka.cqrs.process

import com.productfoundry.akka.cqrs.EntityIdResolution

/**
 * Process manager ids are different for every process and define which process instances receive
 * a message. Multiple processes can receive the same messages with possible a different process manager id.
 */
trait ProcessManagerIdResolution[A <: ProcessManager[_, _]] extends EntityIdResolution[A]