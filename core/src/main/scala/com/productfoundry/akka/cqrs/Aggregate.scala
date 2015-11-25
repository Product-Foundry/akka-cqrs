package com.productfoundry.akka.cqrs

import akka.actor._

/**
  * Aggregate.
  */
trait Aggregate
  extends Entity
  with CommitHandler
  with ActorLogging {

  type S <: AggregateState

  type StateModifications = PartialFunction[AggregateEvent, S]

  /**
    * Creates aggregate state.
    */
  val factory: StateModifications

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
    def update: StateModifications
  }

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
      val updated = commit.entries.foldLeft(this)(_ applyEntry _)
      assert(updated.revision == commit.nextTag.revision)
      updated
    }

    /**
      * Creates a copy with the event applied to this state.
      */
    private def applyEntry(commitEntry: CommitEntry): RevisedState = {

      val event = commitEntry.event

      // Creates new state with the event in scope.
      def createState: Option[S] = {
        if (factory.isDefinedAt(event)) Some(factory.apply(event)) else throw AggregateNotInitializedException(event)
      }

      // Updates the state with the event in scope.
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
      handleCommandRequest(commandRequest)

    case command: AggregateCommand =>
      handleCommandRequest(CommandRequest(command))

    case message =>
      handleCommand.applyOrElse(message, unhandled)
  }

  /**
    * @return Indication if the aggregate is deleted.
    */
  private def isDeleted: Boolean = stateOption.isEmpty && revision > AggregateRevision.Initial

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

    def revisionConflict(expected: AggregateRevision) = {
      sender() ! AggregateStatus.Failure(RevisionConflict(expected, revision))
    }

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
    if (isDeleted) {
      sender() ! AggregateStatus.Failure(AggregateDeleted(revision))
    } else {
      changesAttempt.fold(cause => sender() ! AggregateStatus.Failure(cause), { changes =>
        if (changes.isEmpty) {
          sender() ! AggregateStatus.Success(AggregateResponse(tag, changes.response))
        } else {
          commit(changes)
        }
      })
    }
  }

  /**
    * Gets the default headers to store with the commit.
    *
    * Default implementation simply copies all headers specified by the command. Only invoked when the changes
    * do not already specify headers.
    *
    * @return the commit headers to store with the commit.
    */
  def getDefaultHeaders: Option[CommitHeaders] = commandRequestOption.flatMap(_.headersOption)

  /**
    * Commit changes.
    * @param changes to commit.
    */
  private def commit(changes: Changes): Unit = {

    // Performs a commit for the specified changes
    def performCommit(): Unit = {

      // Add default headers when no headers are present
      val changesToCommit = changes.headersOption.fold(getDefaultHeaders.fold(changes)(changes.withHeaders))(_ => changes)

      // Create commit to freeze changes
      val commit = changesToCommit.createCommit(tag)

      // Dry run commit to make sure this aggregate does not persist invalid state
      val updatedState = revisedState.applyCommit(commit)

      // No exception thrown, persist and update state for real
      persist(commit) { _ =>
        // Updating state should never fail, since we already performed a dry run
        revisedState = updatedState

        // Perform additional mixed in commit handling logic
        val response = handleCommit(commit, AggregateResponse(tag, changesToCommit.response))

        // Notify the sender of the commit
        sender() ! AggregateStatus.Success(response)
      }
    }

    // Fail revision check.
    def unexpectedRevision(expected: AggregateRevision): Unit = {
      throw new AggregateInternalException("Revision unexpectedly updated between commits")
    }

    // Optionally perform a revision check and only perform the commit if successful
    commandRequest.checkRevision(revision)(performCommit)(unexpectedRevision)
  }

  /**
    * Can be overridden by commit handlers mixins to add additional commit behavior.
    * @param commit to handle.
    * @param response which can be manipulated by additional commit handlers.
    * @return Updated response.
    */
  override def handleCommit(commit: Commit, response: AggregateResponse): AggregateResponse = response

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
