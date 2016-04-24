package com.productfoundry.akka.serialization

/**
  * Thrown when unable to deserialize an event because the manifest is unknown.
  */
case class UnknownEventException(manifest: String) extends IllegalStateException(s"Unable to deserialize event with manifest $manifest")
