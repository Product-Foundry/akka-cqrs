package com.productfoundry.akka.serialization

import com.google.protobuf.Message

trait EntityMessageSerializer {

  type EventManifest = PartialFunction[AnyRef, String]

  type EventToBinary = PartialFunction[AnyRef, Message]

  type EventFromBinary = PartialFunction[String, (Array[Byte]) => AnyRef]

  /**
    * @return Partial function to map an event to a manifest.
    */
  def eventManifest: EventManifest

  /**
    * @return Partial function to map an event to a serialized message.
    */
  def eventToBinary: EventToBinary

  /**
    * @return Partial function to deserialize an event based on it's manifest and bytes.
    */
  def eventFromBinary: EventFromBinary
}

