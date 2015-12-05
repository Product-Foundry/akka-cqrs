package com.productfoundry.akka.cqrs.process

import akka.persistence.PersistentActor
import akka.productfoundry.contrib.pattern.ReceivePipeline
import akka.productfoundry.contrib.pattern.ReceivePipeline.{HandledCompletely, Inner}
import com.productfoundry.akka.cqrs.AggregateEventRecord

trait DeduplicationInterceptor {
  this: PersistentActor with ReceivePipeline =>

  private var deduplicationIds: Set[String] = Set.empty

  pipelineInner {
    case eventRecord: AggregateEventRecord =>

      val deduplicationId = eventRecord.tag.value

      if (deduplicationIds.contains(deduplicationId)) {
        HandledCompletely
      } else {
        persist(DeduplicationEntry(deduplicationId))( _ => handled(deduplicationId))
        Inner(eventRecord)
      }
  }

  override def receiveRecover: Receive = {
    case DeduplicationEntry(deduplicationId) => handled(deduplicationId)
  }

  protected def handled(deduplicationId: String): Unit = {
    deduplicationIds += deduplicationId
  }
}
