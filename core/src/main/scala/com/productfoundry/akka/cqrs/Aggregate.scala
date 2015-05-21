package com.productfoundry.akka.cqrs

import akka.actor._
import akka.persistence.{RecoveryFailure, PersistentActor}
import com.productfoundry.akka.GracefulPassivation
import com.productfoundry.akka.cqrs.DomainAggregator._

import scala.util.control.NonFatal

/**
 * Aggregate.
 *
 * @tparam E creates aggregate state.
 */
trait Aggregate[E <: AggregateEvent]
  extends Entity
  with PersistentActor
  with GracefulPassivation
  with CommitHandler
  with ActorLogging {

  type S <: AggregateState

  /**
   * Aggregate state.
   */
  trait AggregateState {
    def update: PartialFunction[E, S]
  }

  /**
   * Persistence id is based on the actor path.
   */
  override val persistenceId: String = PersistenceId(self.path).value

  type StateFactory = PartialFunction[E, S]

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
   * Throws [[AggregateException]] if the state is not initialized.
   *
   * @return current aggregate state.
   */
  def state: S = stateOpt.getOrElse(throw new AggregateException("Aggregate state not defined"))

  /**
   * The current command message
   */
  private var commandOption: Option[AggregateCommandMessage] = None

  /**
   * Provides access to the current command.
   *
   * Throws [[AggregateException]] if the command is not available.
   *
   * @return current command.
   */
  def command: AggregateCommandMessage = commandOption.getOrElse(throw new AggregateException("Current command not defined"))

  /**
   * Gives the expected revision for a command.
   */
  def expected: AggregateRevision = command.expected

  /**
   * @return Indication whether the state is initialized or not.
   */
  def initialized = revision != AggregateRevision.Initial && stateOpt.isDefined

  /**
   * @return the current revision of this aggregate.
   */
  def revision = AggregateRevision(lastSequenceNr)

  /**
   * Command handler is final so that it can always correctly handle the aggregator response.
   */
  final override def receiveCommand: Receive = {
    case commandMessage: AggregateCommandMessage => handleCommandMessage(commandMessage)
    case command: AggregateCommand => handleCommandMessage(AggregateCommandMessage(AggregateRevision.Initial, command))
    case message => handleCommand.applyOrElse(message, unhandled)
  }

  /**
   * Handle all commands and keeping the command for reference in the aggregate.
   *
   * @param command to execute.
   */
  private def handleCommandMessage(command: AggregateCommandMessage) = {
    try {
      commandOption = Some(command)
      handleCommand.applyOrElse(command.command, unhandled)
    } finally {
      commandOption = None
    }
  }

  /**
   * Redefined command handler.
   */
  def handleCommand: Receive

  /**
   * Handle recovery of commits and aggregator confirmation status.
   */
  final override def receiveRecover: Receive = {
    case commit: Commit[E] => updateState(commit)
    case RecoveryFailure(cause) => log.error(cause, "Unable to recover: {}", persistenceId)
  }

  /**
   * Applies the commit to the current aggregate state.
   */
  private def updateState(commit: Commit[E]): Unit = {
    stateOpt = applyCommit(stateOpt, commit)
  }

  /**
   * Applies a commit to the specified state.
   *
   * Can be used for dry run or aggregate update.
   */
  private def applyCommit(stateOption: Option[S], commit: Commit[E]): Option[S] = {
    commit.events.foldLeft(stateOption) { (_stateOption, _event) =>
      applyEvent(_stateOption, _event)
    }
  }

  /**
   * Initializes or updates state with the specified event.
   *
   * Can be used for dry run or aggregate update.
   */
  private def applyEvent(stateOption: Option[S], event: E): Option[S] = {
    if (event.isInstanceOf[AggregateDeleteEvent]) None else Some(stateOption.fold(factory.apply(event))(state => state.update(event)))
  }

  /**
   * Attempts to commit changes.
   *
   * @param expected revision.
   * @param changesAttempt containing changes or a validation failure.
   */
  def tryCommit(expected: AggregateRevision)(changesAttempt: Either[AggregateError, Changes[E]]): Unit = {
    changesAttempt match {
      case Right(changes) =>
        if (expected != revision) {
          handleConflict(RevisionConflict(expected, revision))
        } else {
          commit(changes)
        }
      case Left(cause) =>
        sender() ! AggregateStatus.Failure(cause)
    }
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
      originalSender ! AggregateStatus.Failure(conflict)
    }
  }

  /**
   * Specialized commit function that only attempt a commit if this aggregate is not already initialized.
   *
   * @param changesAttempt containing changes or a validation failure.
   */
  def tryCreate(changesAttempt: => Either[AggregateError, Changes[E]]): Unit = {
    if (initialized) {
      sender() ! AggregateStatus.Failure(AggregateAlreadyInitialized)
    } else {
      tryCommit(AggregateRevision.Initial)(changesAttempt)
    }
  }

  /**
   * Specialized commit function that only attempt a commit if this aggregate is already initialized.
   *
   * @param expected revision.
   * @param changesAttempt containing changes or a validation failure.
   */
  def tryUpdate(expected: AggregateRevision)(changesAttempt: => Either[AggregateError, Changes[E]]): Unit = {
    if (initialized) {
      tryCommit(expected)(changesAttempt)
    } else {
      sender() ! AggregateStatus.Failure(AggregateNotInitialized)
    }
  }

  /**
   * Commit changes.
   * @param changes to commit.
   */
  private def commit(changes: Changes[E]): Unit = {

    // Construct full headers, prefer changes header over command headers in case of duplicates
    val headers = command.headers ++ changes.headers

    // Construct commit to persist
    val commit = Commit(revision.next, changes.events, System.currentTimeMillis(), headers)

    // Dry run commit to make sure this aggregate does not persist invalid state
    applyCommit(stateOpt, commit)

    // No exception thrown, persist and update state for real
    persist(commit) { persistedCommit =>
      if (revision != commit.revision) {
        log.warning("Unexpected aggregate commit revision, expected: {}, actual: {}", revision, commit.revision)
      }

      // Updating state should never fail, since we already performed a dry run
      updateState(persistedCommit)

      // Aggregate the commit globally makes it much easier to build a view of all events in a domain context
      aggregateCommit(persistedCommit, changes.payload)

      // Commit handler is outside our control, so we don't want it to crash our aggregate
      try {
        handleCommit(persistedCommit)
      } catch {
        case NonFatal(e) => log.error(e, "Handling commit: {}", persistedCommit)
      }
    }
  }

  /**
   * Aggregates the commit to a domain wide log in a journal independent manner.
   *
   * Sends the commit revision back to the original sender on success.
   */
  class CommitAggregator(aggregateSupervisor: ActorRef, originalSender: ActorRef, commit: Commit[E], payload: Any) extends Actor {

    import scala.concurrent.duration._

    override def preStart(): Unit = {
      context.setReceiveTimeout(1.minute)
      aggregateSupervisor ! GetDomainAggregator
    }

    override def receive: Actor.Receive = {
      case DomainAggregatorRef(ref) =>
        ref ! commit

      case DomainAggregatorRevision(domainRevision) =>
        originalSender ! AggregateStatus.Success(CommitResult(commit.revision, domainRevision, payload))
        self ! PoisonPill

      case ReceiveTimeout =>
        log.error("Unable to aggregate commit: {}", commit)
        originalSender ! AggregateStatus.Failure(DomainAggregatorFailed(commit.revision))
        self ! PoisonPill
    }

    override def preRestart(reason: Throwable, message: Option[Any]): Unit = {
      log.error(reason, "Handling message: {}", message)
      super.preRestart(reason, message)
    }
  }

  /**
   * Launches a new actor to also persist the commit globally and sends back a global domain revision to the sender.
   * @param commit to aggregate.
   */
  private def aggregateCommit(commit: Commit[E], payload: Any): Unit = {
    val supervisor = context.parent
    val originalSender = sender()
    context.actorOf(Props(new CommitAggregator(supervisor, originalSender, commit, payload)))
  }

  /**
   * Can be overridden by a mixin to handle commits.
   * @param commit that just got persisted.
   */
  override def handleCommit(commit: Commit[AggregateEvent]): Unit = {
  }

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
