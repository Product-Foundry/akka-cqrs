package com.productfoundry.akka.cqrs

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
}

/**
 * Contains an event with its revision for effective storage.
 */
case class CommitEntry(revision: AggregateRevision, event: AggregateEvent)