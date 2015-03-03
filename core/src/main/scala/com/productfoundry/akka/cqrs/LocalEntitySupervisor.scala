package com.productfoundry.akka.cqrs

import akka.actor._
import com.productfoundry.akka.{ActorContextCreationSupport, Passivate, PassivationConfig}

import scala.concurrent.duration._
import scala.language.implicitConversions
import scala.reflect.ClassTag

/**
 * In-between receiving Passivate and Terminated the supervisor buffers all incoming messages for the entity being passivated.
 * @param sender of the message.
 * @param message to buffer.
 */
case class BufferedMessage(sender: ActorRef, message: EntityMessage)

/**
 * Supervises local entities.
 * @param inactivityTimeout before the entity passivates.
 * @param classTag of the entity.
 * @param entityFactory to create the entity.
 * @tparam E Entity type.
 */
class LocalEntitySupervisor[E <: Entity](commitAggregatorRef: ActorRef, inactivityTimeout: Duration = 30.minutes)(implicit classTag: ClassTag[E],
                                                                                                                  entityFactory: EntityFactory[E])
  extends ActorContextCreationSupport
  with Actor
  with ActorLogging {

  private var bufferedMessagesByPath: Map[ActorPath, Vector[BufferedMessage]] = Map.empty

  override def receive: Actor.Receive = {
    case Passivate(stopMessage) =>
      val childPath = sender().path
      log.debug("Passivating: {}", childPath)

      // Having a key in the buffered map causes all messages to buffered
      bufferedMessagesByPath = bufferedMessagesByPath.updated(childPath, Vector.empty)
      sender ! stopMessage

    case Terminated(child) =>
      val childPath = child.path
      val bufferedMessages = bufferedMessagesByPath.getOrElse(childPath, Vector.empty)
      log.debug("Terminated: {}, buffered messages: {}", childPath, bufferedMessages.size)
      bufferedMessages.foreach { buffered =>
        getOrCreateEntity(buffered.message.entityId).tell(buffered.message, buffered.sender)
      }

      // Remove all buffered messages for this actor, so it doesn't continue buffering when it is recreated
      bufferedMessagesByPath = bufferedMessagesByPath - childPath

    case GlobalAggregator.Get =>
      sender ! commitAggregatorRef

    case msg: EntityMessage =>
      // Buffer messages when required
      val childPath = self.path / msg.entityId.toString
      val bufferedMessagesOption = bufferedMessagesByPath.get(childPath)
      bufferedMessagesOption match {
        case Some(bufferedMessages) =>
          val bufferedMessage = BufferedMessage(sender(), msg)
          log.debug("Buffered for: {}, message: {}", childPath, bufferedMessage)
          bufferedMessagesByPath = bufferedMessagesByPath.updated(childPath, bufferedMessages :+ bufferedMessage)
        case None =>
          getOrCreateEntity(msg.entityId) forward msg
      }
  }

  /**
   * @param entityId of the entity.
   * @return Entity actor ref.
   */
  private def getOrCreateEntity(entityId: EntityId): ActorRef = {
    val props = entityFactory.props(PassivationConfig(PoisonPill, inactivityTimeout))
    getOrCreateChild(props, entityId.toString)
  }
}

/**
 * Creates only local entities.
 * @param system used to create entities.
 */
class LocalEntitySystem(implicit system: ActorSystem) extends EntitySystem {

  val commitAggregatorRef = system.actorOf(Props(new GlobalAggregator), "CommitAggregator")

  override def entitySupervisorFactory[E <: Entity : EntityFactory : ClassTag]: EntitySupervisorFactory[E] = {
    new EntitySupervisorFactory[E] {
      override def getOrCreate: ActorRef = {
        system.actorOf(Props(new LocalEntitySupervisor[E](commitAggregatorRef)), supervisorName)
      }
    }
  }
}
