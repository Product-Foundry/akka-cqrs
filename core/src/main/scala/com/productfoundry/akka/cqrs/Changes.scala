package com.productfoundry.akka.cqrs

/**
 * Represents the changes that can be committed atomically to the aggregate.
 */
sealed trait Changes[+E <: AggregateEvent] {

  /**
   * @return changes to apply the aggregate state.
   */
  def events: Seq[E]

  /**
   * @return additional commit info
   */
  def headers: Map[String, String]

  /**
   * Add additional headers.
   *
   * @param headers to add.
   * @return updated headers.
   */
  def withHeaders(headers: (String, String)*): Changes[E]
}

/**
 * Changes companion.
 */
object Changes {

  /**
   * Create changes.
   * @param events changes to apply the aggregate state.
   * @tparam E Base type of events in the changes.
   * @return changes.
   */
  def apply[E <: AggregateEvent](events: E*): Changes[E] = AggregateChanges(events)
}

private[this] case class AggregateChanges[E <: AggregateEvent](events: Seq[E], headers: Map[String, String] = Map.empty) extends Changes[E] {
  override def withHeaders(headers: (String, String)*) = copy(headers = this.headers ++ headers)
}
