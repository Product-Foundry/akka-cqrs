package com.productfoundry.akka.cqrs

/**
 * Defines the possible statuses of executing an aggregate command.
 */
object AggregateStatus {

  sealed trait AggregateStatus extends Serializable

  /**
   * Indicates a successful update to the aggregate.
   * @param response of the aggregate.
   */
  case class Success(response: AggregateResponse) extends AggregateStatus

  /**
   * Indicates an update failure that can be corrected by the user.
   * @param cause of the failure
   */
  case class Failure(cause: DomainError) extends AggregateStatus

}
