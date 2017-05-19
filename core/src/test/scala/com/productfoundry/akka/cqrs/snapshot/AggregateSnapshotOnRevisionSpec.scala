package com.productfoundry.akka.cqrs.snapshot

import java.util.UUID

import akka.actor.{ActorRef, Props}
import com.productfoundry.akka.PassivationConfig
import com.productfoundry.akka.cqrs._
import com.productfoundry.akka.cqrs.snapshot.AggregateSnapshotOnRevisionTestAggregate.GetSnapshotRequested
import com.productfoundry.support.AggregateTestSupport

import scala.concurrent.duration._

case class AggregateSnapshotOnRevisionTestAggregateSnapshot() extends AggregateStateSnapshot

class AggregateSnapshotOnRevisionTestAggregate(val passivationConfig: PassivationConfig, listener: ActorRef)
  extends Aggregate
    with AggregateSnapshotRecovery
    with AggregateSnapshotOnRevision {

  override def snapshotInterval: Int = AggregateSnapshotOnRevisionTestAggregate.snapshotInterval

  override def snapshotDelay: FiniteDuration = 100.millis

  case class State() extends AggregateState {
    override def update: StateModifications = {
      case AggregateSnapshotOnRevisionTestAggregate.Updated(_) => this
    }
  }

  /**
    * Aggregate state definition
    */
  override type S = State

  override type SnapshotType = AggregateSnapshotOnRevisionTestAggregateSnapshot

  /**
    * @return Class of aggregate messages that are supported to update this aggregate
    */
  override def messageClass: Class[AggregateMessage] = classOf[AggregateMessage]

  /**
    * Creates aggregate state.
    */
  override val factory: StateModifications = {
    case AggregateSnapshotOnRevisionTestAggregate.Created(_) => State()
  }

  /**
    * Handles all aggregate commands.
    */
  override def handleCommand: CommandHandler = {
    case AggregateSnapshotOnRevisionTestAggregate.Create(id) =>
      Right(Changes(AggregateSnapshotOnRevisionTestAggregate.Created(id)))

    case AggregateSnapshotOnRevisionTestAggregate.Update(id) =>
      Right(Changes(AggregateSnapshotOnRevisionTestAggregate.Updated(id)))
  }

  override def getStateSnapshot(state: State): AggregateSnapshotOnRevisionTestAggregateSnapshot = {
    listener ! AggregateSnapshotOnRevisionTestAggregate.GetSnapshotRequested(revision)
    AggregateSnapshotOnRevisionTestAggregateSnapshot()
  }

  override def getStateFromSnapshot(snapshot: AggregateSnapshotOnRevisionTestAggregateSnapshot): State = {
    State()
  }
}

object AggregateSnapshotOnRevisionTestAggregate {

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


class AggregateSnapshotOnRevisionSpec extends AggregateTestSupport {

  implicit object TestFactory extends AggregateFactory[AggregateSnapshotOnRevisionTestAggregate] {
    override def props(config: PassivationConfig): Props = {
      Props(new AggregateSnapshotOnRevisionTestAggregate(config, testActor))
    }
  }

  implicit val testFactory: EntitySupervisorFactory[AggregateSnapshotOnRevisionTestAggregate] = entityContext.entitySupervisorFactory[AggregateSnapshotOnRevisionTestAggregate]

  val supervisor: ActorRef = EntitySupervisor.forType[AggregateSnapshotOnRevisionTestAggregate]

  "Configured snapshot" must {

    "not snapshot before interval boundary" in {
      testConfiguredSnapshot(AggregateSnapshotOnRevisionTestAggregate.snapshotInterval - 1, expectSnapshot = false)
    }

    "snapshot on interval boundary" in {
      testConfiguredSnapshot(AggregateSnapshotOnRevisionTestAggregate.snapshotInterval, expectSnapshot = true)
    }

    "delay snapshot on new commits" in {
      testConfiguredSnapshot(AggregateSnapshotOnRevisionTestAggregate.snapshotInterval + 1, expectSnapshot = true)
    }

    "delay snapshot on crossing interval multiple times" in {
      testConfiguredSnapshot(AggregateSnapshotOnRevisionTestAggregate.snapshotInterval * 10, expectSnapshot = true)
    }

    "create multiple snapshots" in {
      val id: AggregateSnapshotOnRevisionTestAggregate.AggregateId = AggregateSnapshotOnRevisionTestAggregate.AggregateId.generate()
      supervisor ! AggregateSnapshotOnRevisionTestAggregate.Create(id)
      expectMsgType[AggregateStatus.Success]

      (1 until AggregateSnapshotOnRevisionTestAggregate.snapshotInterval).foreach { _ =>
        supervisor ! AggregateSnapshotOnRevisionTestAggregate.Update(id)
        expectMsgType[AggregateStatus.Success]
      }

      expectMsgType[GetSnapshotRequested].revision shouldBe AggregateRevision(AggregateSnapshotOnRevisionTestAggregate.snapshotInterval)

      (1 to AggregateSnapshotOnRevisionTestAggregate.snapshotInterval).foreach { _ =>
        supervisor ! AggregateSnapshotOnRevisionTestAggregate.Update(id)
        expectMsgType[AggregateStatus.Success]
      }

      expectMsgType[GetSnapshotRequested].revision shouldBe AggregateRevision(AggregateSnapshotOnRevisionTestAggregate.snapshotInterval * 2)

      expectNoMsg()
    }
  }

  def testConfiguredSnapshot(count: Int, expectSnapshot: Boolean): Unit = {
    val id: AggregateSnapshotOnRevisionTestAggregate.AggregateId = AggregateSnapshotOnRevisionTestAggregate.AggregateId.generate()
    supervisor ! AggregateSnapshotOnRevisionTestAggregate.Create(id)
    expectMsgType[AggregateStatus.Success]

    (1 until count).foreach { _ =>
      supervisor ! AggregateSnapshotOnRevisionTestAggregate.Update(id)
      expectMsgType[AggregateStatus.Success]
    }

    if (expectSnapshot) {
      expectMsgType[GetSnapshotRequested].revision shouldBe AggregateRevision(count)
    }

    expectNoMsg()
  }
}
