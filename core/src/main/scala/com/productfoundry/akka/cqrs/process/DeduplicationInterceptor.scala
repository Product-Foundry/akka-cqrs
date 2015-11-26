package com.productfoundry.akka.cqrs.process

import akka.persistence.{PersistentActor, PersistentRepr}
import akka.productfoundry.contrib.pattern.ReceivePipeline
import akka.productfoundry.contrib.pattern.ReceivePipeline.{HandledCompletely, Inner}
import com.productfoundry.akka.cqrs.AggregateEventRecord

trait DeduplicationInterceptor {
  this: PersistentActor with ReceivePipeline =>

  private var deduplicationIds: Set[String] = Set.empty

  pipelineInner {
    case eventRecord: AggregateEventRecord =>

      val deduplicationId = eventRecord.tag.value

      if (!deduplicationIds.contains(deduplicationId)) {
        persist(DeduplicationEntry(deduplicationId)) { _ =>
          deduplicationIds = deduplicationIds + deduplicationId
        }
        Inner(eventRecord)
      } else {
        HandledCompletely
      }

    case PersistentRepr(DeduplicationEntry(deduplicationId), _) =>

      deduplicationIds = deduplicationIds + deduplicationId
      HandledCompletely
  }
}
