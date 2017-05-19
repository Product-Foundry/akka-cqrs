package com.productfoundry.akka.cqrs

/**
  * Contains headers for a commit, which can be used for storing common event data like timestamps, users, etc.
  *
  * Users needs to define their own headers structure and ensure there is a proper Akka serializer configured.
  */
trait CommitHeaders
  extends EntityState
