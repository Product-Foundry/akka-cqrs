package com.productfoundry.akka.cqrs

import java.util.UUID

import com.productfoundry.akka.PassivationConfig
import com.productfoundry.akka.cqrs.TaskCommand.{AssignTask, CreateTask, TaskAlreadyAssigned}
import com.productfoundry.akka.cqrs.TaskEvent.{TaskAssigned, TaskCreated}
import com.sun.corba.se.impl.activation.CommandHandler

case class TaskId(entityId: String) extends EntityId {
  override def toString: String = entityId
}

object TaskId {
  def generate = TaskId(UUID.randomUUID().toString)
}

trait TaskMessage extends AggregateMessage {
  type Id = TaskId
}

object TaskCommand {

  sealed trait TaskCommand extends TaskMessage with AggregateCommand

  case class CreateTask(id: TaskId, title: String) extends TaskCommand

  case class AssignTask(id: TaskId, assigneeId: UserId) extends TaskCommand

  trait TaskValidationMessage extends ValidationMessage

  case class TaskAlreadyAssigned(assigneeId: UserId) extends TaskValidationMessage

}

object TaskEvent {

  sealed trait TaskEvent extends TaskMessage with AggregateEvent

  case class TaskCreated(id: TaskId, creationTime: Long, title: String) extends TaskEvent

  case class TaskAssigned(id: TaskId, assigneeId: UserId) extends TaskEvent

}

class TaskAggregate(override val passivationConfig: PassivationConfig) extends Aggregate {

  override type S = TaskState

  override type M = TaskMessage

  override val messageClass = classOf[TaskMessage]

  override val factory: StateModifications = {
    case TaskCreated(_, _, _) => TaskState()
  }

  case class TaskState(assigneeIdOption: Option[UserId] = None) extends AggregateState {

    override def update: StateModifications = {
      case TaskAssigned(_, assigneeId) =>
        copy(assigneeIdOption = Some(assigneeId))
    }
  }

  override def handleCommand: CommandHandler = {

    case CreateTask(taskId, title) =>
      Right(Changes(TaskCreated(taskId, System.currentTimeMillis(), title)))

    case AssignTask(taskId, assigneeId) =>
      state.assigneeIdOption.map {
        assigneeId => Left(ValidationError(TaskAlreadyAssigned(assigneeId)))
      } getOrElse {
        Right(Changes(TaskAssigned(taskId, assigneeId)))
      }
  }
}
