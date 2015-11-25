package com.productfoundry.akka.cqrs.process

import com.productfoundry.akka.serialization.Persistable

case class DeduplicationEntry(deduplicationId: String) extends Persistable
