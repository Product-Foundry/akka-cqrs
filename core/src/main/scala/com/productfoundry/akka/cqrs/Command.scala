package com.productfoundry.akka.cqrs

/**
 * Base command marker trait.
 */
trait Command extends AggregateMessage

/**
 * Command message.
 *
 * @param expected revision.
 * @param command to execute.
 */
case class CommandMessage(expected: AggregateRevision, command: Command) extends AggregateMessage {
  type Id = command.Id

  /**
   * @return The id of the root entity.
   */
  override def id = command.id
}