package com.productfoundry.akka.cqrs.process

import com.productfoundry.akka.cqrs.EntityFactory

/**
  * creates a process manager.
  * @tparam P Process manager type.
  */
trait ProcessManagerFactory[P <: ProcessManager] extends EntityFactory[P]
