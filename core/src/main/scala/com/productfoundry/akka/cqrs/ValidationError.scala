package com.productfoundry.akka.cqrs

/**
 * Validation messages should be backed by case classes.
 */
trait ValidationMessage extends Serializable

/**
 * Exception indicating one or more domain validation failures.
 * @param messages for failed validations.
 */
case class ValidationError private (messages: Seq[ValidationMessage]) extends AggregateUpdateFailure

object ValidationError {
  def apply(message: ValidationMessage): ValidationError = ValidationError(Seq(message))

  def apply(message: ValidationMessage, messages: ValidationMessage*): ValidationError = ValidationError(message +: messages)
}
