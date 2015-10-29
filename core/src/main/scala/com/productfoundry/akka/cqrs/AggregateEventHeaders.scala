package com.productfoundry.akka.cqrs

/**
 * Contains additional info stored for an aggregate.
 *
 * @param metadata additional info.
 * @param timestamp of creation.
 */
case class AggregateEventHeaders(metadata: Map[String, String] = Map.empty,
                                 timestamp: Long = System.currentTimeMillis())
