package com.productfoundry.support

import com.productfoundry.akka.cqrs.LocalEntityContext

abstract class EntityTestSupport extends PersistenceTestSupport {

  implicit val entityContext = new LocalEntityContext(system)
}
