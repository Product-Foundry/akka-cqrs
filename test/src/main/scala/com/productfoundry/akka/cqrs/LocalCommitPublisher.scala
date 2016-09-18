package com.productfoundry.akka.cqrs

import akka.serialization.{SerializationExtension, SerializerWithStringManifest}

import scala.util.Try

/**
 * Mix in with aggregates to publishes generated commits for testing purposes.
 *
 * Makes it easy to see what happened in an aggregate in combination with the [[LocalCommitCollector]].
 */
trait LocalCommitPublisher extends CommitHandler {
  this: Aggregate =>

  lazy val serialization = SerializationExtension(context.system)

  /**
   * Can be overridden by commit handlers mixins to add additional commit behavior.
   * @param commit to handle.
   * @param response which can be manipulated by additional commit handlers.
   * @return Updated response.
   */
  override def handleCommit(commit: Commit, response: AggregateResponse): AggregateResponse = {
    val handleAttempt = for {
      serializer <- Try(serialization.findSerializerFor(commit).asInstanceOf[SerializerWithStringManifest])
      bytes <- Try(serializer.toBinary(commit))
      deserialized <- Try(serializer.fromBinary(bytes, serializer.manifest(commit)).asInstanceOf[Commit])
      validated <- Try (if (deserialized == commit) deserialized else throw new IllegalStateException(s"expected: $commit; actual: $deserialized"))
    } yield {
      context.system.eventStream.publish(validated)
    }

    handleAttempt.recover {
      case e => log.error(e, "Handling commit")
    }

    response
  }
}
