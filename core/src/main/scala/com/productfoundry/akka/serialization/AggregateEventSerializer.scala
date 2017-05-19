package com.productfoundry.akka.serialization

import akka.serialization.{BaseSerializer, SerializerWithStringManifest}
import com.productfoundry.akka.cqrs.AggregateEvent

trait AggregateEventSerializer[T <: AggregateEvent]
  extends SerializerWithStringManifest
    with BaseSerializer
    with EntityMessageSerializer {

  override final def manifest(o: AnyRef): String = {
    eventManifest(o.asInstanceOf[T])
  }

  override final def toBinary(o: AnyRef): Array[Byte] = {
    eventToBinary(o.asInstanceOf[T]).toByteArray
  }

  override final def fromBinary(bytes: Array[Byte], manifest: String): AnyRef = {
    if (eventFromBinary.isDefinedAt(manifest)) {
      eventFromBinary(manifest)(bytes)
    } else {
      throw UnknownEventException(manifest)
    }
  }
}
