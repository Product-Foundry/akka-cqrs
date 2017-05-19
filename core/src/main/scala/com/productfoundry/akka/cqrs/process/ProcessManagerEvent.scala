package com.productfoundry.akka.cqrs.process

import com.productfoundry.akka.cqrs.EntityState

/**
  * Process manager event used for persisting process state
  */
trait ProcessManagerEvent
  extends EntityState