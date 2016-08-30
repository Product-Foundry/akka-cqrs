package com.productfoundry.akka.serialization

import akka.actor.ExtendedActorSystem
import akka.event.Logging
import akka.serialization._
import com.google.protobuf.ByteString
import com.productfoundry.akka.cqrs._
import com.productfoundry.akka.cqrs.process.DeduplicationEntry
import com.productfoundry.akka.cqrs.publish.EventPublication
import com.productfoundry.akka.messaging.{ConfirmDeliveryRequest, ConfirmedDelivery}
import com.productfoundry.akka.serialization.{PersistableProtos => proto}

import scala.collection.JavaConverters._
import scala.util.{Failure, Success, Try}

/**
  * Marker trait for persistables.
  */
trait Persistable extends Serializable

/**
  * Protobuf serializer for [[com.productfoundry.akka.serialization.Persistable]] messages.
  */
class PersistableSerializer(val system: ExtendedActorSystem) extends SerializerWithStringManifest with BaseSerializer {

  private lazy val serialization = SerializationExtension(system)

  private val log = Logging(system, getClass.getName)

  override def manifest(o: AnyRef): String = o match {
    case _: Commit => "Commit"
    case _: ConfirmedDelivery => "ConfirmedDelivery"
    case _: DeduplicationEntry => "DeduplicationEntry"
    case _: AggregateEventRecord => "AggregateEventRecord"
    case _: AggregateSnapshot => "AggregateSnapshot"
    case _: ConfirmDeliveryRequest => "ConfirmDeliveryRequest"
    case _: EventPublication => "EventPublication"
  }

  override def toBinary(o: AnyRef): Array[Byte] = o match {
    case persistable: Commit => persistentCommit(persistable).build().toByteArray
    case persistable: ConfirmedDelivery => persistentConfirmedDelivery(persistable).build().toByteArray
    case persistable: DeduplicationEntry => persistentDeduplicationEntry(persistable).build().toByteArray
    case persistable: AggregateEventRecord => persistentAggregateEventRecord(persistable).build().toByteArray
    case persistable: AggregateSnapshot => persistentAggregateSnapshot(persistable).build().toByteArray
    case persistable: ConfirmDeliveryRequest => persistentConfirmDeliveryRequest(persistable).build().toByteArray
    case persistable: EventPublication => persistentEventPublication(persistable).build().toByteArray
  }

  override def fromBinary(bytes: Array[Byte], manifest: String): AnyRef = manifest match {
    case "Commit" => commit(proto.Commit.parseFrom(bytes))
    case "ConfirmedDelivery" => confirmedDelivery(proto.ConfirmedDelivery.parseFrom(bytes))
    case "DeduplicationEntry" => deduplicationEntry(proto.DeduplicationEntry.parseFrom(bytes))
    case "AggregateEventRecord" => eventRecord(proto.AggregateEventRecord.parseFrom(bytes))
    case "AggregateSnapshot" => aggregateSnapshot(proto.AggregateSnapshot.parseFrom(bytes))
    case "ConfirmDeliveryRequest" => confirmDeliveryRequest(proto.ConfirmDeliveryRequest.parseFrom(bytes))
    case "EventPublication" => eventPublication(proto.EventPublication.parseFrom(bytes))
  }

  private def commit(persistent: proto.Commit): Commit = {

    val entryOptions = persistent.getEntriesList.asScala.map { persistentCommitEntry =>

      val eventOption = aggregateEvent(persistentCommitEntry.getEvent) match {
        case Success(event) => Some(event)
        case Failure(UnknownEventException(manifest)) =>
          log.warning("Ignoring event with manifest {}", manifest)
          None
        case Failure(e) => throw e
      }

      eventOption.map { event =>
        CommitEntry(AggregateRevision(persistentCommitEntry.getRevision), event)
      }
    }

    Commit(
      aggregateTag(persistent.getTag),
      if (persistent.hasHeaders) Some(commitHeaders(persistent.getHeaders)) else None,
      entryOptions.flatten
    )
  }

