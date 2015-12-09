package com.productfoundry.akka.journal

import akka.persistence.journal.{AsyncWriteJournal, Tagged}
import akka.persistence.{AtomicWrite, PersistentRepr}
import akka.serialization.SerializationExtension

import scala.collection.immutable.Seq
import scala.concurrent.Future
import scala.util.Try

class InMemoryJournal extends AsyncWriteJournal {

  val store = InMemoryStoreExtension(context.system)

  val serialization = SerializationExtension(context.system)

  implicit val executionContext = context.dispatcher

  override def asyncReplayMessages(persistenceId: String, fromSequenceNr: Long, toSequenceNr: Long, max: Long)(replayCallback: (PersistentRepr) => Unit): Future[Unit] = {
    Future {
      store.findByPersistenceId(persistenceId, fromSequenceNr, toSequenceNr, max)
        .map(entry => serialization.deserialize(entry.bytes, classOf[PersistentRepr]).get)
        .foreach(replayCallback)
    }
  }

  override def asyncReadHighestSequenceNr(persistenceId: String, fromSequenceNr: Long): Future[Long] = {
    Future {
      store.highestSequenceNr(persistenceId)
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
          Entry(
            persistent.persistenceId,
            persistent.sequenceNr,
            bytes,
            tags
          )
        }
      }

      entryAttempts.foldLeft(Try(Vector.empty[Entry])) { case (acc, entryAttempt) =>
          acc.flatMap(entries => entryAttempt.map(entry => entries :+ entry))
      }
    }

    Future {
      serialized.map(_.map(store.addEntries))
    }
  }

  override def asyncDeleteMessagesTo(persistenceId: String, toSequenceNr: Long): Future[Unit] = {
    Future {
      store.removeEntries(persistenceId, toSequenceNr)
    }
  }

  override def postStop(): Unit = {
    store.clear()
  }
}
