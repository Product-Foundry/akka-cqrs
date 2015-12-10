package akka.persistence.inmem.journal

import akka.actor.{Props, Actor}
import akka.persistence.inmem.journal.JournalActor._
import akka.persistence.journal.{AsyncWriteJournal, Tagged}
import akka.persistence.{AtomicWrite, PersistentRepr}
import akka.pattern.ask
import akka.serialization.SerializationExtension
import akka.util.Timeout

import scala.collection.immutable.Seq
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.{Success, Try}

class JournalActor extends Actor {

  var storage = JournalStorage()

  override def receive: Receive = {

    case GetHighestSequenceNr(persistenceId) =>
      sender() ! HighestSequenceNrResponse(storage.getStream(persistenceId).highestSequenceNr)

    case FindByPersistenceId(persistenceId, fromSequenceNr, toSequenceNr, max) =>
      val stream = storage.getStream(persistenceId)
      val entries = stream.entries.filter(entry => entry.sequenceNr >= fromSequenceNr && entry.sequenceNr <= toSequenceNr)
      val result = if (max < Int.MaxValue.toLong) entries.take(max.toInt) else entries
      sender() ! JournalEntries(result)

    case AddEntries(entries) =>
      storage = entries.foldLeft(storage)(_ addEntry _)
      sender() ! JournalAck

    case RemoveEntries(persistenceId, toSequenceNr) =>
      storage = storage.removeEntries(persistenceId, toSequenceNr)
      sender() ! JournalAck
  }
}

object JournalActor {

  case class JournalEntry(persistenceId: String,
                          sequenceNr: Long,
                          bytes: Array[Byte],
                          tags: Set[String] = Set.empty)

  case class JournalStream(persistenceId: String,
                           highestSequenceNr: Long = 0L,
                           entries: Vector[JournalEntry] = Vector.empty)

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

  case class GetHighestSequenceNr(persistenceId: String)

  case class HighestSequenceNrResponse(highestSequenceNr: Long)

  case class FindByPersistenceId(persistenceId: String, fromSequenceNr: Long, toSequenceNr: Long, max: Long)

  case class JournalEntries(entries: Iterable[JournalEntry])

  case class AddEntries(entries: Iterable[JournalEntry])

  case class RemoveEntries(persistenceId: String, toSequenceNr: Long)

  case object JournalAck

}

class InMemoryJournal extends AsyncWriteJournal {

  val journal = context.actorOf(Props(classOf[JournalActor]))

  val serialization = SerializationExtension(context.system)

  implicit val executionContext = context.dispatcher

  implicit val timeout = Timeout(5.seconds)

  def deserialize(entry: JournalEntry): PersistentRepr = {
    serialization
      .deserialize(entry.bytes, classOf[PersistentRepr])
      .getOrElse(throw new IllegalStateException(s"Unable to deserialize entry: $entry"))
  }

  override def asyncReplayMessages(persistenceId: String, fromSequenceNr: Long, toSequenceNr: Long, max: Long)(replayCallback: (PersistentRepr) => Unit): Future[Unit] = {
    journal ? FindByPersistenceId(persistenceId, fromSequenceNr, toSequenceNr, max) map {
      case JournalEntries(entries) =>
        entries.map(deserialize).foreach(replayCallback)
    }
  }

  override def asyncReadHighestSequenceNr(persistenceId: String, fromSequenceNr: Long): Future[Long] = {
    journal ? GetHighestSequenceNr(persistenceId) map {
      case HighestSequenceNrResponse(highestSequenceNr) => highestSequenceNr
    }
  }

  override def asyncWriteMessages(messages: Seq[AtomicWrite]): Future[Seq[Try[Unit]]] = {
    val serialized = messages.map { atomicWrite =>
      val entryAttempts = atomicWrite.payload.map { persistentRepr =>
        val (persistent, tags) = persistentRepr.payload match {
          case Tagged(_payload, _tags) =>
            (persistentRepr.withPayload(_payload), _tags)

          case _ =>
            (persistentRepr, Set.empty[String])
        }

        serialization.serialize(persistent).map { bytes =>
          JournalEntry(
            persistent.persistenceId,
            persistent.sequenceNr,
            bytes,
            tags
          )
        }
      }

      entryAttempts.foldLeft(Try(Vector.empty[JournalEntry])) { case (acc, entryAttempt) =>
        acc.flatMap(entries => entryAttempt.map(entry => entries :+ entry))
      }
    }

    val batchResults = serialized.collect {
      case Success(entries) => journal ? AddEntries(entries)
    }

    Future.sequence(batchResults).map(_ => serialized.map(a => a.map(_ => ())))
  }

  override def asyncDeleteMessagesTo(persistenceId: String, toSequenceNr: Long): Future[Unit] = {
    journal ? RemoveEntries(persistenceId, toSequenceNr) map (_ => ())
  }
}

object InMemoryJournal {

  final val Identifier = "in-memory-journal"
}
