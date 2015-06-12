package com.productfoundry.support

import com.productfoundry.akka.cqrs.AggregateIdResolution

abstract class AggregateTestSupport extends EntityTestSupport {

  implicit def aggregateIdResolution[A]: AggregateIdResolution[A] = new AggregateIdResolution[A]()
}
