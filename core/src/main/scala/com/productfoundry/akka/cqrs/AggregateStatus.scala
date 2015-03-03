package com.productfoundry.akka.cqrs

object AggregateStatus {

  sealed trait AggregateStatus extends Serializable

  case class Success(result: CommitResult) extends AggregateStatus

  case class Failure(cause: AggregateError) extends AggregateStatus

}
