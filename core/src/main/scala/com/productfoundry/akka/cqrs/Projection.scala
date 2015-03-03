package com.productfoundry.akka.cqrs

/**
 * Creates a projection from persisted commits.
 *
 * Projections are used to generate read models, also known as views or query models.
 */
trait Projection extends Function[Commit[DomainEvent], Unit] {

  /**
   * Returns the current projected revision for the specified entity.
   *
   * @param id to get current revision for.
   * @return revision for the specified entity.
   */
  def currentRevision(id: EntityId): Option[AggregateRevision] = None

  /**
   * Indicates whether the specified commit is applied to this projection or not.
   * @param commit to check.
   * @return True id the commit is applied, otherwise False.
   */
  def isApplied(commit: Commit[DomainEvent]): Boolean = {
    currentRevision(commit.id).exists(_ >= commit.revision)
  }

  /**
   * Applies a commit to the projection.
   *
   * Projection is idempotent; already projected commits are not reapplied.
   * @param commit to apply.
   */
  override def apply(commit: Commit[DomainEvent]): Unit = {
    if (!isApplied(commit)) {
      apply(commit.revision, commit.events)
    }
  }

  /**
   * Applies all events to the projection.
   * @param revision of the entity after the event was applied.
   * @param events to apply.
   */
  def apply(revision: AggregateRevision, events: Seq[DomainEvent]): Unit
}
