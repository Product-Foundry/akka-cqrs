package com.productfoundry.akka.cqrs

import java.util.UUID

import akka.actor.{ActorRef, Props, Status}
import com.productfoundry.akka.PassivationConfig
import com.productfoundry.support.AggregateTestSupport

trait CreateEventInUpdateAggregate extends Aggregate {

  case class State() extends AggregateState {
    override def update: StateModifications = {
      case _ => this
    }
  }

  /**
    * Aggregate state definition
    */
  override type S = State

  /**
    * @return Class of aggregate messages that are supported to update this aggregate
    */
  override def messageClass: Class[AggregateMessage] = classOf[AggregateMessage]

  /**
    * Creates aggregate state.
    */
  override val factory: StateModifications = {
    case CreateEventInUpdateAggregate.Created(_) => State()
  }

  /**
    * Handles all aggregate commands.
    */
  override def handleCommand: CommandHandler = {
    case CreateEventInUpdateAggregate.Create(id) =>
      Right(Changes(CreateEventInUpdateAggregate.Created(id)))
  }
}

object CreateEventInUpdateAggregate {

  case class AggregateId(entityId: String) extends EntityId

  object AggregateId {

    def generate(): AggregateId = AggregateId(UUID.randomUUID().toString)
  }

  trait CreateEventInUpdateAggregateMessage extends AggregateMessage {
    type Id = AggregateId
  }

  case class Create(id: AggregateId) extends AggregateCommand with CreateEventInUpdateAggregateMessage

  case class Created(id: AggregateId) extends AggregateEvent with CreateEventInUpdateAggregateMessage

}

class CreateEventInUpdateShouldFail(val passivationConfig: PassivationConfig) extends CreateEventInUpdateAggregate {

}

class CreateEventInUpdateShouldSucceed(val passivationConfig: PassivationConfig) extends CreateEventInUpdateAggregate {

  override def allowFactoryEventInUpdate: PartialFunction[AggregateEvent, Boolean] = {
    case _ => true
  }
}


class AggregateCreateSpec extends AggregateTestSupport {

  implicit object CreateEventInUpdateShouldFailFactory extends AggregateFactory[CreateEventInUpdateShouldFail] {
    override def props(config: PassivationConfig): Props = {
      Props(new CreateEventInUpdateShouldFail(config))
    }
  }

  implicit object CreateEventInUpdateShouldSucceedFactory extends AggregateFactory[CreateEventInUpdateShouldSucceed] {
    override def props(config: PassivationConfig): Props = {
      Props(new CreateEventInUpdateShouldSucceed(config))
    }
  }

  implicit val createEventInUpdateShouldFailFactory: EntitySupervisorFactory[CreateEventInUpdateShouldFail] = entityContext.entitySupervisorFactory[CreateEventInUpdateShouldFail]

  implicit val createEventInUpdateShouldSucceedFactory: EntitySupervisorFactory[CreateEventInUpdateShouldSucceed] = entityContext.entitySupervisorFactory[CreateEventInUpdateShouldSucceed]

  "Handling an event that creates an aggregate" must {

    "fail for second event by default" in {

      val supervisor: ActorRef = EntitySupervisor.forType[CreateEventInUpdateShouldFail]

      val id: CreateEventInUpdateAggregate.AggregateId = CreateEventInUpdateAggregate.AggregateId.generate()
      supervisor ! CreateEventInUpdateAggregate.Create(id)
      expectMsgType[AggregateStatus.Success]

      supervisor ! CreateEventInUpdateAggregate.Create(id)
      expectMsgType[Status.Failure].cause shouldBe AggregateAlreadyInitializedException(AggregateRevision(1L))
    }

    "succeed for second event when desired" in {

      val supervisor: ActorRef = EntitySupervisor.forType[CreateEventInUpdateShouldSucceed]

      val id: CreateEventInUpdateAggregate.AggregateId = CreateEventInUpdateAggregate.AggregateId.generate()
      supervisor ! CreateEventInUpdateAggregate.Create(id)
      expectMsgType[AggregateStatus.Success]

      supervisor ! CreateEventInUpdateAggregate.Create(id)
      expectMsgType[AggregateStatus.Success]
    }
  }
}
