package com.productfoundry.akka.cluster

import akka.actor._
import akka.cluster.singleton.{ClusterSingletonManager, ClusterSingletonManagerSettings, ClusterSingletonProxy, ClusterSingletonProxySettings}
import akka.pattern.ask
import akka.util.Timeout
import com.productfoundry.akka.cqrs.EntityContextActor.GetOrCreateSupervisor
import com.productfoundry.akka.cqrs._

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.language.implicitConversions
import scala.reflect.ClassTag

/**
  * Entity context that uses a cluster singleton to handle all updates on a single node.
  */
class ClusterSingletonEntityContext(system: ActorSystem, actorName: String = "Domain-Cluster") extends EntityContext {

  val actor = singletonActor(Props[EntityContextActor], actorName)

  override def entitySupervisorFactory[E <: Entity : EntityFactory : EntityIdResolution : ClassTag]: EntitySupervisorFactory[E] = {
    new EntitySupervisorFactory[E] {
      override def getOrCreate: ActorRef = {

        implicit val timeout = Timeout(30.seconds)

        val supervisorRefFuture = (actor ? GetOrCreateSupervisor(LocalEntitySupervisor.props, supervisorName)).mapTo[ActorRef]
        Await.result(supervisorRefFuture, timeout.duration)
      }
    }
  }

  /**
    * TODO [AK] This should not be needed, there is some flaw in the context design related to process managers
    */
  override def singletonActor(props: Props, name: String): ActorRef = {
    val singleton = system.actorOf(ClusterSingletonManager.props(
      singletonProps = props,
      terminationMessage = PoisonPill,
      settings = ClusterSingletonManagerSettings(system)),
      name = name)

    system.actorOf(ClusterSingletonProxy.props(
      singletonManagerPath = singleton.path.toStringWithoutAddress,
      settings = ClusterSingletonProxySettings(system)),
      name = s"$name-Proxy")
  }

  /**
    * TODO [AK] This should not be needed, there is some logical flaw in the context design related to context
    */
  override val localContext: EntityContext = new LocalEntityContext(system)
}
