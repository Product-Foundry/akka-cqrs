package com.productfoundry.akka.cqrs.publish

import com.productfoundry.akka.serialization.Persistable

case class ReliableEventPublisherSnapshot(currentPublicationOption: Option[EventPublication],
                                          pendingPublications: Vector[EventPublication]) extends Persistable
