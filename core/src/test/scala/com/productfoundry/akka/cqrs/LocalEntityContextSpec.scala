package com.productfoundry.akka.cqrs

import akka.actor._
import akka.testkit.TestProbe
import com.productfoundry.akka.{Passivate, PassivationConfig}
import com.productfoundry.akka.cqrs.DummyAggregate._
import com.productfoundry.support.AggregateTestSupport

class LocalEntityContextSpec extends AggregateTestSupport {

  implicit object DummyAggregateFactory extends AggregateFactory[DummyAggregate] {
    override def props(config: PassivationConfig): Props = {
      Props(new DummyAggregate(config))
    }
  }

  implicit val supervisorFactory = entityContext.entitySupervisorFactory[DummyAggregate]

  val supervisor: ActorRef = EntitySupervisor.forType[DummyAggregate]

  "Aggregate passivation" must {

    "succeed" in new AggregateFixture {
      val probe = TestProbe()

      supervisor ! Create(testId)
      expectMsgType[AggregateStatus.Success]

      probe.watch(lastSender)
      lastSender ! Passivate
      probe.expectMsgType[Terminated]

      Thread.sleep(1000)

      supervisor ! Count(testId)
      expectMsgType[AggregateStatus.Success]
    }
  }

  trait AggregateFixture {
    val testId = DummyId.generate()
  }
}
