package com.productfoundry.akka.cqrs

/**
 * Revision check failure.
 *
 * @param expected revision.
 * @param actual revision.
 * @param recordsOption optional records from expected until the actual revision.
 */
case class RevisionConflict(expected: AggregateRevision,
                            actual: AggregateRevision,
                            recordsOption: Option[Seq[AggregateEventRecord]] = None) extends DomainError {

  /**
   * Creates a new revision conflict with the conflicting records attached.
   * @param records to add to the conflict.
   * @return new revision conflict.
   */
  def withRecords(records: Seq[AggregateEventRecord]): RevisionConflict = {
    copy(recordsOption = Some(records))
  }
}