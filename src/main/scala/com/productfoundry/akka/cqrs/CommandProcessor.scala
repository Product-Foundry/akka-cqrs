package com.productfoundry.akka.cqrs

import akka.persistence.PersistentActor

import scala.reflect.ClassTag

import scalaz._
import Scalaz._

abstract class CommandProcessor[E](implicit val EventTag: ClassTag[E]) extends PersistentActor {
  self: PersistentActor =>

  import scala.language.implicitConversions

  case class CommitHandler[C <: Commit](commit: DomainValidation[C] => Unit)

  /**
   * Commits all changes and replies to the sender.
   *
   * In case of success, the reply function defined in [[CommitAndReply]] is invoked and the result sent as [[Success]].
   * In case of [[Failure]], the failure is replied.
   *
   * @return [[CommitHandler]] for [[CommitAndReply]].
   */
  implicit def CommitAndReplyHandler: CommitHandler[CommitAndReply] = CommitHandler[CommitAndReply] {
    case Success(c) =>
      val last = c.events.last

      persist(c.events.toSeq) { e =>
        updateState(e)

        if (e == last) {
          sender ! c.replyFunction().successNel
        }
      }

    case failure => sender ! failure
  }

  /**
   * Commits all changes without replying to the sender.
   *
   * In case of [[Failure]] no changes are made.
   *
   * @return [[CommitHandler]] for [[CommitSilently]].
   */
  implicit def CommitSilentlyHandler: CommitHandler[CommitSilently] = CommitHandler[CommitSilently] {
    case Success(commit) => persist(commit.events)(updateState)
    case failure =>
  }

  /**
   * Tro to commit changes using the matching commit handler.
   *
   * @param commit commit to attempt.
   * @param handler implicit [[CommitHandler]] based on the [[Commit]] type.
   * @tparam C the [[Commit]] type defines how the commit handler.
   */
  def commitSuccess[C <: Commit](commit: DomainValidation[C])(implicit handler: CommitHandler[C]): Unit = {
    handler.commit(commit)
  }

  /**
   * Overrides default recovery behavior to handle events for event migration.
   */
  override def receiveRecover: Receive = {
    case IgnoredEvent =>
    case e: AggregatedEvent if receiveRecover.isDefinedAt(e) => receiveRecover(e)
    case EventTag(e) => updateState(e)
    case e if recoverSnapshot.isDefinedAt(e) => recoverSnapshot(e)
  }

  /**
   * Override to handle snapshot recover events.
   */
  def recoverSnapshot: Receive = PartialFunction.empty[Any, Unit]

  /**
   * Update the state of the command processor using the persisted event.
   *
   * @param event The persisted event.
   */
  def updateState(event: E): Unit

  /**
   * Contains events to be persisted.
   */
  trait Commit {

    /**
     * Defines events to persist.
     * @return events to persist.
     */
    def events: List[E]
  }

  object Commit {
    /**
     * Creates a commit that does not send a reply.
     *
     * Ensuring there is at least one event in a commit.
     *
     * @param event The first event to persist.
     * @param events More events.
     * @return commit containing all events.
     */
    def apply(event: E, events: E*): CommitSilently = new CommitSilently(event +: List(events: _*))
  }

  /**
   * Persists events without sending a reply to the sender.
   *
   * @param events the events to persist.
   */
  class CommitSilently(val events: List[E]) extends Commit {

    /**
     * Transforms this commit into a commit that sends a reply to the sender.
     *
     * @param block the reply block.
     * @return the new commit.
     */
    def reply(block: => Any) = new CommitAndReply(events, block _)
  }

  /**
   * Process events and send a single reply to the sender after successful persistence or in case of failure.
   *
   * @param events the events to persist.
   * @param block the reply block, invoked in case of [[Success]] after successful persistence.
   */
  class CommitAndReply(val events: List[E], block: () => Any) extends Commit {
    def replyFunction = block
  }
}

/**
 * Marker trait for commands.
 */
trait DomainCommand extends Product with Serializable

/**
 * Marker trait for domain events.
 */
trait DomainEvent extends Product with Serializable

/**
 * Allow events to be ignored during recovery.
 */
case object IgnoredEvent extends DomainEvent

/**
 * Useful when a domain event needs to be split into multiple events during recovery.
 */
case class AggregatedEvent(events: Any*) extends DomainEvent

/**
 * Marker trait for validation messages.
 */
trait ValidationMessage extends Product

