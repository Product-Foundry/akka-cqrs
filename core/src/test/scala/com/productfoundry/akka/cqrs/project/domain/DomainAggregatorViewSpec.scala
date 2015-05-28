package com.productfoundry.akka.cqrs.project.domain

import akka.actor.Props
import com.productfoundry.support.PersistenceTestSupport

class DomainAggregatorViewSpec extends PersistenceTestSupport {

  "Domain aggregator view" must {

  }

  trait fixture extends {
    val persistenceId = randomPersistenceId

    val domainAggregator = system.actorOf(Props(new DomainAggregator(persistenceId)))
  }
}
