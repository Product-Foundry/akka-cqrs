package com.productfoundry.akka.cqrs

import akka.actor._
import akka.persistence.{PersistentActor, RecoveryFailure}
import com.productfoundry.akka.GracefulPassivation

/**
 * Aggregate.
 */
trait Aggregate
  extends Entity
  with PersistentActor
  with GracefulPassivation
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
   * Id is based on the actor path and determined only once
   */
  val entityId: String = self.path.name

  /**
   * Persistence id is the same as id, but needs to be a def in order to mix in behavior that relies on persistence.
   */
  override def persistenceId: String = entityId

  /**
   * Defines a factory that can create initial state from one or more events.
   */
  type StateFactory = PartialFunction[AggregateEvent, S]

  /**
   * Creates aggregate state.
   */
  val factory: StateFactory

  /**
   * Aggregate is created before state is initialized and is therefore optional.
   *
   * Keep this private, since this is the only mutable member of the aggregate and handling should be uniform.
   */
  private var stateOpt: Option[S] = None

  /**
   * Aggregate is created before state is initialized and is therefore optional.
   *
   * @return `Some` aggregate state if initialized, otherwise `None`.
   */
  def stateOption: Option[S] = stateOpt

  /**
   * Provides access to the aggregate state.
   *
   * @return current aggregate state.
   * @throws AggregateInternalException if the state is not initialized
   */
  def state: S = stateOpt.getOrElse(throw AggregateInternalException("Aggregate state not initialized"))

  /**
   * The current command request
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
   * Indication whether the state is initialized or not.
   * @return true if this aggregate is initialized, otherwise false.
   */
  def initialized = stateOpt.isDefined

  /**
   * Keeps track of the current revision.
   *
   * The revision should increment with every commit without creating gaps.
   * At first sight, this could be backed by [[lastSequenceNr]]. However, commit handlers can also persist additional
   * events for internal use, which shouldn't affect the aggregate revision.
   */
  private var revision = AggregateRevision.Initial

  /**
   * @return the current revision of this aggregate.
   */
  def currentRevision = revision

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
    if (stateOpt.isEmpty && revision > AggregateRevision.Initial) {
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
    commandRequest.checkRevision(revision) {
      try {
        commandRequestOption = Some(commandRequest)
        handleCommand.applyOrElse(commandRequest.command, unhandled)
      } finally {
        commandRequestOption = None
      }
    } { expected =>
      handleConflict(RevisionConflict(expected, revision))
    } {
      throw AggregateRevisionRequiredException(commandRequest.command)
    }
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
    revision = commit.revision
    stateOpt = applyCommit(stateOpt, commit)
  }

  /**
   * Applies a commit to the specified state.
   *
   * Can be used for dry run or aggregate update.
   */
  private def applyCommit(stateOption: Option[S], commit: Commit): Option[S] = {
    commit.events.foldLeft(stateOption)((s, e) => applyEvent(s, e))
  }

  /**
   * Initializes or updates state with the specified event.
   *
   * Can be used for dry run or aggregate update.
   */
  private def applyEvent(stateOption: Option[S], event: AggregateEvent): Option[S] = {
    stateOption.fold[Option[S]] {
      if (factory.isDefinedAt(event)) {
        // We have no state, but we know how to create it from the specified event.
        Some(factory.apply(event))
      } else {
        // State is empty, but the factory also doesn't know how to deal with this event, rather this than a MatchError
        throw AggregateNotInitializedException(event)
      }
    } { state =>
      if (event.isDeleteEvent) {
        // A delete sets the state to None, but keeps the revision, allowing us to detect the delete
        None
      } else if (state.update.isDefinedAt(event)) {
        // We have state and we know how to update it
        Some(state.update(event))
      } else {
        if (factory.isDefinedAt(event)) {
          // We don't know how to update the state, but the factory has a handler, so let's give a helpful exception
          throw AggregateAlreadyInitializedException(revision)
        } else {
          // Most likely, the developer forgot to handle the event in the state update method, rather this than a MatchError
          throw AggregateInternalException(s"Update not defined for $event")
        }
      }
    }
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

    // Construct full headers, prefer changes headers over user-specified command headers in case of duplicates
    val headers = commandRequest.headers ++ changes.headers

    // Construct commit to persist
    val commit = Commit(CommitMetadata(persistenceId, revision.next, headers), changes.events)

    // Dry run commit to make sure this aggregate does not persist invalid state
    applyCommit(stateOpt, commit)

    // No exception thrown, persist and update state for real
    persist(commit) { persistedCommit =>
      // Updating state should never fail, since we already performed a dry run
      updateState(persistedCommit)

      // Notify the sender of the commit
      sender() ! AggregateResult.Success(CommitResult(revision, changes.payload))

      // Perform additional mixed in commit handling logic
      handleCommit(persistedCommit)
    }
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
