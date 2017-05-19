package com.productfoundry.akka.cqrs

import com.productfoundry.akka.PassivationConfig
import com.productfoundry.akka.cqrs.DummyAggregate._

case class DummySnapshot(count: Int)

class DummyAggregate(val passivationConfig: PassivationConfig) extends Aggregate {

  type S = DummyState

  override type M = DummyMessage

  override val messageClass = classOf[DummyMessage]

  override def handleCommand: CommandHandler = {
    case Create(aggregateId) =>
      Right(Changes(Created(aggregateId)))

    case CreateFailure(aggregateId) =>
      Right(Changes(UnsupportedEvent(aggregateId)))

    case Count(aggregateId) =>
      Right(Changes(Counted(aggregateId, state.count + 1)))

    case CountWithRequiredRevisionCheck(aggregateId) =>
      Right(Changes(Counted(aggregateId, state.count + 1)))

    case CountWithPayload(aggregateId) =>
      Right(Changes(Counted(aggregateId, state.count + 1)).withResponse(state.count))

    case Increment(aggregateId, amount) =>
      if (amount > 0) {
        Right(Changes(Incremented(aggregateId, amount)))
      } else {
        Left(ValidationError(InvalidIncrement(amount)))
      }

    case Delete(aggregateId) =>
      Right(Changes(Deleted(aggregateId)))

    case NoOp(_) =>
      Right(Changes())
  }

  /**
    * Sends the configured passivation message to the parent actor on receive timeout.
    *
    * @param message unhandled message.
    */
  override def unhandled(message: Any): Unit = message match {
    case GetCount(_) =>
      sender() ! state.count

    case Snapshot(_, includeState) =>
      if (includeState) {
        saveSnapshot(DummyStateSnapshot(state.count))
      } else {
        saveSnapshot()
      }

      requestPassivation()
      sender() ! SnapshotCompleteAndTerminated

    case _ =>
      super.unhandled(message)
  }

  /**
    * Handles all saved snapshots.
    */
  override def handleSnapshot: SnapshotHandler = {
    case Some(DummyStateSnapshot(count)) => DummyState(count)
    case None => DummyState(0)
  }

  override val factory: StateModifications = {
    case Created(_) =>
      DummyState(0)
  }

  case class DummyState(count: Int) extends AggregateState {
    override def update: StateModifications = {
      case Counted(_, _count) =>
        copy(count = _count)
      case Incremented(_, amount) =>
        copy(count = count + amount)
    }
  }

}

object DummyAggregate {

  sealed trait DummyMessage extends AggregateMessage {
    override type Id = DummyId
  }

  sealed trait DummyAggregateCommand extends DummyMessage with AggregateCommand

  case class Create(id: DummyId) extends DummyAggregateCommand

  case class CreateFailure(id: DummyId) extends DummyAggregateCommand

  case class Count(id: DummyId) extends DummyAggregateCommand

  case class CountWithRequiredRevisionCheck(id: DummyId) extends DummyAggregateCommand with RequiredRevisionCheck

  case class CountWithPayload(id: DummyId) extends DummyAggregateCommand

  case class Increment(id: DummyId, amount: Int) extends DummyAggregateCommand

  case class Delete(id: DummyId) extends DummyAggregateCommand

  case class NoOp(id: DummyId) extends DummyAggregateCommand

  sealed trait DummyEvent extends DummyMessage with AggregateEvent

  case class UnsupportedEvent(id: DummyId) extends AggregateEvent {
    override type Id = DummyId
  }

  case class Created(id: DummyId) extends DummyEvent

  case class Counted(id: DummyId, count: Int) extends DummyEvent

  case class Incremented(id: DummyId, amount: Int) extends DummyEvent

  case class Deleted(id: DummyId) extends DummyEvent with AggregateDeleteEvent

  sealed trait TestValidationMessage extends ValidationMessage

  case class InvalidIncrement(value: Int) extends TestValidationMessage

  case class GetCount(id: DummyId) extends DummyMessage

  case class Snapshot(id: DummyId, includeState: Boolean = true) extends DummyMessage

  case object SnapshotCompleteAndTerminated

  case class DummyStateSnapshot(count: Int) extends AggregateStateSnapshot

}
