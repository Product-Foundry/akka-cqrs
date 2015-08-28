package com.productfoundry.akka.cqrs.process

import com.productfoundry.akka.cqrs.EntityIdResolution

import scala.reflect.ClassTag

/**
 * Simplifies registration of process managers
 */
abstract class ProcessManagerCompanion[P <: ProcessManager[_, _]: ClassTag] {

  /**
   * Name of the process manager, based on class name
   */
  val name = implicitly[ClassTag[P]].runtimeClass.getSimpleName

  /**
   * Defines how to resolve ids for this process manager.
   *
   * Allows correlation of events for a process. The process manager will receive any event that resolves to an id.
   * @return id to correlate events.
   */
  def idResolution: EntityIdResolution[P]

  implicit val ProcessManagerCompanionObject: ProcessManagerCompanion[P] = this
}
