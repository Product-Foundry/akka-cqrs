package com.productfoundry.akka.cqrs

/**
 * Base command marker trait.
 */
trait AggregateCommand extends AggregateMessage

/**
 * Command message.
 *
 * @param expected revision.
 * @param command to execute.
 */
case class AggregateCommandMessage(expected: AggregateRevision, command: AggregateCommand) extends AggregateMessage {
  type Id = command.Id

  /**
   * @return The id of the root entity.
   */
  override def id = command.id
}