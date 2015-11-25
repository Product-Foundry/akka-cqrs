package com.productfoundry.akka.cqrs.publish

import akka.productfoundry.contrib.pattern.ReceivePipeline
import ReceivePipeline.Inner
import akka.productfoundry.contrib.pattern.ReceivePipeline

trait EventPublicationInterceptor {
  this: ReceivePipeline ⇒

  pipelineInner {
    case publication: EventPublication =>
      publication.confirmIfRequested()
      Inner(publication.eventRecord)
  }
}
