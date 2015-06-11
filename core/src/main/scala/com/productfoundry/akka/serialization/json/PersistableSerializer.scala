package com.productfoundry.akka.serialization.json

import java.nio.charset.Charset

import akka.serialization.Serializer
import com.productfoundry.akka.cqrs.AggregateEvent
import com.productfoundry.akka.serialization.Persistable
import play.api.libs.json.{Format, Json}

class PersistableSerializer(implicit val eventFormat: Format[AggregateEvent]) extends Serializer {

  val persistableFormat = PersistableFormat()

  import persistableFormat._

  val JsonCharset: Charset = Charset.forName("UTF-8")

  override def identifier: Int = 34254991

  override def includeManifest: Boolean = false

  override def toBinary(o: AnyRef): Array[Byte] = {
    val persistable = o.asInstanceOf[Persistable]
    val json = Json.toJson(persistable)
    val jsonString = Json.stringify(json)
    jsonString.getBytes(JsonCharset)
  }

  override def fromBinary(bytes: Array[Byte], manifest: Option[Class[_]]): AnyRef = {
    val jsonString = new String(bytes, JsonCharset)
    val json = Json.parse(jsonString)
    json.as[Persistable]
  }
}