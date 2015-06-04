package com.productfoundry.support

import com.productfoundry.akka.cqrs.{AggregateIdResolution, EntityIdResolution, LocalDomainContext}

abstract class AggregateTestSupport extends PersistenceTestSupport {

  implicit def entityIdResolution[A]: EntityIdResolution[A] = new AggregateIdResolution[A]()

  implicit val domainContext = new LocalDomainContext(system)
}
