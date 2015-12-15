package com.productfoundry.akka.cluster

import akka.actor._
import akka.cluster.singleton.{ClusterSingletonManager, ClusterSingletonManagerSettings, ClusterSingletonProxy, ClusterSingletonProxySettings}
import akka.util.Timeout
import com.productfoundry.akka.cqrs.EntityContextActor.GetOrCreateSupervisor
import com.productfoundry.akka.cqrs._

import scala.concurrent.Await
import scala.language.implicitConversions
import scala.reflect.ClassTag

/**
  * Entity context that uses a cluster singleton to handle all updates on a single node.
  */
class ClusterSingletonEntityContext(system: ActorSystem, actorName: String = "Domain") extends EntityContext {

  val singleton = system.actorOf(ClusterSingletonManager.props(
    singletonProps = Props[EntityContextActor],
    terminationMessage = PoisonPill,
    settings = ClusterSingletonManagerSettings(system)),
    name = actorName)

  val singletonProxy = system.actorOf(ClusterSingletonProxy.props(
    singletonManagerPath = s"/user/$actorName",
    settings = ClusterSingletonProxySettings(system)),
    name = s"${actorName}Proxy")

  override def entitySupervisorFactory[E <: Entity : EntityFactory : EntityIdResolution : ClassTag]: EntitySupervisorFactory[E] = {
    new EntitySupervisorFactory[E] {
      override def getOrCreate: ActorRef = {
        import akka.pattern.ask

        import scala.concurrent.duration._

        implicit val timeout = Timeout(30.seconds)

        val supervisorRefFuture = (singletonProxy ? GetOrCreateSupervisor(LocalEntitySupervisor.props(
          implicitly[ClassTag[E]],
          implicitly[EntityFactory[E]],
          implicitly[EntityIdResolution[E]]
        ), supervisorName)).mapTo[ActorRef]
        Await.result(supervisorRefFuture, timeout.duration)
      }
    }
  }
}
