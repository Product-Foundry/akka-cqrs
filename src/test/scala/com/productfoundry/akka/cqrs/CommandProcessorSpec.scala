package com.productfoundry.akka.cqrs

import akka.actor.Props
import akka.persistence.{PersistentView, Update}
import com.productfoundry.support.PersistentActorSpec
import org.specs2.matcher.Scope
import org.specs2.mock.Mockito

import scala.concurrent.stm.Ref

import scala.reflect.ClassTag
import scalaz._
import Scalaz._

class CommandProcessorSpec extends PersistentActorSpec with Mockito {

  sequential

  import CommandProcessorSpec._

  "Reply message" should {

    "be sent in case of success" in new WithCommandProcessor {
      commandProcessor ! AddValueWithReply(Some("value"))
      expectMsgSuccess[Values] must_== values
      values must containAllOf(Seq("value"))
    }

    "be sent in case of failure" in new WithCommandProcessor {
      commandProcessor ! AddValueWithReply(None)
      expectMsgFailure(NoValueSpecified())
      values must beEmpty
    }

    "not be sent in case of success" in new WithCommandProcessor {
      commandProcessor ! AddValueWithoutReply(Some("value"))

      values must containAllOf(Seq("value")).eventually
      expectNoMsg()
    }

    "not be sent in case of failure" in new WithCommandProcessor {
      commandProcessor ! AddValueWithoutReply(None)

      expectNoMsg()
      values must beEmpty
    }
  }

  "Events" should {

    "be persisted" in new WithCommandProcessor {
      commandProcessor ! AddValueWithReply(Some("1"))
      commandProcessor ! AddValueWithReply(Some("2"))
      commandProcessor ! AddValueWithReply(Some("3"))

      val view = system.actorOf(Props(new TestCommandProcessorView(persistenceId)))
      view ! Update

      def countResponse = {
        view ! TestCommandProcessorView.GetCount

        fishForMessage() {
          case TestCommandProcessorView.CountResponse(_) => true
          case _ => false
        }
      }

      countResponse must beEqualTo(TestCommandProcessorView.CountResponse(3)).eventually
    }
  }

  trait WithCommandProcessor extends Scope {

    val persistenceId = randomPersistenceId

    val valuesRef = Ref(Set.empty[String])

    def values = valuesRef.single.get

    val commandProcessor = system.actorOf(Props(new TestCommandProcessor(persistenceId, valuesRef)))
  }

  private def expectMsgSuccess[T](implicit t: ClassTag[T]) = {
    expectMsgType[Success[NonEmptyList[ValidationMessage], T]].a
  }

  private def expectMsgFailure(message: ValidationMessage, messages: ValidationMessage*) = {
    val validationMessages = expectMsgType[Failure[NonEmptyList[ValidationMessage], Any]].e
    validationMessages must_== NonEmptyList(message, messages: _*)
  }
}

object CommandProcessorSpec {

  type Values = Set[String]

  // Commands

  sealed trait TestCommand extends DomainCommand

  case class AddValueWithReply(valueOption: Option[String]) extends TestCommand

  case class AddValueWithoutReply(valueOption: Option[String]) extends TestCommand


  // Events

  sealed trait TestEvent extends DomainEvent

  case class ValueAddedEvent(value: String) extends TestEvent


  // Validation messages

  sealed trait TestValidationMessage extends ValidationMessage

  case class NoValueSpecified() extends TestValidationMessage

  // Test command processor

  class TestCommandProcessor(val persistenceId: String, valuesRef: Ref[Values]) extends CommandProcessor[TestEvent] {

    override def receiveCommand: Receive = {
      case AddValueWithReply(valueOption) =>
        commitSuccess {
          valueOption match {
            case Some(value) => Commit(ValueAddedEvent(value)).reply(valuesRef.single.get).successNel
            case None => NoValueSpecified().failureNel
          }
        }

      case AddValueWithoutReply(valueOption) =>
        commitSuccess {
          valueOption match {
            case Some(value) => Commit(ValueAddedEvent(value)).successNel
            case None => NoValueSpecified().failureNel
          }
        }
    }

    override def updateState(event: TestEvent): Unit = event match {
      case ValueAddedEvent(value) =>
        valuesRef.single.transform(_ + value)
    }
  }

  // Test view

  class TestCommandProcessorView(val persistenceId: String, val viewId: String = "testView") extends PersistentView {

    import TestCommandProcessorView._

    var count = 0

    override def receive: Receive = {
      case ValueAddedEvent(value) => count += 1

      case GetCount => sender ! CountResponse(count)
    }
  }

  object TestCommandProcessorView {

    case object GetCount

    case class CountResponse(count: Int)

  }
}