package com.productfoundry.akka.cqrs

import com.productfoundry.akka.serialization.Persistable

case class AggregateSnapshot(revision: AggregateRevision, snapshot: Any) extends Persistable
