package com.productfoundry.akka.cqrs

import akka.actor._
import akka.persistence.AtLeastOnceDelivery
import com.productfoundry.akka.cqrs.publish.ReliableEventPublisher

/**
  * Aggregate.
  */
trait Aggregate
  extends Entity
    with CommitHandler
    with ActorLogging {

  /**
    * Aggregate state definition
    */
  type S <: AggregateState

  /**
    * @return Class of aggregate messages that are supported to update this aggregate
    */
  def messageClass: Class[_ <: AggregateMessage]

  type StateModifications = PartialFunction[AggregateEvent, S]

  type CommandHandler = PartialFunction[Any, Either[AggregateUpdateFailure, Changes]]

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
    * @param revision    of the aggregate state.
    * @param stateOption containing aggregate state.
    */
  case class RevisedState(revision: AggregateRevision, stateOption: Option[S]) {

    /**
      * Creates a copy with the commit applied to this state.
      */
    def applyCommit(commit: Commit): RevisedState = {
      val updated = commit.entries.foldLeft(this)(_ applyEntry _)
      require(updated.revision == commit.nextTag.revision)
      updated
    }

    /**
      * Creates new state with the event in scope.
      */
    private def createState(event: AggregateEvent): Option[S] = {
      if (factory.isDefinedAt(event)) {
        Some(factory.apply(event))
      } else {
        throw AggregateNotInitializedException(s"Unable to initialize aggregate with $event")
      }
    }

    /**
      * Updates the state with the event in scope.
      */
    private def updateState(state: S, event: AggregateEvent): Option[S] = {
      event match {
        case _: AggregateDeleteEvent => None
        case _ if state.update.isDefinedAt(event) => Some(state.update(event))
        case _ => throw AggregateInternalException(s"Update not defined for $event")
      }
    }

    private def applyEvent(event: AggregateEvent): Option[S] = {
      stateOption match {
        case None if revision == AggregateRevision.Initial =>
          createState(event)

        case Some(state) =>
          if (!factory.isDefinedAt(event) || allowFactoryEventInUpdate.lift(event).getOrElse(false)) {
            updateState(state, event)
          } else {
            throw AggregateAlreadyInitializedException(revision)
          }

        case _ =>
          throw AggregateInternalException(s"Unable to update state using event $event")
      }
    }

    /**
      * Creates a copy with the event applied to this state.
      */
    private def applyEntry(commitEntry: CommitEntry): RevisedState = {

      val event: AggregateEvent = commitEntry.event

      if (!event.hasType(messageClass)) {
        throw AggregateInvalidEventClassException(event)
      } else {
        copy(
          revision = commitEntry.revision,
          stateOption = applyEvent(event)
        )
      }
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
  protected[cqrs] var revisedState = RevisedState.Initial

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
    * @throws AggregateNotInitializedException if the state is not initialized
    */
  def state: S = stateOption.getOrElse(throw AggregateNotInitializedException("Aggregate state not initialized"))

  /**
    * Indication whether the state is initialized or not.
    *
    * @return true if this aggregate is initialized, otherwise false.
    */
  def initialized: Boolean = stateOption.isDefined

  /**
    * Keeps track of the current revision.
    *
    * We are not using [[lastSequenceNr]] for this, since we need to make sure the revision is only incremented with
    * actual state changes.
    */
  def revision: AggregateRevision = revisedState.revision

  /**
    * A tag uniquely identifies a specific revision of an aggregate.
    */
  def tag: AggregateTag = AggregateTag(entityName, entityId, revision)

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

    case message: AggregateCommandMessage if message.hasType(messageClass) =>
      handleCommandRequest(message.commandRequest)

    case message: AggregateCommandMessage =>
      throw AggregateInvalidMessageException(message)

    case message =>
      unhandled(message)
  }

  /**
    * @return Indication if the aggregate is deleted.
    */
  private def isDeleted: Boolean = {
    stateOption.isEmpty && revision > AggregateRevision.Initial
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
        val command = commandRequest.command
        handleCommand.lift.apply(command).fold {
          sender() ! Status.Failure(AggregateCommandUnknownException(command))
        } { changesAttempt =>
          tryCommit(changesAttempt)
        }
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
  def handleCommand: CommandHandler

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
  private def tryCommit(changesAttempt: Either[AggregateUpdateFailure, Changes]): Unit = {
    if (isDeleted) {
      sender() ! AggregateStatus.Failure(AggregateDeleted(revision))
    } else {
      changesAttempt.fold(cause => sender() ! AggregateStatus.Failure(cause), { changes =>
        if (changes.isEmpty) {
          sender() ! AggregateStatus.Success(AggregateResponse(tag, tag, changes.response))
        } else {
          commit(changes)
        }
      })
    }
  }

  def asAtLeastOnceDeliveryOption: Option[AtLeastOnceDelivery] = {
    this match {
      case atLeastOnceDelivery: AtLeastOnceDelivery => Some(atLeastOnceDelivery)
      case _ => None
    }
  }

  def asReliableEventPublisherOption: Option[ReliableEventPublisher] = {
    this match {
      case reliableEventPublisher: ReliableEventPublisher => Some(reliableEventPublisher)
      case _ => None
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
    * Check if this event is handled in both factory and update methods, if so, check if that is allowed for this specific event.
    *
    * @return Partial function that decides if it is allowed based on the actual event.
    */
  def allowFactoryEventInUpdate: PartialFunction[AggregateEvent, Boolean] = PartialFunction.empty

  /**
    * Commit changes.
    *
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
        // Keep the previous tag to return as part of the aggregate response
        val previous = tag

        // Updating state should never fail, since we already performed a dry run
        revisedState = updatedState

        // Perform additional mixed in commit handling logic
        val response = handleCommit(commit, AggregateResponse(tag, previous, changesToCommit.response))

        // Notify the sender of the commit
        sender() ! AggregateStatus.Success(response)
      }
    }

    // Fail revision check.
    def unexpectedRevision(expected: AggregateRevision): Unit = {
      throw AggregateInternalException("Revision unexpectedly updated between commits")
    }

    // Optionally perform a revision check and only perform the commit if successful
    commandRequest.checkRevision(revision)(performCommit)(unexpectedRevision)
  }

  /**
    * Can be overridden by commit handlers mixins to add additional commit behavior.
    *
    * @param commit   to handle.
    * @param response which can be manipulated by additional commit handlers.
    * @return Updated response.
    */
  override def handleCommit(commit: Commit, response: AggregateResponse): AggregateResponse = response

  /**
    * Sends the exception message to the caller.
    *
    * @param cause   the Throwable that caused the restart to happen.
    * @param message optionally the current message the actor processed when failing, if applicable.
    */
  override def preRestart(cause: Throwable, message: Option[Any]): Unit = {
    log.error(cause, "Failed to handle message type [{}] for persistenceId [{}].", message.getClass.getName, persistenceId)
    sender() ! Status.Failure(cause)
    super.preRestart(cause, message)
  }

  /**
    * Notify the sender in case persisting the event fails.
    */
  override protected def onPersistFailure(cause: Throwable, event: Any, seqNr: Long): Unit = {
    sender() ! Status.Failure(cause)
    super.onPersistFailure(cause, event, seqNr)
  }

  /**
    * Notify the sender in case persisting the event fails.
    */
  override protected def onPersistRejected(cause: Throwable, event: Any, seqNr: Long): Unit = {
    sender() ! Status.Failure(cause)
    super.onPersistRejected(cause, event, seqNr)
    context.stop(self)
  }
}
