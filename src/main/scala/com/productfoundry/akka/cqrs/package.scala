package com.productfoundry.akka

import scalaz._

package object cqrs {

  /**
   * Type alias for consistent validation based on Scalaz.
   */
  type DomainValidation[+A] = ValidationNel[ValidationMessage, A]

  /**
   * Alias for DomainValidation Failure type.
   */
  type ValidationMessages = NonEmptyList[ValidationMessage]

}
