package com.productfoundry.akka.cqrs

/**
 * Base command marker trait.
 */
trait AggregateCommand extends AggregateMessage {

  /**
   * Commands do not require a revision check by default.
   * @return indication if a revision check is required in order to process the command.
   */
  def isRevisionCheckRequired: Boolean = false
}

/**
 * Trait to force revision checks for certain commands.
 */
trait RequiredRevisionCheck {
  self: AggregateCommand =>

  /**
   * @return Indication that revision check is required.
   */
  override def isRevisionCheckRequired: Boolean = true
}

/**
 * Requests a command with additional info for the aggregate.
 */
trait CommandRequest extends AggregateMessage {

  /**
   * @return the command to execute.
   */
  def command: AggregateCommand

  /**
   * Creates a new command request with the expected revision.
   * @param expected revision for the aggregate.
   * @return command request with expected revision.
   */
  def withExpectedRevision(expected: AggregateRevision): CommandRequest

  /**
   * Check if the command is valid for the actual revision.
   * @param actual revision of the aggregate.
   * @param success executed when the revision check passed.
   * @param failed is executed when the revision check fails.
   * @return True when the revision is correct or expected is empty.
   */
  def checkRevision(actual: AggregateRevision)(success: () => Unit)(failed: (AggregateRevision) => Unit): Unit

  /**
   * Appends the specified metadata to the command request metadata.
   * @param metadata to append.
   * @return command request with updated metadata.
   */
  def withMetadata(metadata: Map[String, String]): CommandRequest

  /**
   * @return All metadata specified with the command.
   */
  def metadata: Map[String, String]
}

object CommandRequest {

  import scala.language.implicitConversions

  /**
   * Create a new command request for the specified command.
   * @param command to request.
   * @return Command request.
   */
  def apply(command: AggregateCommand): CommandRequest = AggregateCommandRequest(command)

  /**
   * Allows implicit conversion of a command into a command request with advanced options.
   * @param command to convert.
   * @return command request.
   */
  implicit def commandToRequest(command: AggregateCommand): CommandRequest = CommandRequest(command)
}

/**
 *
 * @param command to execute.
 * @param expectedOption for revision check.
 * @param metadata to store in the commit.
 */
private[this] case class AggregateCommandRequest(command: AggregateCommand, expectedOption: Option[AggregateRevision] = None, metadata: Map[String, String] = Map.empty) extends CommandRequest {
  type Id = command.Id

  /**
   * @return The id of the root entity.
   */
  override def id = command.id

  /**
   * Creates a new command request with the expected revision.
   * @param expected revision for the aggregate.
   * @return command request with expected revision.
   */
  override def withExpectedRevision(expected: AggregateRevision): CommandRequest = {
    copy(expectedOption = Some(expected))
  }


  /**
   * Check if the command is valid for the actual revision.
   * @param actual revision of the aggregate.
   * @param success executed when the revision check passed.
   * @param failed is executed when the revision check fails.
   * @return True when the revision is correct or expected is empty.
   */
  override def checkRevision(actual: AggregateRevision)(success: () => Unit)(failed: (AggregateRevision) => Unit): Unit = {

    def unspecified() = if (command.isRevisionCheckRequired) throw AggregateRevisionRequiredException(command) else success()

    def specified(expected: AggregateRevision) = if (actual == expected) success() else failed(expected)

    expectedOption.fold(unspecified())(specified)
  }

  /**
   * Appends the specified metadata to the command request metadata.
   * @param metadata to append.
   * @return command request with updated metadata.
   */
  override def withMetadata(metadata: Map[String, String]): CommandRequest = {
    copy(metadata = this.metadata ++ metadata)
  }
}