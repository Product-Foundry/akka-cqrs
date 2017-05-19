package com.productfoundry.akka.cqrs

/**
  * Trait that indicates items that represent durable entity state.
  *
  * Applications should provide custom serialization for these types.
  */
trait EntityState
  extends Serializable
