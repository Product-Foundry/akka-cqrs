package com.productfoundry.akka.cqrs

import scala.language.implicitConversions
import scalaz._

/**
 * Mixin for Aggregates to implicitly create an Either for committing changes based on Scalaz validation.
 */
trait ScalazValidation {
  type DomainValidation = scalaz.ValidationNel[ValidationMessage, Changes]

  implicit def validationToEither(validation: DomainValidation): Either[ValidationError, Changes] = {
    validation match {
      case Success(changes) => Right(changes)
      case Failure(messages) => Left(ValidationError(messages.head, messages.tail: _*))
    }
  }
}
