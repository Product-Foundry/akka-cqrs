package com.productfoundry.akka.cqrs.process

import akka.actor.Props
import akka.util.Timeout
import com.productfoundry.akka.PassivationConfig
import com.productfoundry.akka.cqrs.EntityIdResolution.EntityIdResolver
import com.productfoundry.akka.cqrs.TaskEvent.TaskAssigned
import com.productfoundry.akka.cqrs.UserCommand.NotifyUser
import com.productfoundry.akka.cqrs._

import scala.concurrent.ExecutionContext

case class UserNotificationProcessId(entityId: String) extends EntityId

object UserNotificationProcess extends ProcessManagerCompanion[UserNotificationProcess] {

  override def idResolution = new ProcessIdResolution[UserNotificationProcess] {

    override def processIdResolver: EntityIdResolver = {
      case TaskAssigned(taskId, assigneeId) => UserNotificationProcessId(s"$taskId-$assigneeId")
    }
  }

  def factory(aggregateFactory: AggregateFactoryProvider)(implicit ec: ExecutionContext, timeout: Timeout) =
    new ProcessManagerFactory[UserNotificationProcess] {
      override def props(config: PassivationConfig): Props = {
        Props(new UserNotificationProcess(config, aggregateFactory))
      }
    }
}

class UserNotificationProcess(val passivationConfig: PassivationConfig, aggregateFactory: AggregateFactoryProvider)
                             (implicit ec: ExecutionContext, timeout: Timeout)
  extends ProcessManager {

  override def receiveEvent(tag: AggregateTag, headersOption: Option[CommitHeaders]): ReceiveEvent = {

    case TaskAssigned(taskId, assigneeId) =>
      aggregateFactory[UserAggregate] ! NotifyUser(assigneeId, "New task assigned")
  }
}
