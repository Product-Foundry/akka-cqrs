package com.productfoundry.akka.cqrs

import akka.actor._
import akka.persistence.PersistentActor
import akka.util.Timeout
import com.productfoundry.akka.GracefulPassivation

import scala.concurrent.Await

/**
 * Aggregate.
 *
 * @tparam E creates aggregate state.
 * @tparam S aggregated state.
 */
trait Aggregate[E <: DomainEvent, S <: AggregateState[E, S]]
  extends Entity
  with PersistentActor
  with GracefulPassivation
  with CommitHandler
  with ActorLogging {

  /**
   * All domain entities have an id.
   * @return The id of the domain entity.
   */
  override val id = AggregateId(self.path)

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
    case CommandMessage(expected, command) =>
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
   * @param changesHandler callback handler for the specified changes.
   */
  def tryCommit(expected: AggregateRevision)(changesAttempt: Either[AggregateError, Changes[E]])(implicit changesHandler: ChangesHandler = DefaultChangesHandler): Unit = {
    changesAttempt match {
      case Right(changes) =>
        if (expected != revision) {
          changesHandler.onConflict(RevisionConflict(id, expected, revision))
        } else {
          commit(changes)(changesHandler.onCommit)
        }
      case Left(cause) =>
        changesHandler.onFailure(cause)
    }
  }

  /**
   * Specialized commit function that only attempt a commit if this aggregate is not already initialized.
   *
   * @param expected revision.
   * @param changesAttempt containing changes or a validation failure.
   * @param changesHandler callback handler for the specified changes.
   */
  def tryCreate(expected: AggregateRevision)(changesAttempt: => Either[AggregateError, Changes[E]])(implicit changesHandler: ChangesHandler = DefaultChangesHandler): Unit = {
    if (initialized) {
      changesHandler.onFailure(AggregateAlreadyExists(id))
    } else {
      tryCommit(expected)(changesAttempt)
    }
  }

  /**
   * Specialized commit function that only attempt a commit if this aggregate is already initialized.
   *
   * @param expected revision.
   * @param changesAttempt containing changes or a validation failure.
   * @param changesHandler callback handler for the specified changes.
   */
  def tryUpdate(expected: AggregateRevision)(changesAttempt: => Either[AggregateError, Changes[E]])(implicit changesHandler: ChangesHandler = DefaultChangesHandler): Unit = {
    if (initialized) {
      tryCommit(expected)(changesAttempt)
    } else {
      changesHandler.onFailure(AggregateUnknown(id))
    }
  }

  /**
   * Specifies handling behavior for offered changes.
   */
  trait ChangesHandler {

    /**
     * Invoked on successful commit.
     * @param commit with persistence details and events.
     */
    def onCommit(commit: CommitResult): Unit

    /**
     * Invoked on failure, no changes are persisted.
     * @param cause of failure.
     */
    def onFailure(cause: AggregateError): Unit

    /**
     * Invoked when a revision conflict occurs.
     * @param conflict information.
     */
    def onConflict(conflict: RevisionConflict): Unit
  }

  /**
   * Default changes handler.
   */
  trait DefaultChangesHandler extends ChangesHandler {

    /**
     * Replies a success status with the new revision to the sender.
     * @param commitResult with revisions.
     */
    override def onCommit(commitResult: CommitResult): Unit = sender() ! AggregateStatus.Success(commitResult)

    /**
     * Replies a failure status with the cause to the sender.
     * @param cause of failure.
     */
    override def onFailure(cause: AggregateError): Unit = sender() ! AggregateStatus.Failure(cause)

    /**
     * Replies a failure status with the conflict message to the sender.
     * @param conflict information.
     */
    override def onConflict(conflict: RevisionConflict): Unit = {
      val originalSender = sender()

      if (conflict.expected < conflict.actual) {
        context.actorOf(Props(new AggregateConflictView(persistenceId, originalSender, conflict)))
      } else {
        originalSender ! AggregateStatus.Failure(conflict)
      }
    }
  }

  /**
   * Used by default to handle all changes.
   */
  object DefaultChangesHandler extends DefaultChangesHandler

  /**
   * Commit changes.
   * @param changes to commit.
   * @param onCommit invoked when commit is persisted successfully.
   */
  private def commit(changes: Changes[E])(onCommit: CommitResult => Unit): Unit = {
    // Construct commit to persist
    val commit = Commit(id, revision.next, System.currentTimeMillis(), changes.events, changes.headers)

    // Dry run commit to make sure this aggregate does not persist invalid state
    applyCommit(stateOpt, commit)

    // No exception thrown, persist and update state for real
    persist(commit) {
      commit =>
        val domainRevision = aggregateCommit(commit)
        updateState(commit)
        handleCommit(commit)
        onCommit(CommitResult(commit.revision, domainRevision))
    }
  }

  /**
   * Aggregates the commit to a domain wide log in a journal independent manner.
   * @param commit from this aggregate.
   * @return aggregator result.
   */
  private def aggregateCommit(commit: Commit[E]): DomainRevision = {
    try {
      import akka.pattern.ask
      import context.dispatcher

      import scala.concurrent.duration._

      implicit val duration = 1.minute
      implicit val timeout = Timeout(duration)

      // Send the commit to the aggregator and get the aggregator commit revision
      val domainRevisionFuture = (context.parent ? DomainAggregator.Get).mapTo[ActorRef].flatMap { domainAggregatorRef =>
        (domainAggregatorRef ? commit).mapTo[DomainRevision]
      }

      // We are blocking to make sure no messages are processed until we received a revision from the commit aggregator
      Await.result(domainRevisionFuture, duration)
    } catch {
      case e: Exception =>
        log.error(e, "Commit aggregation failed for: {}", commit)
        throw AggregateException("Commit aggregation failed")
    }
  }

  /**
   * Can be overridden by a mixin to handle commits.
   * @param commit that just got persisted.
   */
  override def handleCommit(commit: Commit[DomainEvent]): Unit = {
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
