package com.productfoundry.akka.cqrs

import akka.actor.{Actor, ActorLogging, ActorSystem, Props}
import akka.event.Logging

import scala.concurrent.stm._

/**
 * Collects all commits published on the system event stream.
 *
 * Used to check which commits are persisted by aggregates under test.
 * @param system test actor system.
 */
case class LocalCommitCollector(implicit system: ActorSystem) {

  /**
   * Actor to handle messages from the system event stream and collect commits.
   */
  class CollectorActor extends Actor with ActorLogging {
    override def receive: Receive = {
      case commit: Commit[AggregateEvent] =>
        atomic { implicit txn =>
          commits.transform(_ :+ commit)
        }

      case DumpCommits =>
        dumpCommits(Logging.ErrorLevel)

      case c =>
        log.error(s"Unexpected message: $c")
    }

    /**
     * Subscribe to the system event stream.
     */
    override def preStart(): Unit = {
      system.eventStream.subscribe(self, classOf[Commit[AggregateEvent]])
      super.preStart()
    }

    /**
     * Unsubscribe from the event stream and dump all commits on Debug level.
     */
    override def postStop(): Unit = {
      system.eventStream.unsubscribe(self)
      dumpCommits(Logging.DebugLevel)
      super.postStop()
    }

    /**
     * Log all collected commits.
     * @param level the logging level.
     */
    def dumpCommits(level: Logging.LogLevel): Unit = {
      if (log.isEnabled(level)) {
        val commitsWithIndex = commits.single.get.zipWithIndex
        val commitLines = commitsWithIndex.map { case (commit, i) => s"  ${i + 1}. $commit\n"}
        log.log(level, s"${commitLines.size} Commits collected\n\n${commitLines.mkString}\n")
      }
    }
  }

  case object DumpCommits

  /**
   * Reference to the commit collector.
   */
  val ref = system.actorOf(Props(new CollectorActor), "CommitCollector")

  /**
   * All collected commits.
   */
  val commits: Ref[Vector[Commit[AggregateEvent]]] = Ref(Vector.empty)

  /**
   * Tells the commit collector to dump all commits.
   */
  def dumpCommits(): Unit = {
    ref ! DumpCommits
  }

  /**
   * @return a view of all the committed events extracted from the commits.
   */
  def events: Vector[AggregateEvent] = {
    commits.single.get.map { commit =>
      commit.events
    }.flatten
  }
}
