package com.productfoundry.akka.cqrs.snapshot

import akka.persistence.AtLeastOnceDelivery.AtLeastOnceDeliverySnapshot
import com.productfoundry.akka.cqrs.AggregateRevision
import com.productfoundry.akka.cqrs.publish.ReliableEventPublisherSnapshot
import com.productfoundry.akka.serialization.Persistable

case class AggregateSnapshot(revision: AggregateRevision,
                             stateSnapshotOption: Option[AggregateStateSnapshot],
                             atLeastOnceDeliverySnapshotOption: Option[AtLeastOnceDeliverySnapshot],
                             reliableEventPublisherSnapshotOption: Option[ReliableEventPublisherSnapshot]) extends Persistable
