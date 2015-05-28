package com.productfoundry.akka.cqrs.project

import com.productfoundry.akka.cqrs._
import com.productfoundry.support.Spec

class EventProjectionSpec extends Spec {

  "Event projection" must {

    "provide access to projected commit" in new fixture {
      val commit = createCommit(1)
      CommitCollector().project(commit).commits should contain(commit)
    }

    "project a commit without events" in new fixture {
      EventCounter().project(createCommit(0)).count should be(0)
    }

    "project a commit with a single event" in new fixture {
      EventCounter().project(createCommit(1)).count should be(1)
    }

    "project a commit with multiple events" in new fixture {
      EventCounter().project(createCommit(5)).count should be(5)
    }

    "project multiple commits" in new fixture {
      val commitCount = 10
      val eventCount = 5

      val updated = (1 to commitCount).foldLeft(EventCounter()) { case (state, i) =>
        state.project(createCommit(eventCount))
      }

      updated.count should be(10 * 5)
    }
  }

  trait fixture {

    case class EventCounter(count: Int = 0) extends EventProjection[EventCounter] {
      override def project(container: Commit, event: AggregateEvent): EventCounter = {
        copy(count = count + 1)
      }
    }
    
    case class CommitCollector(commits: Vector[Commit] = Vector.empty) extends EventProjection[CommitCollector] {
      override def project(container: Commit, event: AggregateEvent): CommitCollector = {
        copy(commits = commits :+ container)
      }
    }

    object TestEvent extends AggregateEvent {
      override type Id = TestId

      override def id = TestId.generate()
    }

    var revision = AggregateRevision.Initial

    def createCommit(eventCount: Int) = {
      revision = revision.next
      Commit(CommitMetadata("", revision), 1 to eventCount map (_ => TestEvent))
    }
  }
}
