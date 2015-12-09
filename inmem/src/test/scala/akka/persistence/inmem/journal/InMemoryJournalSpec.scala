package akka.persistence.inmem.journal

import akka.persistence.CapabilityFlag
import akka.persistence.journal.JournalSpec
import akka.persistence.inmem.InMemoryPluginSupport

class InMemoryJournalSpec extends JournalSpec(InMemoryPluginSupport.config) {

  override protected def supportsRejectingNonSerializableObjects: CapabilityFlag = CapabilityFlag.off()
}