package com.productfoundry.akka.cluster

import akka.actor.Props
import com.productfoundry.akka.PassivationConfig
import com.productfoundry.akka.cqrs.{AggregateFactory, AggregateIdResolution, EntityIdResolution}
import test.support.ClusterSpec

class TestAggregateSpecMultiJvmNode1 extends TestAggregateSpec
class TestAggregateSpecMultiJvmNode2 extends TestAggregateSpec

class TestAggregateSpec  extends ClusterSpec {

  import test.support.ClusterConfig._

  implicit object AccountActorFactory extends AggregateFactory[TestAggregate] {
    override def props(config: PassivationConfig): Props = {
      Props(classOf[TestAggregate], config)
    }
  }

  implicit def entityIdResolution: EntityIdResolution[TestAggregate] = new AggregateIdResolution[TestAggregate]()

  "Test aggregate" must {

    "given cluster joined" in {
      setupSharedJournal()
      joinCluster()
    }

    enterBarrier("when")

    val entityContext = new ClusterSingletonEntityContext(system)

    "send all commands to same account aggregate" in {
      on(node1) {
      }

      on(node2) {
      }
    }
  }
}
