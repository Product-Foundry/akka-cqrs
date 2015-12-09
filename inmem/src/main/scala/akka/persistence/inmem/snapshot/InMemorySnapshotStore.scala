package akka.persistence.inmem.snapshot

import akka.persistence.serialization.Snapshot
import akka.persistence.snapshot.SnapshotStore
import akka.persistence.{SelectedSnapshot, SnapshotMetadata, SnapshotSelectionCriteria}
import akka.serialization.SerializationExtension

import scala.concurrent.Future

class InMemorySnapshotStore extends SnapshotStore {

  val storage = InMemorySnapshotStorageExtension(context.system)

  val serialization = SerializationExtension(context.system)

  implicit val executionContext = context.dispatcher

  override def loadAsync(persistenceId: String, criteria: SnapshotSelectionCriteria): Future[Option[SelectedSnapshot]] = {
    Future {
      storage.findLatestByCriteria(persistenceId, criteria).map { case entry =>
        SelectedSnapshot(entry.metadata, serialization.deserialize(entry.bytes, classOf[Snapshot]).get.data)
      }
    }
  }

  override def saveAsync(metadata: SnapshotMetadata, data: Any): Future[Unit] = {
    Future {
      val snapshot = Snapshot(data)
      storage.addEntry(SnapshotEntry(metadata, serialization.findSerializerFor(snapshot).toBinary(snapshot)))
    }
  }

  override def deleteAsync(metadata: SnapshotMetadata): Future[Unit] = {
    Future {
      storage.removeByMetadata(metadata)
    }
  }

  override def deleteAsync(persistenceId: String, criteria: SnapshotSelectionCriteria): Future[Unit] = {
    Future {
      storage.removeBySelectionCriteria(persistenceId, criteria)
    }
  }

  override def postStop(): Unit = {
    storage.clear()
  }
}
