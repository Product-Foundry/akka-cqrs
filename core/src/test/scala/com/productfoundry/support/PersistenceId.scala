package com.productfoundry.support

import com.productfoundry.akka.cqrs._

case class PersistenceId(uuid: Uuid) extends Identifier

object PersistenceId extends IdentifierCompanion[PersistenceId]
