package com.productfoundry.akka.cqrs

/**
 * A commit with aggregate event records to keep revisions per event.
 *
 * @param headers for the event entries.
 * @param entries in the commit.
 */
case class Commit(headers: AggregateEventHeaders, entries: Seq[CommitEntry]) extends Persistable {

  /**
   * @return All event records from this commit.
   */
  def records: Seq[AggregateEventRecord] = {
    entries.map { entry =>
      AggregateEventRecord(
        headers.copy(tag = headers.tag.copy(revision = entry.revision)),
        entry.event
      )
    }
  }
}

/**
 * Contains an event with its revision for effective storage.
 */
case class CommitEntry(revision: AggregateRevision, event: AggregateEvent)