  private def confirmedDelivery(persistent: proto.ConfirmedDelivery): ConfirmedDelivery = {
    ConfirmedDelivery(
      persistent.getDeliveryId
    )
  }

  private def eventRecord(persistent: proto.AggregateEventRecord): AggregateEventRecord = {
    AggregateEventRecord(
      aggregateTag(persistent.getTag),
      if (persistent.hasHeaders) Some(commitHeaders(persistent.getHeaders)) else None,
      aggregateEvent(persistent.getEvent).get
    )
  }

  private def confirmDeliveryRequest(persistent: proto.ConfirmDeliveryRequest): ConfirmDeliveryRequest = {
    ConfirmDeliveryRequest(
      system.provider.resolveActorRef(persistent.getTarget),
      persistent.getDeliveryId
    )
  }

  private def eventPublication(persistent: proto.EventPublication): EventPublication = {
    EventPublication(
      eventRecord(persistent.getEventRecord),
      if (persistent.hasConfirmation) Some(confirmDeliveryRequest(persistent.getConfirmation)) else None,
      if (persistent.hasCommander) Some(system.provider.resolveActorRef(persistent.getCommander)) else None
    )
  }

  private def deduplicationEntry(persistent: proto.DeduplicationEntry): DeduplicationEntry = {
    DeduplicationEntry(persistent.getDeduplicationId)
  }

  private def aggregateTag(persistent: proto.AggregateTag): AggregateTag = {
    AggregateTag(
      persistent.getName,
      persistent.getId,
      AggregateRevision(persistent.getRevision)
    )
  }

  private def commitHeaders(persistent: proto.CommitHeaders): CommitHeaders = {

    val manifest = if (persistent.hasHeadersManifest) {
      persistent.getHeadersManifest.toStringUtf8
    } else {
      ""
    }

    serialization.deserialize(
      persistent.getHeaders.toByteArray,
      persistent.getSerializerId,
      manifest
    ).get.asInstanceOf[CommitHeaders]
  }

  private def aggregateEvent(persistent: proto.AggregateEvent): Try[AggregateEvent] = {
    serialization.deserialize(
      persistent.getEvent.toByteArray,
      persistent.getSerializerId,
      if (persistent.hasEventManifest) persistent.getEventManifest.toStringUtf8 else ""
    ).map(_.asInstanceOf[AggregateEvent])
  }

  private def persistentCommit(commit: Commit): proto.Commit.Builder = {
    val builder = proto.Commit.newBuilder()

    builder.setTag(persistentAggregateTag(commit.tag))

    commit.headersOption.foreach { headers =>
      builder.setHeaders(persistentCommitHeaders(headers))
    }

    commit.entries.foreach { entry =>
      val entryBuilder = proto.Commit.CommitEntry.newBuilder()
      entryBuilder.setRevision(entry.revision.value)
      entryBuilder.setEvent(persistentAggregateEvent(entry.event))
      builder.addEntries(entryBuilder)
    }

    builder
  }

  private def persistentConfirmedDelivery(confirmedDelivery: ConfirmedDelivery): proto.ConfirmedDelivery.Builder = {
    val builder = proto.ConfirmedDelivery.newBuilder()
    builder.setDeliveryId(confirmedDelivery.deliveryId)
    builder
  }

  private def persistentAggregateEventRecord(eventRecord: AggregateEventRecord): proto.AggregateEventRecord.Builder = {
    val builder = proto.AggregateEventRecord.newBuilder()
    builder.setTag(persistentAggregateTag(eventRecord.tag))
    eventRecord.headersOption.foreach(headers => builder.setHeaders(persistentCommitHeaders(headers)))
    builder.setEvent(persistentAggregateEvent(eventRecord.event))
    builder
  }

  private def aggregateSnapshot(persistent: proto.AggregateSnapshot): AggregateSnapshot = {
    val persistentSnapshot = persistent.getSnapshot

    val snapshotAttempt = serialization.deserialize(
      persistentSnapshot.getSnapshot.toByteArray,
      persistentSnapshot.getSerializerId,
      if (persistentSnapshot.hasSnapshotManifest) persistentSnapshot.getSnapshotManifest.toStringUtf8 else ""
    )

    AggregateSnapshot(
      AggregateRevision(persistent.getRevision),
      snapshotAttempt.get
    )
  }

