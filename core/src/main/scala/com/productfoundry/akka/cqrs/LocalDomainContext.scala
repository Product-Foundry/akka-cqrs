package com.productfoundry.akka.cqrs

import akka.actor._
import akka.util.Timeout
import com.productfoundry.akka.cqrs.Entity.EntityId
import com.productfoundry.akka.{ActorContextCreationSupport, Passivate, PassivationConfig}

import scala.concurrent.Await
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
class LocalEntitySupervisor[E <: Entity](inactivityTimeout: Duration = 30.minutes)(implicit classTag: ClassTag[E], entityFactory: EntityFactory[E], entityIdResolution: EntityIdResolution[E])
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
      sender() ! stopMessage

    case Terminated(child) =>
      val childPath = child.path
      val bufferedMessages = bufferedMessagesByPath.getOrElse(childPath, Vector.empty)
      log.debug("Terminated: {}, buffered messages: {}", childPath, bufferedMessages.size)
      bufferedMessages.foreach { buffered =>
        getOrCreateEntity(resolveEntityId(buffered.message)).tell(buffered.message, buffered.sender)
      }

      // Remove all buffered messages for this actor, so it doesn't continue buffering when it is recreated
      bufferedMessagesByPath = bufferedMessagesByPath - childPath

    case msg: EntityMessage =>
      // Buffer messages when required
      val entityId = resolveEntityId(msg)
      val childPath = self.path / entityId
      val bufferedMessagesOption = bufferedMessagesByPath.get(childPath)
      bufferedMessagesOption match {
        case Some(bufferedMessages) =>
          val bufferedMessage = BufferedMessage(sender(), msg)
          log.debug("Buffered for: {}, message: {}", childPath, bufferedMessage)
          bufferedMessagesByPath = bufferedMessagesByPath.updated(childPath, bufferedMessages :+ bufferedMessage)
        case None =>
          getOrCreateEntity(entityId) forward msg
      }
  }

  private def resolveEntityId(msg: Any) = entityIdResolution.entityIdResolver(msg)

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
 * Creates only local entity supervisor factories.
 *
 * Can only be initialized once per actorRefFactory.
 *
 * @param actorRefFactory used to create entities supervisor factories.
 */
class LocalDomainContext(actorRefFactory: ActorRefFactory) extends DomainContext {

  private val entitySystemRef = actorRefFactory.actorOf(Props(new LocalDomainContextActor), "Domain")

  class LocalDomainContextActor extends Actor with ActorContextCreationSupport with ActorLogging {
    override def receive: Actor.Receive = {
      case GetOrCreateSupervisor(props, name) => sender() ! getOrCreateChild(props, name)
    }
  }

  case class GetOrCreateSupervisor(props: Props, name: String)

  override def entitySupervisorFactory[E <: Entity : EntityFactory : EntityIdResolution : ClassTag]: EntitySupervisorFactory[E] = {
    new EntitySupervisorFactory[E] {
      override def getOrCreate: ActorRef = {
        import akka.pattern.ask

        import scala.concurrent.duration._

        implicit val timeout = Timeout(30.seconds)

        val supervisorRefFuture = (entitySystemRef ? GetOrCreateSupervisor(Props(new LocalEntitySupervisor[E]), supervisorName)).mapTo[ActorRef]
        Await.result(supervisorRefFuture, timeout.duration)
      }
    }
  }
}
