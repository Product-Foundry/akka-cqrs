package com.productfoundry.support

import com.productfoundry.akka.cqrs.LocalDomainContext

abstract class AggregateTestSupport extends PersistenceTestSupport {

  implicit val domainContext = new LocalDomainContext(system)
}
