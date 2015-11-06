package com.productfoundry.akka.cqrs

import akka.actor.Props
import com.productfoundry.akka.PassivationConfig
import com.productfoundry.akka.cqrs.TaskAggregateSpec._
import com.productfoundry.akka.cqrs.TaskCommand.{AssignTask, CreateTask, TaskAlreadyAssigned}
import com.productfoundry.akka.cqrs.TaskEvent.{TaskAssigned, TaskCreated}
import com.productfoundry.akka.cqrs.UserEvent.UserCreated

object TaskAggregateSpec {

  implicit object TaskAggregateActorFactory extends AggregateFactory[TaskAggregate] {
    override def props(config: PassivationConfig): Props = {
      Props(new TaskAggregate(config) with LocalCommitPublisher)
    }
  }
}

class TaskAggregateSpec extends AggregateSupport[TaskAggregate](TestConfig.system) {

  "create task" must {

    "succeed" in new TaskFixture {
      command(CreateTask(taskId, "title"))
      expectEventPF {
        case TaskCreated(id, _, title) =>
          id shouldBe taskId
          title shouldBe "title"
      }
    }
  }

  "assign task" must {

    "succeed" in new TaskFixture {
      given(CreateTask(taskId, "title"))

      val assigneeId = UserId.generate

      command(AssignTask(taskId, assigneeId))
      expectEvent(TaskAssigned(taskId, assigneeId))
    }

    "fail when already assigned" in new TaskFixture {

      val assigneeId = UserId.generate

      given(
        CreateTask(taskId, "title"),
        AssignTask(taskId, assigneeId)
      )

      val status = command(AssignTask(taskId, UserId.generate))
      assertValidationError(TaskAlreadyAssigned(assigneeId), status)
    }
  }

  trait TaskFixture extends AggregateFixture {

    val taskId = TaskId.generate
  }
}
