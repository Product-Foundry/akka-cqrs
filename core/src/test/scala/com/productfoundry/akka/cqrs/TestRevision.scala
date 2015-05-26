package com.productfoundry.akka.cqrs

case class TestRevision(value: Long) extends Revision[TestRevision]

object TestRevision extends RevisionCompanion[TestRevision]
