package com.productfoundry.akka.cqrs

import com.productfoundry.akka.cqrs.Entity.EntityId

/**
 * Event that is persisted and applied to an aggregate.
 *
 * Aggregate event records are perfect to expose outside the core system, since they contain the event with
 * all commit related data.
 *
 * @param tag of the aggregate after the event was applied.
 * @param headers with info about the aggregate related to the event.
 * @param event with the actual change.
 */
case class AggregateEventRecord(tag: AggregateTag, headers: AggregateEventHeaders, event: AggregateEvent) extends EntityMessage {

  /**
   * Used to publish events, for deduplication and handling it makes sense to base the id entirely on the tag,
   * which uniquely identifies the persisted record.
   */
  override def entityId: EntityId = tag.handle
}
