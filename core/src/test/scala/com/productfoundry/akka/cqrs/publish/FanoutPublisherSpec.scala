package com.productfoundry.akka.cqrs.publish

import akka.actor.Props
import akka.testkit.TestProbe
import com.productfoundry.akka.cqrs.ConfirmationProtocol.Confirm
import com.productfoundry.akka.cqrs.publish.FanoutPublisher.Subscribe
import com.productfoundry.akka.cqrs.{Confirmable, Fixtures, DummyConfirmable}
import com.productfoundry.support.PersistenceTestSupport
import org.scalatest.prop.GeneratorDrivenPropertyChecks

import scala.concurrent.duration._
import scala.util.Random

class FanoutPublisherSpec extends PersistenceTestSupport with GeneratorDrivenPropertyChecks with Fixtures {

  "publication" must {

    "succeed for single subscriber" in {
      forAll { confirmables: List[DummyConfirmable] =>
        val subject = system.actorOf(Props(new FanoutPublisher()))

        val subscriber = TestProbe()
        subject ! Subscribe(subscriber.ref.path)

        for (confirmable <- confirmables) {
          subject ! confirmable
          subscriber.expectMsgType[Confirmable]
        }
      }
    }

    "succeed for multiple subscribers" in {
      forAll { confirmables: List[DummyConfirmable] =>
        val subject = system.actorOf(Props(new FanoutPublisher()))

        val subscribers = 1 to 5 map { _ =>
          val subscriber = TestProbe()
          subject ! Subscribe(subscriber.ref.path)
          subscriber
        }

        for (confirmable <- confirmables) {
          subject ! confirmable

          for (subscriber <- subscribers) {
            subscriber.expectMsgType[Confirmable]
          }
        }
      }
    }
  }

  "confirmations" must {

    "be confirmed to sender when publications are confirmed" in {
      forAll { confirmables: List[DummyConfirmable] =>
        val subject = system.actorOf(Props(new FanoutPublisher()))

        val subscribers = 1 to 5 map { _ =>
          val subscriber = TestProbe()
          subject ! Subscribe(subscriber.ref.path)
          subscriber
        }

        for (confirmable <- confirmables) {
          val deliveryId = Random.nextLong()

          subject ! confirmable.requestConfirmation(deliveryId)

          for (subscriber <- subscribers) {
            subscriber.expectMsgType[Confirmable].confirmIfRequested()
          }

          expectMsgType[Confirm].deliveryId should be(deliveryId)
        }
      }
    }

    "be confirmed without subscribers" in {
      forAll { confirmable: DummyConfirmable =>
        val subject = system.actorOf(Props(new FanoutPublisher()))

        val deliveryId = Random.nextLong()
        subject ! confirmable.requestConfirmation(deliveryId)
        expectMsgType[Confirm].deliveryId should be(deliveryId)
      }
    }
  }

  "redeliver" must {

    "succeed when subscriber does not confirm" in {
      forAll { confirmable: DummyConfirmable =>
        val subject = system.actorOf(Props(new FanoutPublisher()))

        val subscribers = 1 to 10 map { _ =>
          val subscriber = TestProbe()
          subject ! Subscribe(subscriber.ref.path)
          subscriber
        }

        val deliveryId = Random.nextLong()
        val confirmableWithConfirmationRequest = confirmable.requestConfirmation(deliveryId)

        def confirmSome(remaining: Seq[TestProbe]): Seq[TestProbe] = {

          subject ! confirmableWithConfirmationRequest

          var unconfirmed: Seq[TestProbe] = Seq.empty

          for (subscriber <- remaining) {
            val c = subscriber.expectMsgType[Confirmable]
            if (Random.nextInt(2) % 2 == 0) {
              c.confirmIfRequested()
            } else {
              unconfirmed = unconfirmed :+ subscriber
            }
          }

          unconfirmed
        }

        var unconfirmed: Seq[TestProbe] = subscribers
        while (unconfirmed.nonEmpty) {
          unconfirmed = confirmSome(unconfirmed)
        }

        expectMsgType[Confirm].deliveryId should be(deliveryId)
        for (subscriber <- subscribers) {
          subscriber.expectNoMsg(1.millis)
        }
      }
    }
  }
}
