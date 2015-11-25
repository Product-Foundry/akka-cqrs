package com.productfoundry.akka.cqrs.process

import com.productfoundry.akka.cqrs.TaskEvent.TaskAssigned
import com.productfoundry.akka.cqrs.UserCommand.NotifyUser
import com.productfoundry.akka.cqrs.UserEvent.UserCreated
import com.productfoundry.akka.cqrs.{UserId, TaskId, TestConfig}

class UserNotificationProcessSpec extends ProcessManagerSupport(TestConfig.system) {

  "User notification" must {

    "succeed" in new Fixture {

      val taskId = TaskId.generate
      val assigneeId = UserId.generate

      publishEvents(TaskAssigned(taskId, assigneeId))

      val notifyUser = commandReceiver.expectMsgType[NotifyUser]
      notifyUser.id shouldBe assigneeId
    }

    "ignore other event" in new Fixture {
      publishEvents(UserCreated(UserId.generate, "User"))

      commandReceiver.expectNoMsg()
    }
  }

  trait Fixture extends ProcessManagerFixture {

    processManagerRegistry.register(UserNotificationProcess.factory(aggregateRegistry))
  }
}