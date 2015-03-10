package com.productfoundry.akka.cqrs

import akka.actor._
import akka.persistence.PersistentActor
import com.productfoundry.akka.GracefulPassivation
import com.productfoundry.akka.cqrs.DomainAggregator._

/**
 * Aggregate.
 *
 * @tparam E creates aggregate state.
 * @tparam S aggregated state.
 */
trait Aggregate[E <: AggregateEvent, S <: AggregateState[E, S]]
  extends Entity
  with PersistentActor
  with GracefulPassivation
  with CommitHandler
  with ActorLogging {

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
   * Preferably keep this private when possible, since this is the only mutable member of the aggregate.
   */
  private var stateOpt: Option[S] = None

  /**
   * Provides access to the aggregate state.
   *
   * Throws [[AggregateException]] if the state is not initialized.
   *
   * @return current aggregate state.
   */
  def state: S = stateOpt.getOrElse(throw new AggregateException("Aggregate not initialized"))

  /**
   * @return Indication whether the state is initialized or not.
   */
  def initialized = stateOpt.isDefined

  /**
   * @return the current revision of this aggregate.
   */
  def revision = AggregateRevision(lastSequenceNr)

  /**
   * Command handler is final so that it can always correctly handle the aggregator response.
   */
  final override def receiveCommand: Receive = {
    case AggregateCommandMessage(expected, command) =>
      handleWithRevision(expected, command)

    case command =>
      handleWithRevision(AggregateRevision.Initial, command)
  }

  /**
   * Ensures all commands are handled with a revision.
   *
   * @param expected revision.
   * @param command to execute.
   */
  private def handleWithRevision(expected: AggregateRevision, command: Any) = {
    handleCommand(expected).applyOrElse(command, unhandled)
  }

  /**
   * Redefined command handler.
   *
   * @param expected revision.
   */
  def handleCommand(expected: AggregateRevision): Receive

  /**
   * Handle recovery of commits and aggregator confirmation status.
   */
  final override def receiveRecover: Receive = {
    case commit: Commit[E] => updateState(commit)
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
    stateOption.fold {
      if (factory.isDefinedAt(event)) {
        factory(event)
      } else {
        throw new AggregateException(s"Unable to initialize state with: $event")
      }
    } { state =>
      if (state.update.isDefinedAt(event)) {
        state.update(event)
      } else {
        throw new AggregateException(s"Unable to update state with: $event")
      }
    }

    Some(stateOption.fold(factory.apply(event))(state => state.update(event)))
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
   * @param expected revision.
   * @param changesAttempt containing changes or a validation failure.
   */
  def tryCreate(expected: AggregateRevision)(changesAttempt: => Either[AggregateError, Changes[E]]): Unit = {
    if (initialized) {
      sender() ! AggregateStatus.Failure(AggregateAlreadyInitialized)
    } else {
      tryCommit(expected)(changesAttempt)
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
    // Construct commit to persist
    val commit = Commit(revision.next, System.currentTimeMillis(), changes.events, changes.headers)

    // Dry run commit to make sure this aggregate does not persist invalid state
    applyCommit(stateOpt, commit)

    // No exception thrown, persist and update state for real
    persist(commit) { persistedCommit =>
      // Updating state should never fail, since we already performed a dry run
      updateState(persistedCommit)

      // Aggregate the commit globally makes it much easier to build a view of all events in a domain context
      aggregateCommit(persistedCommit)

      // Commit handler is outside our control, so we don't want it to crash our aggregate
      try {
        handleCommit(persistedCommit)
      } catch {
        case e: Exception => log.error(e, "Handling commit: {}", persistedCommit)
      }
    }
  }

  /**
   * Aggregates the commit to a domain wide log in a journal independent manner.
   *
   * Sends the commit revision back to the original sender on success.
   */
  class CommitAggregator(aggregateSupervisor: ActorRef, originalSender: ActorRef, commit: Commit[E]) extends Actor {

    import scala.concurrent.duration._

    override def preStart(): Unit = {
      context.setReceiveTimeout(1.minute)
      aggregateSupervisor ! GetDomainAggregator
    }

    override def receive: Actor.Receive = {
      case DomainAggregatorRef(ref) =>
        ref ! commit

      case domainRevision: DomainRevision =>
        originalSender ! AggregateStatus.Success(CommitResult(commit.revision, domainRevision))
        self ! PoisonPill

      case ReceiveTimeout =>
        log.error("Unable to aggregate commit: {}", commit)
        originalSender ! AggregateStatus.Failure(DomainAggregatorFailed(commit.revision))
        self ! PoisonPill
    }
  }

  /**
   * Launches a new actor to also persist the commit globally and sends back a global domain revision to the sender.
   * @param commit to aggregate.
   */
  private def aggregateCommit(commit: Commit[E]): Unit = {
    val supervisor = context.parent
    val originalSender = sender()
    context.actorOf(Props(new CommitAggregator(supervisor, originalSender, commit)))
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
