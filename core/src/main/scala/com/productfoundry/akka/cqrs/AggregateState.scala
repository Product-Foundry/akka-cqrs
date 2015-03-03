package com.productfoundry.akka.cqrs

/**
 * Aggregate state factory.
 * @tparam E creates aggregate state.
 * @tparam S aggregated state.
 */
trait AggregateStateFactory[E, S <: AggregateState[E, S]] {

  def apply: PartialFunction[E, S]
}

/**
 * Aggregate state.
 * @tparam E updates aggregate state.
 * @tparam S aggregated state.
 */
trait AggregateState[E, S] {

  def update: PartialFunction[E, S]
}
