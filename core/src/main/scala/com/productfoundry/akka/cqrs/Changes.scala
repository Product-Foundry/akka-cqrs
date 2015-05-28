package com.productfoundry.akka.cqrs

/**
 * Represents the changes that can be committed atomically to the aggregate.
 */
sealed trait Changes {

  /**
   * @return changes to apply the aggregate state.
   */
  def events: Seq[AggregateEvent]

  /**
   * @return additional commit info
   */
  def headers: Map[String, String]

  /**
   * @return payload for additional response.
   */
  def payload: Any

  /**
   * Sets payload.
   *
   * @param payload to set.
   * @return updated payload.
   */
  def withPayload(payload: Any): Changes

  /**
   * Add additional headers.
   *
   * @param headers to add.
   * @return updated headers.
   */
  def withHeaders(headers: (String, String)*): Changes
}

/**
 * Changes companion.
 */
object Changes {

  /**
   * Create changes.
   * @param events changes to apply the aggregate state.
   * @return changes.
   */
  def apply(events: AggregateEvent*): Changes = AggregateChanges(events)

  /**
   * Create changes from optional changes.
   * @param event change to apply the aggregate state.
   * @param eventOptions optional additional changes to apply the aggregate state.
   * @return changes.
   */
  def apply(event: AggregateEvent, eventOptions: Seq[Option[AggregateEvent]]): Changes = AggregateChanges(event +: eventOptions.flatten)
}

private[this] case class AggregateChanges(events: Seq[AggregateEvent], payload: Any = Unit, headers: Map[String, String] = Map.empty) extends Changes {

  override def withHeaders(headers: (String, String)*) = copy(headers = this.headers ++ headers)

  override def withPayload(payload: Any) = copy(payload = payload)
}
