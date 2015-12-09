package com.productfoundry.akka.journal

import akka.actor.{ExtendedActorSystem, Extension, ExtensionId, ExtensionIdProvider}

import scala.concurrent.stm._

case class Entry(persistenceId: String,
                 sequenceNr: Long,
                 bytes: Array[Byte],
                 tags: Set[String] = Set.empty)

case class Stream(persistenceId: String,
                  highestSequenceNr: Long = 0L,
                  entries: Vector[Entry] = Vector.empty)

case class Journal(streams: Map[String, Stream] = Map.empty) {

  def addEntry(entry: Entry): Journal = {
    val stream = getStream(entry.persistenceId)
    val updated = stream.copy(
      highestSequenceNr = Math.max(stream.highestSequenceNr, entry.sequenceNr),
      entries = stream.entries :+ entry
    )
    copy(streams = streams.updated(entry.persistenceId, updated))
  }

  def removeEntries(persistenceId: String, toSequenceNr: Long): Journal = {
    val stream = getStream(persistenceId)
    val updated = stream.copy(
     entries = stream.entries.filter(_.sequenceNr > toSequenceNr)
    )
    copy(streams = streams.updated(persistenceId, updated))
  }

  def getStream(persistenceId: String): Stream = {
    streams.getOrElse(persistenceId, Stream(persistenceId))
  }
}

trait InMemoryStore extends Extension {

  def highestSequenceNr(persistenceId: String): Long

  def findByPersistenceId(persistenceId: String, fromSequenceNr: Long, toSequenceNr: Long, max: Long): Iterable[Entry]

  def addEntries(entries: Iterable[Entry]): Unit

  def removeEntries(persistenceId: String, toSequenceNr: Long): Unit

  def clear(): Unit
}

private class DefaultInMemoryStore(val system: ExtendedActorSystem) extends InMemoryStore {

  val journalRef: Ref[Journal] = Ref(Journal())

  def highestSequenceNr(persistenceId: String): Long = {
    atomic { implicit txn =>
      journalRef().getStream(persistenceId).highestSequenceNr
    }
  }

  def findByPersistenceId(persistenceId: String, fromSequenceNr: Long, toSequenceNr: Long, max: Long): Iterable[Entry] = {
    atomic { implicit txn =>
      val stream = journalRef().getStream(persistenceId)
      val entries = stream.entries.filter(entry => entry.sequenceNr >= fromSequenceNr && entry.sequenceNr <= toSequenceNr)
      if (max < Int.MaxValue.toLong) entries.take(max.toInt) else entries
    }
  }

  override def addEntries(entries: Iterable[Entry]): Unit = {
    atomic { implicit txn =>
      journalRef.transform(journal => entries.foldLeft(journal)(_ addEntry _))
    }
  }

  override def removeEntries(persistenceId: String, toSequenceNr: Long): Unit = {
    atomic { implicit txn =>
      journalRef.transform(_.removeEntries(persistenceId, toSequenceNr))
    }
  }

  override def clear(): Unit = {
    atomic { implicit txn =>
      journalRef() = Journal()
    }
  }
}

/**
  * Makes the in memory store available as a singleton throughout the system
  */
object InMemoryStoreExtension extends ExtensionId[InMemoryStore] with ExtensionIdProvider {

  override def lookup(): ExtensionId[InMemoryStore] = InMemoryStoreExtension

  override def createExtension(system: ExtendedActorSystem): InMemoryStore = {
    new DefaultInMemoryStore(system)
  }
}
