package com.productfoundry.akka.snapshot

import akka.persistence.serialization.Snapshot
import akka.persistence.snapshot.SnapshotStore
import akka.persistence.{SelectedSnapshot, SnapshotMetadata, SnapshotSelectionCriteria}
import akka.serialization.SerializationExtension

import scala.concurrent.Future

class InMemorySnapshotStore extends SnapshotStore {

  val serialization = SerializationExtension(context.system)

  var snapshots: List[(SnapshotMetadata, Array[Byte])] = List.empty

  override def loadAsync(persistenceId: String, criteria: SnapshotSelectionCriteria): Future[Option[SelectedSnapshot]] = {
    Future.successful {
      snapshots.find { case (metadata, bytes) =>
        metadata.persistenceId == persistenceId && metadata.sequenceNr <= criteria.maxSequenceNr && metadata.timestamp <= criteria.maxTimestamp
      }.map { case (metadata, bytes) =>
        SelectedSnapshot(metadata, deserialize(bytes).data)
      }
    }
  }

  override def saveAsync(metadata: SnapshotMetadata, snapshot: Any): Future[Unit] = {
    Future.successful {
      val bytes = serialize(Snapshot(snapshot))
      snapshots = (metadata, bytes) :: snapshots
    }
  }

  override def saved(metadata: SnapshotMetadata): Unit = {}

  override def delete(metadata: SnapshotMetadata): Unit = {
    snapshots = snapshots.filterNot(_._1 == metadata)
  }

  override def delete(persistenceId: String, criteria: SnapshotSelectionCriteria): Unit = {
    snapshots = snapshots.filterNot { case (metadata, bytes) =>
      metadata.persistenceId == persistenceId && metadata.sequenceNr <= criteria.maxSequenceNr && metadata.timestamp <= criteria.maxTimestamp
    }
  }

  def serialize(snapshot: Snapshot): Array[Byte] = serialization.findSerializerFor(snapshot).toBinary(snapshot)

  def deserialize(bytes: Array[Byte]): Snapshot = serialization.deserialize(bytes, classOf[Snapshot]).get
}
