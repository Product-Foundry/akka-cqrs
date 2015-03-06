package com.productfoundry.akka.cqrs

import com.productfoundry.akka.cqrs.TestAggregate.{Counted, Created, TestEvent}
import org.scalatest.{Matchers, WordSpecLike}
import play.api.libs.json.Json

class JsonSerializationSpec extends WordSpecLike with Matchers {

  "Json serialization" must {

    "read and write AggregateId" in {
      val id = AggregateId.generate()
      assert(Json.fromJson[AggregateId](Json.toJson(id)).get === id)
    }

    "read and write AggregateRevision" in {
      val revision = AggregateRevision.Initial
      assert(Json.fromJson[AggregateRevision](Json.toJson(revision)).get === revision)
    }

    "read and write DomainRevision" in {
      val revision = DomainRevision.Initial
      assert(Json.fromJson[DomainRevision](Json.toJson(revision)).get === revision)
    }

    "read and write commit" in {
      type SerialType = Commit[TestEvent]

      val aggregateId = AggregateId.generate()
      val events = Seq(Created(aggregateId), Counted(aggregateId, 1))
      val commit = Commit(aggregateId, AggregateRevision.Initial, System.currentTimeMillis(), events, Map("faa" -> "bor"))
      val jsValue = Json.toJson[SerialType](commit)
      assert(Json.fromJson[SerialType](jsValue).get === commit)
    }

    "read and write domain commit" in {
      type SerialType = DomainCommit[TestEvent]

      val aggregateId = AggregateId.generate()
      val commit = DomainCommit(DomainRevision.Initial, System.currentTimeMillis(), Commit(aggregateId, AggregateRevision.Initial, System.currentTimeMillis(), Seq(Created(aggregateId))))
      val jsValue = Json.toJson[SerialType](commit)
      assert(Json.fromJson[SerialType](jsValue).get === commit)
    }
  }
}
