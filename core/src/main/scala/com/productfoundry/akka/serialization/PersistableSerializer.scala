package com.productfoundry.akka.serialization

import akka.actor.ExtendedActorSystem
import akka.serialization._
import com.google.protobuf.ByteString
import com.productfoundry.akka.cqrs._
import com.productfoundry.akka.cqrs.project.ProjectionRevision
import com.productfoundry.akka.cqrs.project.domain.{DomainCommit, DomainAggregatorSnapshot}
import com.productfoundry.akka.messaging.{ConfirmedDelivery, DeduplicationEntry}
import com.productfoundry.akka.serialization.{PersistableProtos => proto}

/**
 * Marker trait for persistables.
 */
trait Persistable extends Serializable

/**
 * Protobuf serializer for [[com.productfoundry.akka.serialization.Persistable]] messages.
 */
class PersistableSerializer(val system: ExtendedActorSystem) extends SerializerWithStringManifest with BaseSerializer {

  val CommitManifest = "Commit"
  val ConfirmedDeliveryManifest = "ConfirmedDelivery"
  val DeduplicationEntryManifest = "DeduplicationEntry"
  val DomainCommitManifest = "DomainCommit"
  val DomainAggregatorSnapshotManifest = "DomainAggregatorSnapshot"

  private lazy val serialization = SerializationExtension(system)

  override def manifest(o: AnyRef): String = o match {
    case _: Commit => CommitManifest
    case _: ConfirmedDelivery => ConfirmedDeliveryManifest
    case _: DeduplicationEntry => DeduplicationEntryManifest
    case _: DomainCommit => DomainCommitManifest
    case _: DomainAggregatorSnapshot => DomainAggregatorSnapshotManifest
  }

  override def toBinary(o: AnyRef): Array[Byte] = o match {
    case c: Commit => persistentCommit(c).build().toByteArray
    case c: ConfirmedDelivery => persistentConfirmedDelivery(c).build().toByteArray
    case r: DeduplicationEntry => persistentDeduplicationEntry(r).build().toByteArray
    case d: DomainCommit => persistentDomainCommit(d).build().toByteArray
    case d: DomainAggregatorSnapshot => persistentDomainAggregatorSnapshot(d).build().toByteArray
  }

  override def fromBinary(bytes: Array[Byte], manifest: String): AnyRef = manifest match {
    case CommitManifest => commit(proto.PersistentCommit.parseFrom(bytes))
    case ConfirmedDeliveryManifest => confirmedDelivery(proto.PersistentConfirmedDelivery.parseFrom(bytes))
    case DeduplicationEntryManifest => deduplicationEntry(proto.PersistentDeduplicationEntry.parseFrom(bytes))
    case DomainCommitManifest => domainCommit(proto.PersistentDomainCommit.parseFrom(bytes))
    case DomainAggregatorSnapshotManifest => domainAggregatorSnapshot(proto.PersistentDomainAggregatorSnapshot.parseFrom(bytes))
  }

  private def commit(persistentCommit: proto.PersistentCommit): Commit = {
    import scala.collection.JavaConverters._

    val entries = persistentCommit.getEntriesList.asScala.map { persistentCommitEntry =>
      CommitEntry(
        AggregateRevision(persistentCommitEntry.getRevision),
        aggregateEvent(persistentCommitEntry.getEvent)
      )
    }

    Commit(
      aggregateTag(persistentCommit.getTag),
      aggregateEventHeaders(persistentCommit.getHeaders),
      entries.toSeq
    )
  }

  private def confirmedDelivery(persistentConfirmedDelivery: proto.PersistentConfirmedDelivery): ConfirmedDelivery = {
    ConfirmedDelivery(
      persistentConfirmedDelivery.getDeliveryId
    )
  }

  private def domainCommit(persistentDomainCommit: proto.PersistentDomainCommit): DomainCommit = {

    val persistentEventRecord = persistentDomainCommit.getEventRecord

    DomainCommit(
      ProjectionRevision(persistentDomainCommit.getRevision),
      AggregateEventRecord(
        aggregateTag(persistentEventRecord.getTag),
        aggregateEventHeaders(persistentEventRecord.getHeaders),
        aggregateEvent(persistentEventRecord.getEvent)
      )
    )
  }

  private def domainAggregatorSnapshot(persistentDomainAggregatorSnapshot: proto.PersistentDomainAggregatorSnapshot): DomainAggregatorSnapshot = {
    DomainAggregatorSnapshot(
      ProjectionRevision(persistentDomainAggregatorSnapshot.getRevision)
    )
  }

  private def deduplicationEntry(persistentDeduplicationEntry: proto.PersistentDeduplicationEntry): DeduplicationEntry = {
    DeduplicationEntry(persistentDeduplicationEntry.getDeduplicationId)
  }

  private def aggregateTag(persistentAggregateTag: proto.PersistentAggregateTag): AggregateTag = {
    AggregateTag(
      persistentAggregateTag.getName,
      persistentAggregateTag.getId,
      AggregateRevision(persistentAggregateTag.getRevision)
    )
  }

