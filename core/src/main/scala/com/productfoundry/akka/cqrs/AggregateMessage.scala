package com.productfoundry.akka.cqrs

/**
  * Message that can be sent to an aggregate.
  */
trait AggregateMessage extends EntityMessage {

  type Id <: EntityId

  /**
    * @return id of the aggregate.
    */
  def id: Id

  /**
    * @param clazz to check.
    * @return Indication whether the message is of the specified class type.
    */
  def hasType(clazz: Class[_]): Boolean = clazz.isAssignableFrom(getClass)

}