package com.productfoundry.akka.cqrs.project.domain

import akka.actor.ActorRef
import akka.pattern.ask
import akka.util.Timeout
import com.productfoundry.akka.cqrs.project.ProjectionRevision
import com.productfoundry.akka.cqrs.project.domain.DomainAggregatorCommitter._
import com.productfoundry.akka.cqrs.{Aggregate, AggregateResponse, Commit, CommitHandler}

import scala.concurrent.Await

/**
 * Naive commit handler that sends all commits to the domain aggregator.
 */
@deprecated("use Persistence Query instead", "0.1.28")
trait DomainAggregatorCommitter extends CommitHandler {
  this: Aggregate =>

  def domainAggregatorRef: ActorRef

  def commitHandlerTimeout: Timeout

  /**
   * Handle a persisted commit.
   * @param commit to handle.
   * @param response which can be manipulated by additional commit handlers.
   * @return Updated response.
   */
  override def handleCommit(commit: Commit, response: AggregateResponse): AggregateResponse = {

    implicit val timeout = commitHandlerTimeout

    // TODO [AK] Log on error, retry
    commit.records.foldLeft(response) { case (updated, eventRecord) =>
      val projectionRevision = Await.result((domainAggregatorRef ? eventRecord).mapTo[ProjectionRevision], commitHandlerTimeout.duration)
      response.withHeaders(ProjectionRevisionKey -> String.valueOf(projectionRevision.value))
    }
  }
}

@deprecated("use Persistence Query instead", "0.1.28")
object DomainAggregatorCommitter {

  private val ProjectionRevisionKey = "ProjectionRevision"

  def projectionRevision(headers: Map[String, String]): Option[ProjectionRevision] = {
    headers.get(ProjectionRevisionKey).flatMap(ProjectionRevision.fromString)
  }
}