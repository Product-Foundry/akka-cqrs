package com.productfoundry.akka.journal

import akka.persistence.CapabilityFlag
import akka.persistence.journal.JournalSpec
import com.productfoundry.akka.InMemoryPluginSupport

class InMemoryJournalSpec extends JournalSpec(InMemoryPluginSupport.config) {

  override protected def supportsRejectingNonSerializableObjects: CapabilityFlag = CapabilityFlag.off()
}