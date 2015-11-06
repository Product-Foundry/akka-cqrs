package com.productfoundry.akka.cqrs

import akka.actor.Props
import com.productfoundry.akka.PassivationConfig
import com.productfoundry.akka.cqrs.UserAggregateSpec._
import com.productfoundry.akka.cqrs.UserCommand.CreateUser
import com.productfoundry.akka.cqrs.UserEvent.UserCreated

object UserAggregateSpec {

  implicit object UserAggregateActorFactory extends AggregateFactory[UserAggregate] {
    override def props(config: PassivationConfig): Props = {
      Props(new UserAggregate(config) with LocalCommitPublisher)
    }
  }
}

class UserAggregateSpec extends AggregateSupport[UserAggregate](TestConfig.system) {

  "create user" must {

    "succeed" in new UserFixture {
      command(CreateUser(userId, "name"))
      expectEvent(UserCreated(userId, "name"))
    }
  }

  trait UserFixture extends AggregateFixture {

    val userId = UserId.generate
  }
}
