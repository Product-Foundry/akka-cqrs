package com.productfoundry.akka.cqrs.project.domain

import com.productfoundry.akka.cqrs.{Persistable, AggregateEvent, Commit, AggregateEventContainer}

/**
 * A successful aggregated commit.
 *
 * @param revision of the domain aggregator.
 * @param commit that was aggregated.
 */
case class DomainCommit(revision: DomainRevision, commit: Commit) extends Persistable with AggregateEventContainer {

  /**
   * @return unique id of the container.
   */
  override def id: String = commit.id

  /**
   * @return events persisted in the commit.
   */
  override def events: Seq[AggregateEvent] = commit.events
}
