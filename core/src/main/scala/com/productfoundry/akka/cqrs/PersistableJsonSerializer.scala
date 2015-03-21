package com.productfoundry.akka.cqrs

import java.nio.charset.Charset

import akka.serialization.Serializer
import play.api.libs.json.{JsValue, Format, Json}

class PersistableJsonSerializer(implicit val AggregateEventFormat: Format[AggregateEvent]) extends Serializer {

  val JsonCharset: Charset = Charset.forName("UTF-8")

  override def identifier: Int = 34254991

  override def includeManifest: Boolean = false

  override def toBinary(o: AnyRef): Array[Byte] = {
    val json = toJson(o.asInstanceOf[Persistable])
    val jsonString = Json.stringify(json)
    jsonString.getBytes(JsonCharset)
  }

  private def toJson(persistable: Persistable): JsValue = persistable match {
    case p: Commit[AggregateEvent] => Json.toJson(p)
    case p: DomainCommit[AggregateEvent] => Json.toJson(p)
  }

  override def fromBinary(bytes: Array[Byte], manifest: Option[Class[_]]): AnyRef = {
    val jsonString = new String(bytes, JsonCharset)
    val json = Json.parse(jsonString)

    json.asOpt[Commit[AggregateEvent]]
      .orElse(json.asOpt[DomainCommit[AggregateEvent]])
      .getOrElse(throw new IllegalArgumentException(s"Unable to deserialize: $jsonString"))
  }
}