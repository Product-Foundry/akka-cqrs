package test.support

import akka.remote.testkit.MultiNodeConfig
import com.typesafe.config.ConfigFactory

object ClusterConfig extends MultiNodeConfig {

  val node1 = role("node1")
  val node2 = role("node2")

  commonConfig(
    ConfigFactory.parseString(
      """
        akka {
          loglevel = ERROR
          loggers = ["akka.event.slf4j.Slf4jLogger"]

          actor {
            provider = "akka.cluster.ClusterActorRefProvider"
          }

          remote {
            log-remote-lifecycle-events = off
            netty.tcp {
              hostname = "127.0.0.1"
              port = 0
            }
          }

          cluster {
            seed-nodes = [
              "akka.tcp://ClusterSystem@127.0.0.1:2551",
              "akka.tcp://ClusterSystem@127.0.0.1:2552"]

            auto-down-unreachable-after = 20s
          }

          persistence {
            journal.plugin = "akka.persistence.journal.leveldb-shared"
            journal.leveldb-shared.store {
              native = off
              dir = "target/test-shared-journal"
            }
            snapshot-store.local.dir = "target/test-snapshots"
          }
        }
      """
    ).withFallback(ConfigFactory.load()))
}
