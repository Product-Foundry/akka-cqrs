package com.productfoundry.akka.cqrs.process

import com.productfoundry.akka.cqrs.EntityIdResolution

trait ProcessManagerIdResolution[A <: ProcessManager[_, _]] extends EntityIdResolution[A]