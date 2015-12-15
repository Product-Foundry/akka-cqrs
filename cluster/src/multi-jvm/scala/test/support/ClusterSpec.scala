package test.support

import java.io.{File, IOException}
import java.nio.file._
import java.nio.file.attribute.BasicFileAttributes

import akka.actor.{ActorIdentity, Identify, Props}
import akka.cluster.Cluster
import akka.persistence.Persistence
import akka.persistence.journal.leveldb.{SharedLeveldbJournal, SharedLeveldbStore}
import akka.remote.testconductor.RoleName
import akka.remote.testkit.MultiNodeSpec
import akka.testkit.ImplicitSender

import scala.concurrent.duration._
import scala.reflect.ClassTag
import scala.util.control.NonFatal

abstract class ClusterSpec
  extends MultiNodeSpec(ClusterConfig)
  with SbtMultiNodeSpec
  with ImplicitSender {

  import ClusterConfig._

  implicit val logger = system.log

  def initialParticipants = roles.size

  def deleteDirectory(path: Path): Unit = if (Files.exists(path)) {

    Files.walkFileTree(path, new SimpleFileVisitor[Path] {

      def deleteAndContinue(file: Path): FileVisitResult = {
        Files.delete(file)
        FileVisitResult.CONTINUE
      }

      override def visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult = deleteAndContinue(file)

      override def visitFileFailed(file: Path, exc: IOException): FileVisitResult =  deleteAndContinue(file)

      override def postVisitDirectory(dir: Path, exc: IOException): FileVisitResult = {
        Option(exc).fold(deleteAndContinue(dir)) {
          case NonFatal(e) => throw e
        }
      }
    })
  }

  val storageLocations = List(
    "akka.persistence.journal.leveldb.dir",
    "akka.persistence.journal.leveldb-shared.store.dir",
    "akka.persistence.snapshot-store.local.dir").map(s => new File(system.settings.config.getString(s)))

  override protected def atStartup() {
    on(node1) {
      storageLocations.foreach(dir => deleteDirectory(dir.toPath))
    }
  }

  override protected def afterTermination() {
    on(node1) {
      storageLocations.foreach(dir => deleteDirectory(dir.toPath))
    }
  }

  def join(startOn: RoleName, joinTo: RoleName) {
    on(startOn) {
      Cluster(system) join node(joinTo).address
    }
    enterBarrier(startOn.name + "-joined")
  }

  def setupSharedJournal() {
    Persistence(system)
    on(node1) {
      system.actorOf(Props[SharedLeveldbStore], "store")
    }
    enterBarrier("persistence-started")

    system.actorSelection(node(node1) / "user" / "store") ! Identify(None)
    val sharedStore = expectMsgType[ActorIdentity].ref.get
    SharedLeveldbJournal.setStore(sharedStore, system)

    enterBarrier("after-1")
  }

  def joinCluster() {
    join(startOn = node1, joinTo = node1)
    join(startOn = node2, joinTo = node1)
    enterBarrier("after-2")
  }

  def on(nodes: RoleName*)(thunk: ⇒ Unit): Unit = {
    runOn(nodes: _*)(thunk)
  }

  def expectReply[T](obj: T) {
    expectMsg(20.seconds, obj)
  }

  def expectReply[T](implicit tag: ClassTag[T]) {
    expectMsgClass(20.seconds, tag.runtimeClass)
  }
}
