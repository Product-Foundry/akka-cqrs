package com.productfoundry.akka.journal

import akka.persistence.journal.JournalSpec
import com.productfoundry.akka.InMemoryPluginSupport

class InMemoryJournalSpec extends JournalSpec(InMemoryPluginSupport.config)