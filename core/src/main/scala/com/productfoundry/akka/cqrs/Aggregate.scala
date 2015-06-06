package com.productfoundry.akka.cqrs

import akka.actor._
import akka.persistence.RecoveryFailure

/**
 * Aggregate.
 */
trait Aggregate
  extends Entity
  with CommitHandler
  with ActorLogging {

  type S <: AggregateState

  /**
   * Aggregate state required to validate commands.
   */
  trait AggregateState {

    /**
     * Applies a new event to update the state.
     *
     * Should be side-effect free.
     *
     * @return updated state.
     */
    def update: PartialFunction[AggregateEvent, S]
  }

  /**
   * Defines a factory that can create initial state from one or more events.
   */
  type StateFactory = PartialFunction[AggregateEvent, S]

  /**
   * Creates aggregate state.
   */
  val factory: StateFactory

  /**
   * Specifies the aggregate state with its revision.
   *
   * @param revision of the aggregate state.
   * @param stateOption containing aggregate state.
   */
  case class RevisedState(revision: AggregateRevision, stateOption: Option[S]) {

    /**
     * Creates a copy with the commit applied to this state.
     */
    def applyCommit(commit: Commit): RevisedState = {
      commit.entries.foldLeft(this)(_ applyEntry _)
    }

    /**
     * Creates a copy with the event applied to this state.
     */
    private def applyEntry(commitEntry: CommitEntry): RevisedState = {

      val event = commitEntry.event

      /**
       * Creates new state with the event in scope.
       */
      def createState: Option[S] = {
        if (factory.isDefinedAt(event)) Some(factory.apply(event)) else throw AggregateNotInitializedException(event)
      }

      /**
       * Updates the state with the event in scope.
       */
      def updateState(state: S): Option[S] = {
        if (event.isDeleteEvent) {
          None
        } else if (state.update.isDefinedAt(event)) {
          Some(state.update(event))
        } else {
          if (factory.isDefinedAt(event)) {
            throw AggregateAlreadyInitializedException(revision)
          } else {
            throw AggregateInternalException(s"Update not defined for $event")
          }
        }
      }

      copy(
        revision = commitEntry.revision,
        stateOption = stateOption.fold(createState)(updateState)
      )
    }
  }

  object RevisedState {

    /**
     * Initially, we start counting from the initial revision and without any predefined state.
     */
    val Initial = RevisedState(AggregateRevision.Initial, None)
  }

  /**
   * Holds the aggregate state with its revision.
   */
  private var revisedState = RevisedState.Initial

  /**
   * Aggregate is created before state is initialized and is therefore optional.
   *
   * @return `Some` aggregate state if initialized, otherwise `None`.
   */
  def stateOption: Option[S] = revisedState.stateOption

  /**
   * Provides access to the aggregate state.
   *
   * @return current aggregate state.
   * @throws AggregateInternalException if the state is not initialized
   */
  def state: S = stateOption.getOrElse(throw AggregateInternalException("Aggregate state not initialized"))

  /**
   * Indication whether the state is initialized or not.
   * @return true if this aggregate is initialized, otherwise false.
   */
  def initialized = stateOption.isDefined

  /**
   * Keeps track of the current revision.
   *
   * We are not using [[lastSequenceNr]] for this, since we need to make sure the revision is only incremented with
   * actual state changes.
   */
  def revision = revisedState.revision

  /**
   * A tag uniquely identifies a specific revision of an aggregate.
   */
  def tag = AggregateTag(entityName, entityId, revision)

  /**
   * The current command request.
   */
  private var commandRequestOption: Option[CommandRequest] = None

  /**
   * Provides access to the current command.
   *
   * @return current command.
   * @throws AggregateInternalException if no current command request is available.
   */
  def commandRequest: CommandRequest = commandRequestOption.getOrElse(throw AggregateInternalException("Current command request not defined"))

  /**
   * Handles incoming messages.
   */
  override def receiveCommand: Receive = {
    case commandRequest: CommandRequest =>
      executeIfNotDeleted(handleCommandRequest(commandRequest))

    case command: AggregateCommand =>
      executeIfNotDeleted(handleCommandRequest(CommandRequest(command)))

    case message =>
      executeIfNotDeleted(handleCommand.applyOrElse(message, unhandled))
  }

  /**
   * Ensures the block is only executed when the aggregate is not deleted.
   *
   * @param block to execute if not deleted.
   * @throws AggregateDeletedException if the aggregate was deleted.
   */
  private def executeIfNotDeleted(block: => Unit): Unit = {
    if (stateOption.isEmpty && revision > AggregateRevision.Initial) {
      throw AggregateDeletedException(revision)
    } else {
      block
    }
  }

  /**
   * Handle all commands and keep the command for reference in the aggregate.
   *
   * @param commandRequest to execute.
   */
  private def handleCommandRequest(commandRequest: CommandRequest): Unit = {

    def handleCommandInContext() = {
      try {
        commandRequestOption = Some(commandRequest)
        handleCommand.applyOrElse(commandRequest.command, unhandled)
      } finally {
        commandRequestOption = None
      }
    }

    def revisionConflict(expected: AggregateRevision) = handleConflict(RevisionConflict(expected, revision))

    commandRequest.checkRevision(revision)(handleCommandInContext)(revisionConflict)
  }

  /**
   * Handles all aggregate commands.
   */
  def handleCommand: Receive

  /**
   * Handle recovery of commits and aggregator confirmation status.
   */
  override def receiveRecover: Receive = {
    case commit: Commit => updateState(commit)
    case RecoveryFailure(cause) => log.error(cause, "Unable to recover: {}", persistenceId)
  }

  /**
   * Applies the commit to the current aggregate state.
   */
  private def updateState(commit: Commit): Unit = {
    revisedState = revisedState.applyCommit(commit)
  }


  /**
   * Attempts to commit changes.
   *
   * @param changesAttempt containing changes or a validation failure.
   */
  def tryCommit(changesAttempt: Either[DomainError, Changes]): Unit = {
    changesAttempt.fold(cause => sender() ! AggregateResult.Failure(cause), changes => commit(changes))
  }

  /**
   * Launches a new actor to collect all changes since the expected version and send them back to the sender.
   * @param conflict in the aggregate.
   */
  private def handleConflict(conflict: RevisionConflict): Unit = {
    val originalSender = sender()

    if (conflict.expected < conflict.actual) {
      context.actorOf(Props(new AggregateConflictView(persistenceId, originalSender, conflict)))
    } else {
      originalSender ! AggregateResult.Failure(conflict)
    }
  }

  /**
   * Commit changes.
   * @param changes to commit.
   */
  private def commit(changes: Changes): Unit = {

    def performCommit(): Unit = {
      // Construct commit to persist
      val commit = changes.withMetadata(commandRequest.metadata.toSeq: _*).createCommit(tag)

      // Dry run commit to make sure this aggregate does not persist invalid state
      revisedState.applyCommit(commit)

      // No exception thrown, persist and update state for real
      persist(commit) { _ =>
        // Updating state should never fail, since we already performed a dry run
        updateState(commit)

        // Notify the sender of the commit
        sender() ! AggregateResult.Success(tag, changes.response)

        // Perform additional mixed in commit handling logic
        handleCommit(commit)
      }
    }

    def unexpectedRevision(expected: AggregateRevision): Unit = {
      throw new AggregateInternalException("Revision unexpectedly updated between commits")
    }

    commandRequest.checkRevision(revision)(performCommit)(unexpectedRevision)
  }

  /**
   * Can be overridden by a mixin to handle commits.
   * @param commit that just got persisted.
   */
  override def handleCommit(commit: Commit): Unit = {}

  /**
   * Sends the exception message to the caller.
   *
   * @param cause the Throwable that caused the restart to happen.
   * @param message optionally the current message the actor processed when failing, if applicable.
   */
  override def preRestart(cause: Throwable, message: Option[Any]): Unit = {
    sender() ! Status.Failure(cause)
    super.preRestart(cause, message)
  }
}
