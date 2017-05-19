package com.productfoundry.akka.cqrs.snapshot

import java.util.UUID

import akka.actor.{ActorRef, Props}
import com.productfoundry.akka.PassivationConfig
import com.productfoundry.akka.cqrs._
import com.productfoundry.akka.cqrs.snapshot.AggregateSnapshotStrategyTestAggregate.GetSnapshotRequested
import com.productfoundry.support.AggregateTestSupport

import scala.concurrent.duration._

class AggregateSnapshotStrategyTestAggregate(val passivationConfig: PassivationConfig, listener: ActorRef)
  extends Aggregate
    with AggregateSnapshotSupport
    with AggregateSnapshotStrategy {

  override def snapshotInterval: Int = AggregateSnapshotStrategyTestAggregate.snapshotInterval

  override def snapshotDelay: FiniteDuration = 100.millis

  case class State() extends AggregateState {
    override def update: StateModifications = {
      case AggregateSnapshotStrategyTestAggregate.Updated(_) => this
    }
  }

  override def handleSnapshot: SnapshotHandler = {
    case _ => State()
  }

  override def getStateSnapshot: Option[AggregateStateSnapshot] = {
    listener ! AggregateSnapshotStrategyTestAggregate.GetSnapshotRequested(revision)
    None
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
    case AggregateSnapshotStrategyTestAggregate.Created(_) => State()
  }

  /**
    * Handles all aggregate commands.
    */
  override def handleCommand: CommandHandler = {
    case AggregateSnapshotStrategyTestAggregate.Create(id) =>
      Right(Changes(AggregateSnapshotStrategyTestAggregate.Created(id)))

    case AggregateSnapshotStrategyTestAggregate.Update(id) =>
      Right(Changes(AggregateSnapshotStrategyTestAggregate.Updated(id)))
  }
}

object AggregateSnapshotStrategyTestAggregate {

  val snapshotInterval: Int = 5

  case class AggregateId(entityId: String) extends EntityId

  object AggregateId {

    def generate(): AggregateId = AggregateId(UUID.randomUUID().toString)
  }

  trait CreateEventInUpdateAggregateMessage extends AggregateMessage {
    type Id = AggregateId
  }

  case class Create(id: AggregateId) extends AggregateCommand with CreateEventInUpdateAggregateMessage

  case class Created(id: AggregateId) extends AggregateEvent with CreateEventInUpdateAggregateMessage

  case class Update(id: AggregateId) extends AggregateCommand with CreateEventInUpdateAggregateMessage

  case class Updated(id: AggregateId) extends AggregateEvent with CreateEventInUpdateAggregateMessage

  case class GetSnapshotRequested(revision: AggregateRevision)

}


class AggregateSnapshotStrategySpec extends AggregateTestSupport {

  implicit object TestFactory extends AggregateFactory[AggregateSnapshotStrategyTestAggregate] {
    override def props(config: PassivationConfig): Props = {
      Props(new AggregateSnapshotStrategyTestAggregate(config, testActor))
    }
  }

  implicit val testFactory: EntitySupervisorFactory[AggregateSnapshotStrategyTestAggregate] = entityContext.entitySupervisorFactory[AggregateSnapshotStrategyTestAggregate]

  val supervisor: ActorRef = EntitySupervisor.forType[AggregateSnapshotStrategyTestAggregate]

  "Configured snapshot" must {

    "not snapshot before interval boundary" in {
      testConfiguredSnapshot(AggregateSnapshotStrategyTestAggregate.snapshotInterval - 1, expectSnapshot = false)
    }

    "snapshot on interval boundary" in {
      testConfiguredSnapshot(AggregateSnapshotStrategyTestAggregate.snapshotInterval, expectSnapshot = true)
    }

    "delay snapshot on new commits" in {
      testConfiguredSnapshot(AggregateSnapshotStrategyTestAggregate.snapshotInterval + 1, expectSnapshot = true)
    }

    "delay snapshot on crossing interval multiple times" in {
      testConfiguredSnapshot(AggregateSnapshotStrategyTestAggregate.snapshotInterval * 10, expectSnapshot = true)
    }
  }

  def testConfiguredSnapshot(count: Int, expectSnapshot: Boolean): Unit = {
    val id: AggregateSnapshotStrategyTestAggregate.AggregateId = AggregateSnapshotStrategyTestAggregate.AggregateId.generate()
    supervisor ! AggregateSnapshotStrategyTestAggregate.Create(id)
    expectMsgType[AggregateStatus.Success]

    (1 until count).foreach { _ =>
      supervisor ! AggregateSnapshotStrategyTestAggregate.Update(id)
      expectMsgType[AggregateStatus.Success]
    }

    if (expectSnapshot) {
      expectMsgType[GetSnapshotRequested].revision shouldBe AggregateRevision(count)
    }

    expectNoMsg()
  }
}