  private def aggregateEventHeaders(persistentAggregateEventHeaders: proto.PersistentAggregateEventHeaders): AggregateEventHeaders = {
    import scala.collection.JavaConverters._

    val metadata = persistentAggregateEventHeaders.getHeadersList.asScala.foldLeft(Map.empty[String, String]) {
      case (acc, persistentAggregateEventHeader) =>
        acc.updated(persistentAggregateEventHeader.getKey, persistentAggregateEventHeader.getValue)
    }

    AggregateEventHeaders(
      metadata,
      persistentAggregateEventHeaders.getTimestamp
    )
  }

  private def aggregateEvent(persistentAggregateEvent: proto.PersistentAggregateEvent): AggregateEvent = {

    val manifest = if (persistentAggregateEvent.hasEventManifest) {
      persistentAggregateEvent.getEventManifest.toStringUtf8
    } else {
      ""
    }

    serialization.deserialize(
      persistentAggregateEvent.getEvent.toByteArray,
      persistentAggregateEvent.getSerializerId,
      manifest
    ).get.asInstanceOf[AggregateEvent]
  }

  private def persistentCommit(commit: Commit): proto.PersistentCommit.Builder = {
    val builder = proto.PersistentCommit.newBuilder()

    builder.setTag(persistentAggregateTag(commit.tag))
    builder.setHeaders(persistentAggregateEventHeaders(commit.headers))

    commit.entries.foreach { entry =>
      val entryBuilder = proto.PersistentCommit.PersistentCommitEntry.newBuilder()
      entryBuilder.setRevision(entry.revision.value)
      entryBuilder.setEvent(persistentAggregateEvent(entry.event))
      builder.addEntries(entryBuilder)
    }

    builder
  }

  private def persistentConfirmedDelivery(confirmedDelivery: ConfirmedDelivery): proto.PersistentConfirmedDelivery.Builder = {
    val builder = proto.PersistentConfirmedDelivery.newBuilder()
    builder.setDeliveryId(confirmedDelivery.deliveryId)
    builder
  }

  private def persistentDomainCommit(domainCommit: DomainCommit): proto.PersistentDomainCommit.Builder = {
    val builder = proto.PersistentDomainCommit.newBuilder()

    val eventRecord = domainCommit.eventRecord
    val eventRecordBuilder = proto.PersistentDomainCommit.PersistentAggregateEventRecord.newBuilder()
    eventRecordBuilder.setTag(persistentAggregateTag(eventRecord.tag))
    eventRecordBuilder.setHeaders(persistentAggregateEventHeaders(eventRecord.headers))
    eventRecordBuilder.setEvent(persistentAggregateEvent(eventRecord.event))

    builder.setRevision(domainCommit.revision.value)
    builder.setEventRecord(eventRecordBuilder)
    builder
  }

  private def persistentDomainAggregatorSnapshot(domainAggregatorSnapshot: DomainAggregatorSnapshot): proto.PersistentDomainAggregatorSnapshot.Builder = {
    val builder = proto.PersistentDomainAggregatorSnapshot.newBuilder()
    builder.setRevision(domainAggregatorSnapshot.revision.value)
    builder
  }

  private def persistentDeduplicationEntry(deduplicationEntry: DeduplicationEntry): proto.PersistentDeduplicationEntry.Builder = {
    val builder = proto.PersistentDeduplicationEntry.newBuilder()
    builder.setDeduplicationId(deduplicationEntry.deduplicationId)
    builder
  }

  private def persistentAggregateTag(aggregateTag: AggregateTag): proto.PersistentAggregateTag.Builder = {
    val builder = proto.PersistentAggregateTag.newBuilder()

    builder.setName(aggregateTag.name)
    builder.setId(aggregateTag.id)
    builder.setRevision(aggregateTag.revision.value)
    builder
  }

  private def persistentAggregateEventHeaders(aggregateEventHeaders: AggregateEventHeaders): proto.PersistentAggregateEventHeaders.Builder = {
    val builder = proto.PersistentAggregateEventHeaders.newBuilder()

    aggregateEventHeaders.metadata.foreach {
      case (key, value) =>
        val headerBuilder = proto.PersistentAggregateEventHeaders.PersistentAggregateEventHeader.newBuilder()
        headerBuilder.setKey(key)
        headerBuilder.setValue(value)
        builder.addHeaders(headerBuilder)
    }

    builder.setTimestamp(aggregateEventHeaders.timestamp)
    builder
  }

  private def persistentAggregateEvent(event: AggregateEvent): proto.PersistentAggregateEvent.Builder = {
    val payload = event.asInstanceOf[AnyRef]
    val serializer = serialization.findSerializerFor(payload)
    val builder = proto.PersistentAggregateEvent.newBuilder()

    serializer match {
      case ser: SerializerWithStringManifest =>
        val manifest = ser.manifest(payload)
        if (manifest != "")
          builder.setEventManifest(ByteString.copyFromUtf8(manifest))
      case _ =>
        if (serializer.includeManifest)
          builder.setEventManifest(ByteString.copyFromUtf8(payload.getClass.getName))
    }

    builder.setSerializerId(serializer.identifier)
    builder.setEvent(ByteString.copyFrom(serializer.toBinary(payload)))
  }
}
