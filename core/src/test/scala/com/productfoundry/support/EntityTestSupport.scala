package com.productfoundry.support

import com.productfoundry.akka.cqrs.LocalDomainContext

abstract class EntityTestSupport extends PersistenceTestSupport {

  implicit val domainContext = new LocalDomainContext(system)
}
