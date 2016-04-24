package com.productfoundry.akka.serialization

import akka.serialization.{BaseSerializer, SerializerWithStringManifest}
import com.google.protobuf.GeneratedMessage
import com.productfoundry.akka.cqrs.AggregateEvent

trait AggregateEventSerializer[T <: AggregateEvent] extends SerializerWithStringManifest with BaseSerializer {

  override final def manifest(o: AnyRef): String = {
    eventManifest(o.asInstanceOf[T])
  }

  override final def toBinary(o: AnyRef): Array[Byte] = {
    eventToBinary(o.asInstanceOf[T]).build().toByteArray
  }

  override final def fromBinary(bytes: Array[Byte], manifest: String): AnyRef = {
    if (eventFromBinary.isDefinedAt(manifest)) {
      eventFromBinary(manifest)(bytes)
    } else {
      throw new UnknownEventException(manifest)
    }
  }

  def eventManifest: PartialFunction[T, String]

  def eventToBinary: PartialFunction[T, GeneratedMessage.Builder[_]]

  def eventFromBinary: PartialFunction[String, (Array[Byte]) => T]
}
