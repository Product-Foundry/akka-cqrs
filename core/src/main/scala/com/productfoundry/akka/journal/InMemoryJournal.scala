package com.productfoundry.akka.journal

import java.util.concurrent.Callable

import akka.dispatch.Futures
import akka.persistence.journal.SyncWriteJournal
import akka.persistence.{PersistentConfirmation, PersistentId, PersistentRepr}
import akka.serialization.SerializationExtension

import scala.collection.immutable.{Seq, TreeMap}
import scala.concurrent.Future

class InMemoryJournal extends SyncWriteJournal {

  type Stream = Map[Long, Array[Byte]]

  private var journal: Map[String, Stream] = Map.empty

  private val serialization = SerializationExtension(context.system)

  private def persistentStream(persistenceId: String): Stream = {
    journal.getOrElse(persistenceId, {
      val stream = TreeMap.empty[Long, Array[Byte]]
      updateJournal(persistenceId, stream)
      stream
    })
  }

  override def asyncReplayMessages(persistenceId: String, fromSequenceNr: Long, toSequenceNr: Long, max: Long)(replayCallback: (PersistentRepr) => Unit): Future[Unit] = {
    Futures.future(
      new Callable[Unit] {
        override def call(): Unit = {
          val stream = persistentStream(persistenceId)
          val messages = stream.values.map(deserialize).filter(m => m.sequenceNr >= fromSequenceNr && m.sequenceNr <= toSequenceNr)
          messages.take(max.toInt).foreach(replayCallback)
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
          if (stream.isEmpty) {
            0L
          } else {
            stream.keys.last
          }
        }
      },
      context.dispatcher
    )
  }

  override def writeMessages(messages: Seq[PersistentRepr]): Unit = {
    messages.foreach { message =>
      updateStream(message.persistenceId, message.sequenceNr, message)
    }
  }

  override def writeConfirmations(confirmations: Seq[PersistentConfirmation]): Unit = {
    confirmations.foreach { confirmation =>
      val stream = persistentStream(confirmation.persistenceId)
      val bytesOption = stream.get(confirmation.sequenceNr)
      bytesOption.foreach { bytes =>
        val message = deserialize(bytes)
        val confirms = confirmation.channelId +: message.confirms
        val update = message.update(confirms = confirms)
        updateStream(confirmation.persistenceId, update.sequenceNr, update)
      }
    }
  }

  override def deleteMessages(messageIds: Seq[PersistentId], permanent: Boolean): Unit = {
    if (permanent) {
      messageIds.foreach(messageId => updateJournal(messageId.processorId, persistentStream(messageId.processorId) - messageId.sequenceNr))
    } else {
      messageIds.foreach { messageId =>
        val stream = persistentStream(messageId.processorId)
        val bytesOption = stream.get(messageId.sequenceNr)
        bytesOption.foreach { bytes =>
          val message = deserialize(bytes)
          val update = message.update(deleted = true)
          updateStream(messageId.processorId, update.sequenceNr, update)
        }
      }
    }
  }

  override def deleteMessagesTo(persistenceId: String, toSequenceNr: Long, permanent: Boolean): Unit = {
    if (permanent) {
      updateJournal(persistenceId, persistentStream(persistenceId).filterKeys(_ > toSequenceNr))
    } else {
      persistentStream(persistenceId).filterKeys(_ <= toSequenceNr).values.foreach { bytes =>
        val message = deserialize(bytes)
        val update = message.update(deleted = true)
        updateStream(persistenceId, update.sequenceNr, update)
      }
    }
  }

  private def updateStream(processorId: String, sequenceNr: Long, message: PersistentRepr): Unit = {
    updateJournal(processorId, persistentStream(processorId).updated(sequenceNr, serialize(message)))
  }

  private def updateJournal(processorId: String, stream: Stream): Unit = {
    journal = journal.updated(processorId, stream)
  }

  override def postStop(): Unit = {
    journal = Map.empty
  }

  private def serialize(repr: PersistentRepr): Array[Byte] = serialization.serialize(repr).get

  private def deserialize(bytes: Array[Byte]): PersistentRepr = serialization.deserialize(bytes, classOf[PersistentRepr]).get
}
