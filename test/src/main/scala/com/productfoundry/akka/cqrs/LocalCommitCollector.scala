package com.productfoundry.akka.cqrs

import akka.actor.{Actor, ActorLogging, ActorSystem, Props}
import akka.event.Logging
import akka.pattern.ask
import akka.util.Timeout

import scala.concurrent.{Await, ExecutionContext}

/**
  * Collects all commits published on the system event stream.
  *
  * Can be used to check which commits are persisted by aggregates under test.
  *
  * @param system test actor system.
  */
case class LocalCommitCollector(actorName: String = "CommitCollector")(implicit system: ActorSystem) {

  /**
    * Actor to handle messages from the system event stream and collect commits.
    */
  class CollectorActor extends Actor with ActorLogging {

    override def receive: Receive = receiveCommits(Vector.empty)

    def receiveCommits(commits: Vector[Commit]): Receive = {
      case commit: Commit =>
        context.become(receiveCommits(commits :+ commit))

      case DumpCommits =>
        dumpCommits(commits, Logging.ErrorLevel)

      case GetCommitsRequest =>
        sender ! GetCommitsResponse(commits)

      case message =>
        log.error("Unexpected: {}", message)
    }

    /**
      * Subscribe to the system event stream.
      */
    override def preStart(): Unit = {
      system.eventStream.subscribe(self, classOf[Commit])
      super.preStart()
    }

    /**
      * Unsubscribe from the event stream.
      */
    override def postStop(): Unit = {
      system.eventStream.unsubscribe(self)
      super.postStop()
    }

    /**
      * Log all collected commits.
      *
      * @param level the logging level.
      */
    def dumpCommits(commits: Vector[Commit], level: Logging.LogLevel): Unit = {
      if (log.isEnabled(level)) {
        val commitsWithIndex = commits.zipWithIndex
        val commitLines = commitsWithIndex.map { case (commit, i) => s"  ${i + 1}. $commit\n" }
        log.log(level, s"${commitLines.size} Commits collected\n\n${commitLines.mkString}\n")
      }
    }
  }

  case object DumpCommits

  case object GetCommitsRequest

  case class GetCommitsResponse(commits: Vector[Commit])

  /**
    * Reference to the commit collector.
    */
  val ref = system.actorOf(Props(new CollectorActor), actorName)

  /**
    * Tells the commit collector to dump all commits.
    */
  def dumpCommits(): Unit = {
    ref ! DumpCommits
  }

  def eventRecords(implicit ec: ExecutionContext, timeout: Timeout): Vector[AggregateEventRecord] = {
    val res = ref ? GetCommitsRequest collect {
      case GetCommitsResponse(commits) => commits.flatMap(_.records)
    }

    Await.result(res, timeout.duration)
  }

  /**
    * @return a view of all the committed events extracted from the commits.
    */
  def events(implicit ec: ExecutionContext, timeout: Timeout): Vector[AggregateEvent] = {
    eventRecords.map(_.event)
  }
}
