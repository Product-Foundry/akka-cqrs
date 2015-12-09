package com.productfoundry.akka.snapshot

import akka.actor.{ExtendedActorSystem, Extension, ExtensionId, ExtensionIdProvider}
import akka.persistence.{SnapshotMetadata, SnapshotSelectionCriteria}

import scala.concurrent.stm._

/**
  * Snapshot store entry.
  * @param metadata of the snapshot.
  * @param bytes with the serialized snapshot.
  */
case class SnapshotEntry(metadata: SnapshotMetadata,
                         bytes: Array[Byte])

/**
  * Stream of all snapshots for a persistent actor.
  * @param persistenceId of the persistent actor.
  * @param entries in the snapshot store.
  */
case class SnapshotStream(persistenceId: String,
                          entries: Vector[SnapshotEntry] = Vector.empty)

/**
  * The snapshot store.
  * @param streams contains all entries grouped by persistence id.
  */
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

/**
  * Definition of the Akka extension.
  */
trait InMemorySnapshotStorage extends Extension {

  def findLatestByCriteria(persistenceId: String, criteria: SnapshotSelectionCriteria): Option[SnapshotEntry]

  def addEntry(entry: SnapshotEntry): Unit

  def removeByMetadata(metadata: SnapshotMetadata): Unit

  def removeBySelectionCriteria(persistenceId: String, criteria: SnapshotSelectionCriteria): Unit

  /**
    * Clears the snapshot store.
    */
  def clear(): Unit

}

private class DefaultInMemorySnapshotStorage(val system: ExtendedActorSystem) extends InMemorySnapshotStorage {

  val snapshotStorageRef: Ref[SnapshotStorage] = Ref(SnapshotStorage())

  override def findLatestByCriteria(persistenceId: String, criteria: SnapshotSelectionCriteria): Option[SnapshotEntry] = {
    atomic { implicit txn =>
      snapshotStorageRef().findLatestByCriteria(persistenceId, criteria)
    }
  }

  override def addEntry(entry: SnapshotEntry): Unit = {
    atomic { implicit txn =>
      snapshotStorageRef.transform(_.addEntry(entry))
    }
  }

  override def removeByMetadata(metadata: SnapshotMetadata): Unit = {
    atomic { implicit txn =>
      snapshotStorageRef.transform(_.removeByMetadata(metadata))
    }
  }

  override def removeBySelectionCriteria(persistenceId: String, criteria: SnapshotSelectionCriteria): Unit = {
    atomic { implicit txn =>
      snapshotStorageRef.transform(_.removeBySelectionCriteria(persistenceId, criteria))
    }
  }

  override def clear(): Unit = {
    atomic { implicit txn =>
      snapshotStorageRef() = SnapshotStorage()
    }
  }
}

/**
  * Makes the in memory store available as a singleton throughout the system
  */
object InMemorySnapshotStorageExtension extends ExtensionId[InMemorySnapshotStorage] with ExtensionIdProvider {

  override def lookup(): ExtensionId[InMemorySnapshotStorage] = InMemorySnapshotStorageExtension

  override def createExtension(system: ExtendedActorSystem): InMemorySnapshotStorage = {
    new DefaultInMemorySnapshotStorage(system)
  }
}
