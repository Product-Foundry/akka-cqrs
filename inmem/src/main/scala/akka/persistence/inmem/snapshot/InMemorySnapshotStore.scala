package akka.persistence.inmem.snapshot

import akka.actor.{Actor, Props}
import akka.pattern.ask
import akka.persistence.inmem.snapshot.SnapshotStoreActor._
import akka.persistence.serialization.Snapshot
import akka.persistence.snapshot.SnapshotStore
import akka.persistence.{SelectedSnapshot, SnapshotMetadata, SnapshotSelectionCriteria}
import akka.serialization.SerializationExtension
import akka.util.Timeout

import scala.concurrent.Future
import scala.concurrent.duration._

class SnapshotStoreActor extends Actor {

  var storage = SnapshotStorage()

  override def receive: Receive = {

    case FindLatestByCriteria(persistenceId, criteria) =>
      sender() ! FindLatestResult(storage.findLatestByCriteria(persistenceId, criteria))

    case AddEntry(entry) =>
      storage = storage.addEntry(entry)
      sender() ! SnapshotStoreAck

    case RemoveByMetadata(metadata) =>
      storage = storage.removeByMetadata(metadata)
      sender() ! SnapshotStoreAck

    case RemoveBySelectionCriteria(persistenceId, criteria) =>
      storage = storage.removeBySelectionCriteria(persistenceId, criteria)
      sender ! SnapshotStoreAck

  }
}

object SnapshotStoreActor {

  case class SnapshotEntry(metadata: SnapshotMetadata,
                           bytes: Array[Byte])

  case class SnapshotStream(persistenceId: String,
                            entries: Vector[SnapshotEntry] = Vector.empty)

  case class SnapshotStorage(streams: Map[String, SnapshotStream] = Map.empty) {

    private def getStream(persistenceId: String): SnapshotStream = {
      streams.getOrElse(persistenceId, SnapshotStream(persistenceId))
    }

    private def updateStream(persistenceId: String)(update: (SnapshotStream) => SnapshotStream): SnapshotStorage = {
      copy(streams = streams.updated(persistenceId, update(getStream(persistenceId))))
    }

    def addEntry(entry: SnapshotEntry): SnapshotStorage = {
      updateStream(entry.metadata.persistenceId) { stream =>
        stream.copy(entries = entry +: stream.entries)
      }
    }

    def findLatestByCriteria(persistenceId: String, criteria: SnapshotSelectionCriteria): Option[SnapshotEntry] = {
      getStream(persistenceId).entries.find { entry =>
        entry.metadata.sequenceNr <= criteria.maxSequenceNr && entry.metadata.timestamp <= criteria.maxTimestamp
      }
    }

    def removeByMetadata(metadata: SnapshotMetadata): SnapshotStorage = {
      updateStream(metadata.persistenceId) { stream =>
        stream.copy(entries = stream.entries.filterNot { entry =>
          entry.metadata.sequenceNr == metadata.sequenceNr &&
            (entry.metadata.timestamp == metadata.timestamp || metadata.timestamp == 0)
        })
      }
    }

    def removeBySelectionCriteria(persistenceId: String, criteria: SnapshotSelectionCriteria): SnapshotStorage = {
      updateStream(persistenceId) { stream =>
        stream.copy(entries = stream.entries.filterNot { entry =>
          entry.metadata.sequenceNr <= criteria.maxSequenceNr && entry.metadata.timestamp <= criteria.maxTimestamp
        })
      }
    }
  }

  case class FindLatestByCriteria(persistenceId: String, criteria: SnapshotSelectionCriteria)

  case class FindLatestResult(entryOption: Option[SnapshotEntry])

  case class AddEntry(entry: SnapshotEntry)

  case class RemoveByMetadata(metadata: SnapshotMetadata)

  case class RemoveBySelectionCriteria(persistenceId: String, criteria: SnapshotSelectionCriteria)

  object SnapshotStoreAck

}

class InMemorySnapshotStore extends SnapshotStore {

  val snapshotStore = context.actorOf(Props(classOf[SnapshotStoreActor]))

  val serialization = SerializationExtension(context.system)

  implicit val executionContext = context.dispatcher

  implicit val timeout = Timeout(5.seconds)

  override def loadAsync(persistenceId: String, criteria: SnapshotSelectionCriteria): Future[Option[SelectedSnapshot]] = {
    snapshotStore ? FindLatestByCriteria(persistenceId, criteria) map {
      case FindLatestResult(entryOption) =>
        entryOption.map { entry =>
          SelectedSnapshot(entry.metadata, serialization.deserialize(entry.bytes, classOf[Snapshot]).get.data)
        }
    }
  }

  override def saveAsync(metadata: SnapshotMetadata, data: Any): Future[Unit] = {
    val snapshot = Snapshot(data)
    val entry = SnapshotEntry(metadata, serialization.findSerializerFor(snapshot).toBinary(snapshot))
    snapshotStore ? AddEntry(entry) map (_ => ())
  }

  override def deleteAsync(metadata: SnapshotMetadata): Future[Unit] = {
    snapshotStore ? RemoveByMetadata(metadata) map (_ => ())
  }

  override def deleteAsync(persistenceId: String, criteria: SnapshotSelectionCriteria): Future[Unit] = {
    snapshotStore ? RemoveBySelectionCriteria(persistenceId, criteria) map (_ => ())
  }
}
