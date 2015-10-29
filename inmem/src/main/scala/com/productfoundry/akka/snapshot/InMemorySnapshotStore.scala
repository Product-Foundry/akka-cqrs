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

    def deserialize(bytes: Array[Byte]): Snapshot = serialization.deserialize(bytes, classOf[Snapshot]).get

    Future.successful {
      snapshots.find { case (metadata, bytes) =>
        metadata.persistenceId == persistenceId && metadata.sequenceNr <= criteria.maxSequenceNr && metadata.timestamp <= criteria.maxTimestamp
      }.map { case (metadata, bytes) =>
        SelectedSnapshot(metadata, deserialize(bytes).data)
      }
    }
  }

  override def saveAsync(metadata: SnapshotMetadata, snapshot: Any): Future[Unit] = {

    def serialize(snapshot: Snapshot): Array[Byte] = serialization.findSerializerFor(snapshot).toBinary(snapshot)

    Future.successful {
      val bytes = serialize(Snapshot(snapshot))
      snapshots = (metadata, bytes) :: snapshots
    }
  }

  override def deleteAsync(metadata: SnapshotMetadata): Future[Unit] = {
    Future.successful {
      snapshots = snapshots.filterNot {
        case (md, bytes) => md.persistenceId == metadata.persistenceId &&
          md.sequenceNr == metadata.sequenceNr &&
          (md.timestamp == metadata.timestamp || metadata.timestamp == 0)
      }
    }
  }

  override def deleteAsync(persistenceId: String, criteria: SnapshotSelectionCriteria): Future[Unit] = {
    Future.successful {
      snapshots = snapshots.filterNot { case (metadata, bytes) =>
        metadata.persistenceId == persistenceId && metadata.sequenceNr <= criteria.maxSequenceNr && metadata.timestamp <= criteria.maxTimestamp
      }
    }
  }
}
