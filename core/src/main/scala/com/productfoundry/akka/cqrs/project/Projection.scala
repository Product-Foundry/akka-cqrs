package com.productfoundry.akka.cqrs.project

import com.productfoundry.akka.cqrs.Commit

/**
 * Defines a projection.
 *
 * @tparam R projection result type
 */
trait Projection[R] {

  /**
   * Projects a single commit.
   */
  def project(commit: Commit): R
}
