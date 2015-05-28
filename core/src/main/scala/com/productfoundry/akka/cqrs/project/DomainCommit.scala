package com.productfoundry.akka.cqrs.project

import com.productfoundry.akka.cqrs.{Commit, Persistable}

case class DomainCommit(revision: DomainRevision,
                        timestamp: Long,
                        commit: Commit) extends Persistable

