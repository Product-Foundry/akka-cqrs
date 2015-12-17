package com.productfoundry.akka.cluster

import akka.actor.Props
import com.productfoundry.akka.PassivationConfig
import com.productfoundry.akka.cqrs.{AggregateStatus, AggregateFactory, AggregateIdResolution, EntityIdResolution}
import org.scalatest.concurrent.Eventually
import org.scalatest.time.{Seconds, Millis, Span}
import test.support.ClusterSpec
import test.support.ClusterConfig._

class TestAggregateSpecMultiJvmNode1 extends TestAggregateSpec
class TestAggregateSpecMultiJvmNode2 extends TestAggregateSpec

object TestActorFactory extends AggregateFactory[TestAggregate] {
  override def props(config: PassivationConfig): Props = {
    Props(classOf[TestAggregate], config)
  }
}

class TestAggregateSpec  extends ClusterSpec with Eventually {

  implicit def entityIdResolution: EntityIdResolution[TestAggregate] = new AggregateIdResolution[TestAggregate]()

  implicit def aggregateFactory: AggregateFactory[TestAggregate] = TestActorFactory

  implicit override val patienceConfig = PatienceConfig(
    timeout = scaled(Span(5, Seconds)),
    interval = scaled(Span(100, Millis))
  )

  "Test aggregate" must {

    "given cluster joined" in {
      setupSharedJournal()
      joinCluster()
    }

    enterBarrier("when")

    val entityContext = new ClusterSingletonEntityContext(system)

    "send all commands to same aggregate" in {

      def test(): Unit = {
        val aggregate = entityContext.entitySupervisorFactory[TestAggregate].getOrCreate
        val id = TestId("1")

        aggregate ! Count(id)
        expectMsgType[AggregateStatus.Success]

        eventually {
          aggregate ! GetCount(id)
          expectMsgType[GetCountResult].count shouldBe 2
        }
      }

      on(node1)(test())

      on(node2)(test())
    }
  }
}
