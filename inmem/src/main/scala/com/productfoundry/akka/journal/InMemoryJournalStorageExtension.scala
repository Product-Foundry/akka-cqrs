package com.productfoundry.akka.journal

import akka.actor.{ExtendedActorSystem, Extension, ExtensionId, ExtensionIdProvider}

import scala.concurrent.stm._

/**
  * Journal entry.
  * @param persistenceId of the persistent actor.
  * @param sequenceNr of the persisted entry.
  * @param bytes of the serialized persistent.
  * @param tags associated with the persistent.
  */
case class JournalEntry(persistenceId: String,
                        sequenceNr: Long,
                        bytes: Array[Byte],
                        tags: Set[String] = Set.empty)

/**
  * Collection of entries by persistence id.
  * @param persistenceId of all persistent actors in the stream.
  * @param highestSequenceNr stored separately to properly keep up to date sequenceNr after deletion.
  * @param entries with the specified persistence id.
  */
case class JournalStream(persistenceId: String,
                         highestSequenceNr: Long = 0L,
                         entries: Vector[JournalEntry] = Vector.empty)

/**
  * In memory journal.
  * @param streams mapping persistence Ids to a stream with all persisted entries.
  */
case class JournalStorage(streams: Map[String, JournalStream] = Map.empty) {

  def addEntry(entry: JournalEntry): JournalStorage = {
    val stream = getStream(entry.persistenceId)
    val updated = stream.copy(
      highestSequenceNr = Math.max(stream.highestSequenceNr, entry.sequenceNr),
      entries = stream.entries :+ entry
    )
    copy(streams = streams.updated(entry.persistenceId, updated))
  }

  def removeEntries(persistenceId: String, toSequenceNr: Long): JournalStorage = {
    val stream = getStream(persistenceId)
    val updated = stream.copy(
      entries = stream.entries.filter(_.sequenceNr > toSequenceNr)
    )
    copy(streams = streams.updated(persistenceId, updated))
  }

  def getStream(persistenceId: String): JournalStream = {
    streams.getOrElse(persistenceId, JournalStream(persistenceId))
  }
}

/**
  * Definition of the Akka extension.
  */
trait InMemoryJournalStorage extends Extension {

  /**
    * Get the highest sequence nr for the specified persistence id.
    * @param persistenceId to get highest sequence nr.
    * @return sequence nr or 0 when unknown.
    */
  def highestSequenceNr(persistenceId: String): Long

  /**
    * Find all entries matching the specified criteria.
    * @param persistenceId persistent actor id.
    * @param fromSequenceNr sequence number where replay should start (inclusive).
    * @param toSequenceNr sequence number where replay should end (inclusive).
    * @param max maximum number of messages to be replayed.
    * @return
    */
  def findByPersistenceId(persistenceId: String, fromSequenceNr: Long, toSequenceNr: Long, max: Long): Iterable[JournalEntry]

  /**
    * Add all entries to the journal, maintaining order.
    * @param entries to add.
    */
  def addEntries(entries: Iterable[JournalEntry]): Unit

  /**
    * Remove specified entries from the journal
    * @param persistenceId persistent actor id.
    * @param toSequenceNr sequence number to delete all persistent messages to (inclusive).
    */
  def removeEntries(persistenceId: String, toSequenceNr: Long): Unit

  /**
    * Clears the journal.
    */
  def clear(): Unit
}

private class DefaultInMemoryJournalStorage(val system: ExtendedActorSystem) extends InMemoryJournalStorage {

  val journalStorageRef: Ref[JournalStorage] = Ref(JournalStorage())

  def highestSequenceNr(persistenceId: String): Long = {
    atomic { implicit txn =>
      journalStorageRef().getStream(persistenceId).highestSequenceNr
    }
  }

  def findByPersistenceId(persistenceId: String, fromSequenceNr: Long, toSequenceNr: Long, max: Long): Iterable[JournalEntry] = {
    atomic { implicit txn =>
      val stream = journalStorageRef().getStream(persistenceId)
      val entries = stream.entries.filter(entry => entry.sequenceNr >= fromSequenceNr && entry.sequenceNr <= toSequenceNr)
      if (max < Int.MaxValue.toLong) entries.take(max.toInt) else entries
    }
  }

  override def addEntries(entries: Iterable[JournalEntry]): Unit = {
    atomic { implicit txn =>
      journalStorageRef.transform(journal => entries.foldLeft(journal)(_ addEntry _))
    }
  }

  override def removeEntries(persistenceId: String, toSequenceNr: Long): Unit = {
    atomic { implicit txn =>
      journalStorageRef.transform(_.removeEntries(persistenceId, toSequenceNr))
    }
  }

  override def clear(): Unit = {
    atomic { implicit txn =>
      journalStorageRef() = JournalStorage()
    }
  }
}

/**
  * Makes the in memory store available as a singleton throughout the system
  */
object InMemoryJournalStorageExtension extends ExtensionId[InMemoryJournalStorage] with ExtensionIdProvider {

  override def lookup(): ExtensionId[InMemoryJournalStorage] = InMemoryJournalStorageExtension

  override def createExtension(system: ExtendedActorSystem): InMemoryJournalStorage = {
    new DefaultInMemoryJournalStorage(system)
  }
}
