package com.productfoundry.akka.cqrs

import akka.actor.{ActorRef, ActorSystem, PoisonPill, Terminated}
import akka.testkit.{ImplicitSender, TestKit}
import akka.util.Timeout
import org.scalatest.concurrent.Eventually
import org.scalatest.time.{Millis, Second, Span}
import org.scalatest.{BeforeAndAfter, BeforeAndAfterAll, Matchers, WordSpecLike}

import scala.concurrent.Await
import scala.concurrent.duration._

abstract class EntitySupport(_system: ActorSystem)
  extends TestKit(_system)
  with ImplicitSender
  with WordSpecLike
  with Matchers
  with BeforeAndAfterAll
  with BeforeAndAfter
  with Eventually {

  /**
    * System should be fast, so for fast test runs check assertions frequently.
    */
  implicit override val patienceConfig = PatienceConfig(
    timeout = scaled(Span(1, Second)),
    interval = scaled(Span(10, Millis))
  )

  implicit val executionContext = system.dispatcher

  implicit val executionTimeout = Timeout(1.second)

  /**
    * Terminates specified actors and wait until termination is confirmed.
    * @param actors to terminate.
    */
  def terminateConfirmed(actors: ActorRef*): Unit = {
    actors.foreach { actor =>
      watch(actor)
      actor ! PoisonPill
      // wait until supervisor is terminated
      fishForMessage(1.seconds) {
        case Terminated(_) =>
          unwatch(actor)
          true
        case _ =>
          false
      }
    }
  }

  /**
    * Shut down the actor system after every suite.
    */
  override def afterAll(): Unit = {
    TestKit.shutdownActorSystem(system)
    Await.result(system.whenTerminated, executionTimeout.duration)
  }
}