  private def persistentAggregateSnapshot(aggregateSnapshot: AggregateSnapshot): proto.AggregateSnapshot.Builder = {
    val builder = proto.AggregateSnapshot.newBuilder()
    builder.setRevision(aggregateSnapshot.revision.value)

    val snapshot = aggregateSnapshot.snapshot.asInstanceOf[AnyRef]
    val serializer = serialization.findSerializerFor(snapshot)
    val snapshotBuilder = proto.AggregateSnapshot.Snapshot.newBuilder()
    snapshotBuilder.setSerializerId(serializer.identifier)
    createManifestOption(serializer, snapshot).foreach(snapshotBuilder.setSnapshotManifest)
    snapshotBuilder.setSnapshot(ByteString.copyFrom(serializer.toBinary(snapshot)))

    builder.setSnapshot(snapshotBuilder)

    builder
  }

  private def persistentConfirmDeliveryRequest(confirmDeliveryRequest: ConfirmDeliveryRequest): proto.ConfirmDeliveryRequest.Builder = {
    val builder = proto.ConfirmDeliveryRequest.newBuilder()
    builder.setTarget(Serialization.serializedActorPath(confirmDeliveryRequest.target))
    builder.setDeliveryId(confirmDeliveryRequest.deliveryId)
    builder
  }

  private def persistentEventPublication(eventPublication: EventPublication): proto.EventPublication.Builder = {
    val builder = proto.EventPublication.newBuilder()
    builder.setEventRecord(persistentAggregateEventRecord(eventPublication.eventRecord))
    eventPublication.confirmationOption.foreach(confirmation => builder.setConfirmation(persistentConfirmDeliveryRequest(confirmation)))
    eventPublication.commanderOption.foreach(commander => builder.setCommander(Serialization.serializedActorPath(commander)))
    builder
  }

  private def persistentDeduplicationEntry(deduplicationEntry: DeduplicationEntry): proto.DeduplicationEntry.Builder = {
    val builder = proto.DeduplicationEntry.newBuilder()
    builder.setDeduplicationId(deduplicationEntry.deduplicationId)
    builder
  }

  private def persistentAggregateTag(aggregateTag: AggregateTag): proto.AggregateTag.Builder = {
    val builder = proto.AggregateTag.newBuilder()

    builder.setName(aggregateTag.name)
    builder.setId(aggregateTag.id)
    builder.setRevision(aggregateTag.revision.value)
    builder
  }

  private def persistentCommitHeaders(headers: CommitHeaders): proto.CommitHeaders.Builder = {
    val serializer = serialization.findSerializerFor(headers)
    val builder = proto.CommitHeaders.newBuilder()

    builder.setSerializerId(serializer.identifier)
    createManifestOption(serializer, headers).foreach(builder.setHeadersManifest)
    builder.setHeaders(ByteString.copyFrom(serializer.toBinary(headers)))

    builder
  }

  private def persistentAggregateEvent(event: AggregateEvent): proto.AggregateEvent.Builder = {
    val payload = event.asInstanceOf[AnyRef]
    val serializer = serialization.findSerializerFor(payload)
    val builder = proto.AggregateEvent.newBuilder()

    builder.setSerializerId(serializer.identifier)
    createManifestOption(serializer, payload).foreach(builder.setEventManifest)
    builder.setEvent(ByteString.copyFrom(serializer.toBinary(payload)))
  }

  private def createManifestOption(serializer: Serializer, o: AnyRef): Option[ByteString] = {
    serializer match {
      case ser: SerializerWithStringManifest =>
        val manifest = ser.manifest(o)
        if (manifest == "") {
          None
        } else {
          Some(ByteString.copyFromUtf8(manifest))
        }

      case _ =>
        if (serializer.includeManifest) {
          Some(ByteString.copyFromUtf8(o.getClass.getName))
        } else {
          None
        }
    }
  }
}
