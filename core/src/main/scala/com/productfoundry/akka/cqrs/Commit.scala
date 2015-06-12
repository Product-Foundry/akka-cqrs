package com.productfoundry.akka.cqrs

import com.productfoundry.akka.serialization.Persistable

/**
 * A commit with aggregate event records to keep revisions per event.
 *
 * @param tag of the aggregate to which the commit was applied.
 * @param headers for the event entries.
 * @param entries in the commit.
 */
case class Commit(tag: AggregateTag, headers: AggregateEventHeaders, entries: Seq[CommitEntry]) extends Persistable {

  /**
   * @return All event records from this commit.
   */
  def records: Seq[AggregateEventRecord] = {
    entries.map { entry =>
      AggregateEventRecord(
        tag.copy(revision = entry.revision),
        headers,
        entry.event
      )
    }
  }

  /**
   * Predicts the next tag of the aggregate after this commit is applied.
   * @return next tag.
   */
  def nextTag: AggregateTag = {
    tag.copy(revision = entries.lastOption.map(_.revision).getOrElse(tag.revision))
  }
}

/**
 * Contains an event with its revision for effective storage.
 */
case class CommitEntry(revision: AggregateRevision, event: AggregateEvent)