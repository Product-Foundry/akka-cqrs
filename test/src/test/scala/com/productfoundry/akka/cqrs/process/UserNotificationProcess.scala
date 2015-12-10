package com.productfoundry.akka.cqrs.process

import akka.actor.Props
import akka.util.Timeout
import com.productfoundry.akka.PassivationConfig
import com.productfoundry.akka.cqrs.EntityIdResolution.EntityIdResolver
import com.productfoundry.akka.cqrs.TaskEvent.TaskAssigned
import com.productfoundry.akka.cqrs.UserCommand.NotifyUser
import com.productfoundry.akka.cqrs._

case class UserNotificationProcessId(entityId: String) extends EntityId

object UserNotificationProcess extends ProcessManagerCompanion[UserNotificationProcess] {

  override def idResolution = new ProcessIdResolution[UserNotificationProcess] {

    override def processIdResolver: EntityIdResolver = {
      case TaskAssigned(taskId, assigneeId) => UserNotificationProcessId(s"$taskId-$assigneeId")
    }
  }

  def factory(aggregateRegistry: AggregateRegistry) =
    new ProcessManagerFactory[UserNotificationProcess] {
      override def props(config: PassivationConfig): Props = {
        Props(classOf[UserNotificationProcess], config, aggregateRegistry)
      }
    }
}

class UserNotificationProcess(val passivationConfig: PassivationConfig, aggregateRegistry: AggregateRegistry)
  extends SimpleProcessManager {

  override def receiveCommand: Receive = {
    case AggregateEventRecord(_, _, TaskAssigned(taskId, assigneeId)) =>
      aggregateRegistry[UserAggregate] ! NotifyUser(assigneeId, "New task assigned")
  }
}
