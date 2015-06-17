package com.productfoundry.akka.cqrs

trait ValidationMessage

/**
 * Exception indicating one or more domain validation failures.
 * @param messages for failed validations.
 */
case class ValidationError private (messages: Seq[ValidationMessage]) extends DomainError

object ValidationError {
  def apply(message: ValidationMessage): ValidationError = ValidationError(Seq(message))

  def apply(message: ValidationMessage, messages: ValidationMessage*): ValidationError = ValidationError(message +: messages)
}
