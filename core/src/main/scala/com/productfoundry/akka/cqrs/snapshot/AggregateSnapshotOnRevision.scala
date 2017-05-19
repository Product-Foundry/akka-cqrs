package com.productfoundry.akka.cqrs.snapshot

import akka.persistence.PersistentActor
import com.productfoundry.akka.cqrs._

import scala.concurrent.duration._

trait AggregateSnapshotOnRevisionInterval
  extends PersistentActor
    with RuleBasedSnapshotRecovery
    with CommitHandler {

  this: Aggregate with AggregateSnapshotRecovery =>

  def snapshotInterval: Int = AggregateSnapshotOnRevisionInterval.defaultInterval

  def snapshotDelay: FiniteDuration = AggregateSnapshotOnRevisionInterval.defaultDelay

  override abstract def handleCommit(commit: Commit, response: AggregateResponse): AggregateResponse = {

    // Check if we need to create a snapshot
    if (revision.value - lastSnapshotRevision.value >= snapshotInterval) {
      self ! SnapshotProtocol.RequestSnapshot(snapshotDelay)
    }

    // Invoke other handlers
    super.handleCommit(commit, response)
  }
}

object AggregateSnapshotOnRevisionInterval {

  val defaultInterval: Int = 1000

  val defaultDelay: FiniteDuration = 10.seconds

}