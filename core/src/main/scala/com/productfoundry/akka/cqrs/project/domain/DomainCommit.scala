package com.productfoundry.akka.cqrs.project.domain

import com.productfoundry.akka.cqrs.{Commit, Persistable}

/**
 * A successful aggregated commit.
 *
 * @param revision of the domain aggregator.
 * @param commit that was aggregated.
 */
case class DomainCommit(revision: DomainRevision, commit: Commit) extends Persistable