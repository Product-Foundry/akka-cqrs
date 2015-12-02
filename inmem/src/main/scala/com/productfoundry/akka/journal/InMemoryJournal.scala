package com.productfoundry.akka.journal

import java.util.concurrent.Callable

import akka.dispatch.Futures
import akka.persistence.journal.AsyncWriteJournal
import akka.persistence.{AtomicWrite, PersistentRepr}
import akka.serialization.SerializationExtension

import scala.collection.immutable.{Seq, TreeMap}
import scala.concurrent.Future
import scala.util.Try


class InMemoryJournal extends AsyncWriteJournal {

  type Stream = Map[Long, Array[Byte]]


  private var journal: Map[String, PersistentStream] = Map.empty

  private val serialization = SerializationExtension(context.system)

  case class PersistentStream(highestSequenceNr: Long = 0L, stream: Stream = TreeMap.empty[Long, Array[Byte]]) {

    def values = stream.values
  }

  private def persistentStream(persistenceId: String): PersistentStream = {
    journal.getOrElse(persistenceId, {
      val stream = PersistentStream()
      journal = journal.updated(persistenceId, stream)
      stream
    })
  }

  override def asyncReplayMessages(persistenceId: String, fromSequenceNr: Long, toSequenceNr: Long, max: Long)(replayCallback: (PersistentRepr) => Unit): Future[Unit] = {

    def deserialize(bytes: Array[Byte]): PersistentRepr = serialization.deserialize(bytes, classOf[PersistentRepr]).get

    Futures.future(
      new Callable[Unit] {
        override def call(): Unit = {
          val stream = persistentStream(persistenceId)
          val messages = stream.values.map(deserialize).filter(m => m.sequenceNr >= fromSequenceNr && m.sequenceNr <= toSequenceNr)
          val maxInt = max.toInt
          val limited = if (maxInt >= 0) messages.take(maxInt) else messages
          limited.foreach(replayCallback)
        }
      },
      context.dispatcher
    )
  }

  override def asyncReadHighestSequenceNr(persistenceId: String, fromSequenceNr: Long): Future[Long] = {
    Futures.future(
      new Callable[Long] {
        override def call(): Long = {
          val stream = persistentStream(persistenceId)
          stream.highestSequenceNr
        }
      },
      context.dispatcher
    )
  }

  override def asyncWriteMessages(messages: Seq[AtomicWrite]): Future[Seq[Try[Unit]]] = {
    val results = messages.map { atomicWrite =>
      val persistenceId = atomicWrite.persistenceId
      val stream = persistentStream(persistenceId)

      val maybeUpdatedStream = atomicWrite.payload.foldLeft(Try(stream)) {
        case (acc, persistent) =>
          serialization.serialize(persistent).flatMap { bytes =>
            acc.map { persistentStream =>
              persistentStream.copy(
                highestSequenceNr = persistent.sequenceNr,
                stream = persistentStream.stream.updated(persistent.sequenceNr, bytes))
            }
          }
      }

      maybeUpdatedStream.map[Unit](updatedStream => journal = journal.updated(persistenceId, updatedStream))
    }

    Future.successful(results)
  }

  override def asyncDeleteMessagesTo(persistenceId: String, toSequenceNr: Long): Future[Unit] = {
    Future.fromTry[Unit] {
      Try {
        val stream = persistentStream(persistenceId)
        val updated = stream.copy(stream = stream.stream.filterKeys(_ > toSequenceNr))
        journal = journal.updated(persistenceId, updated)
      }
    }
  }

  override def postStop(): Unit = {
    journal = Map.empty
  }
}
