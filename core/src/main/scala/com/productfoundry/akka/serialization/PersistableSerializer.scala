package com.productfoundry.akka.serialization

import akka.actor.ExtendedActorSystem
import akka.event.Logging
import akka.persistence.AtLeastOnceDelivery.AtLeastOnceDeliverySnapshot
import akka.serialization._
import com.google.protobuf.ByteString
import com.productfoundry.akka.cqrs._
import com.productfoundry.akka.cqrs.process.DeduplicationEntry
import com.productfoundry.akka.cqrs.publish.{EventPublication, ReliableEventPublisherSnapshot}
import com.productfoundry.akka.cqrs.snapshot.{AggregateSnapshot, AggregateStateSnapshot}
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

  override def manifest(o: AnyRef): String = ""

  override def toBinary(o: AnyRef): Array[Byte] = {
    val builder = proto.Persistable.newBuilder()

    o match {
      case persistable: Commit => builder.setCommit(persistentCommit(persistable))
      case persistable: ConfirmedDelivery => builder.setConfirmedDelivery(persistentConfirmedDelivery(persistable))
      case persistable: DeduplicationEntry => builder.setDeduplicationEntry(persistentDeduplicationEntry(persistable))
      case persistable: AggregateEventRecord => builder.setAggregateEventRecord(persistentAggregateEventRecord(persistable))
      case persistable: AggregateSnapshot => builder.setAggregateSnapshot(persistentAggregateSnapshot(persistable))
      case persistable: ConfirmDeliveryRequest => builder.setConfirmDeliveryRequest(persistentConfirmDeliveryRequest(persistable))
      case persistable: EventPublication => builder.setEventPublication(persistentEventPublication(persistable))
      case persistable: ReliableEventPublisherSnapshot => builder.setReliableEventPublisherSnapshot(persistentReliableEventPublisherSnapshot(persistable))
    }

    builder.build().toByteArray
  }

  override def fromBinary(bytes: Array[Byte], manifest: String): AnyRef = manifest match {
    // Legacy messages stored with manifest
    case "Commit" => commit(proto.Commit.parseFrom(bytes))
    case "ConfirmedDelivery" => confirmedDelivery(proto.ConfirmedDelivery.parseFrom(bytes))
    case "DeduplicationEntry" => deduplicationEntry(proto.DeduplicationEntry.parseFrom(bytes))
    case "AggregateEventRecord" => aggregateEventRecord(proto.AggregateEventRecord.parseFrom(bytes))
    case "AggregateSnapshot" => aggregateSnapshot(proto.AggregateSnapshot.parseFrom(bytes))
    case "ConfirmDeliveryRequest" => confirmDeliveryRequest(proto.ConfirmDeliveryRequest.parseFrom(bytes))
    case "EventPublication" => eventPublication(proto.EventPublication.parseFrom(bytes))

    // New messages are contained in another Protobuf object that establishes the type
    case "" =>
      proto.Persistable.parseFrom(bytes) match {
        case p if p.hasCommit => commit(p.getCommit)
        case p if p.hasConfirmedDelivery => confirmedDelivery(p.getConfirmedDelivery)
        case p if p.hasDeduplicationEntry => deduplicationEntry(p.getDeduplicationEntry)
        case p if p.hasAggregateEventRecord => aggregateEventRecord(p.getAggregateEventRecord)
        case p if p.hasAggregateSnapshot => aggregateSnapshot(p.getAggregateSnapshot)
        case p if p.hasConfirmDeliveryRequest => confirmDeliveryRequest(p.getConfirmDeliveryRequest)
        case p if p.hasEventPublication => eventPublication(p.getEventPublication)
        case p if p.hasReliableEventPublisherSnapshot => reliableEventPublisherSnapshot(p.getReliableEventPublisherSnapshot)
        case p => throw new NoSuchElementException(s"Unexpected persistable: $p")
      }
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

  private def aggregateEventRecord(persistent: proto.AggregateEventRecord): AggregateEventRecord = {
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
      aggregateEventRecord(persistent.getEventRecord),
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

  private def deserializeSnapshot[A](persistent: proto.AggregateSnapshot.Snapshot): A = {
    val snapshotAttempt: Try[AnyRef] = serialization.deserialize(
      persistent.getSnapshot.toByteArray,
      persistent.getSerializerId,
      if (persistent.hasSnapshotManifest) persistent.getSnapshotManifest.toStringUtf8 else ""
    )

    snapshotAttempt match {
      case Success(value: A) =>
        value

      case Success(value) =>
        val message = s"Unexpected result deserializing snapshot: ${value.getClass.getName}"
        log.error(message)
        throw new IllegalArgumentException(message)

      case Failure(e) =>
        log.error(e, "Unexpected error deserializing snapshot")
        throw e
    }
  }

  private def deserializeSnapshotOption[A](hasSnapshot: Boolean, persistentF: () => proto.AggregateSnapshot.Snapshot): Option[A] = {
    if (hasSnapshot) {
      Some(deserializeSnapshot[A](persistentF()))
    } else {
      None
    }
  }

  private def serializeSnapshot(snapshot: AnyRef): proto.AggregateSnapshot.Snapshot.Builder = {
    val serializer = serialization.findSerializerFor(snapshot)
    val builder = proto.AggregateSnapshot.Snapshot.newBuilder()
    builder.setSerializerId(serializer.identifier)
    createManifestOption(serializer, snapshot).foreach(builder.setSnapshotManifest)
    builder.setSnapshot(ByteString.copyFrom(serializer.toBinary(snapshot)))
    builder
  }

  private def aggregateSnapshot(persistent: proto.AggregateSnapshot): AggregateSnapshot = {
    AggregateSnapshot(
      AggregateRevision(persistent.getRevision),
      deserializeSnapshotOption[AggregateStateSnapshot](persistent.hasStateSnapshot, persistent.getStateSnapshot),
      deserializeSnapshotOption[AtLeastOnceDeliverySnapshot](persistent.hasAtLeastOnceDeliverySnapshot, persistent.getAtLeastOnceDeliverySnapshot),
      deserializeSnapshotOption[ReliableEventPublisherSnapshot](persistent.hasReliableEventPublisherSnapshot, persistent.getReliableEventPublisherSnapshot)
    )
  }

  private def reliableEventPublisherSnapshot(persistent: proto.ReliableEventPublisherSnapshot):ReliableEventPublisherSnapshot  = {
    ReliableEventPublisherSnapshot(
      if (persistent.hasCurrentPublication) Some(eventPublication(persistent.getCurrentPublication)) else None,
      persistent.getPendingPublicationList.asScala.map(eventPublication).toVector
    )
  }

  private def persistentAggregateSnapshot(aggregateSnapshot: AggregateSnapshot): proto.AggregateSnapshot.Builder = {
    val builder = proto.AggregateSnapshot.newBuilder()

    builder.setRevision(aggregateSnapshot.revision.value)

    aggregateSnapshot.stateSnapshotOption.map(serializeSnapshot).foreach(builder.setStateSnapshot)
    aggregateSnapshot.atLeastOnceDeliverySnapshotOption.map(serializeSnapshot).foreach(builder.setAtLeastOnceDeliverySnapshot)
    aggregateSnapshot.reliableEventPublisherSnapshotOption.map(serializeSnapshot).foreach(builder.setReliableEventPublisherSnapshot)

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

  private def persistentReliableEventPublisherSnapshot(snapshot: ReliableEventPublisherSnapshot): proto.ReliableEventPublisherSnapshot.Builder = {
    val builder = proto.ReliableEventPublisherSnapshot.newBuilder()
    snapshot.currentPublicationOption.map(persistentEventPublication).foreach(builder.setCurrentPublication)
    snapshot.pendingPublications.map(persistentEventPublication).foreach(builder.addPendingPublication)
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
