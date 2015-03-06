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
  override def aggregateId: AggregateId = command.aggregateId
}