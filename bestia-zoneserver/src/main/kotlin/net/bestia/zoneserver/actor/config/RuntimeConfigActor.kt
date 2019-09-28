package net.bestia.zoneserver.actor.config

import akka.cluster.pubsub.DistributedPubSub
import akka.cluster.pubsub.DistributedPubSubMediator
import mu.KotlinLogging
import net.bestia.zoneserver.actor.Actor
import net.bestia.zoneserver.actor.routing.DynamicMessageRoutingActor
import net.bestia.zoneserver.config.RuntimeConfig
import net.bestia.zoneserver.config.RuntimeConfigService

data class UpdateRuntimeConfig(
    val newConfig: RuntimeConfig
)

private val LOG = KotlinLogging.logger { }

@Actor
class RuntimeConfigActor(
    val runtimeConfigService: RuntimeConfigService
) : DynamicMessageRoutingActor() {

  override fun createReceive(builder: BuilderFacade) {
    builder
        .matchRedirect(UpdateRuntimeConfig::class.java, this::sendUpdatedRuntimeConfigToCluster)
        .match(RuntimeConfig::class.java, this::receive)
        .match(DistributedPubSubMediator.SubscribeAck::class.java) {
          LOG.debug { "Subscribed to RuntimeConfig channel" }
        }
  }

  private val mediator = DistributedPubSub.get(context.system).mediator()

  init {
    // subscribe to the topic updating the runtime configs
    mediator.tell(DistributedPubSubMediator.Subscribe(TOPIC_RUNTIME_UPDATE, self), self)
  }

  private fun receive(config: RuntimeConfig) {
    runtimeConfigService.setConfigWithoutClusterUpdate(config)
  }

  private fun sendUpdatedRuntimeConfigToCluster(msg: UpdateRuntimeConfig) {
    mediator.tell(DistributedPubSubMediator.Publish(TOPIC_RUNTIME_UPDATE, msg.newConfig), self)
  }

  companion object {
    const val TOPIC_RUNTIME_UPDATE = "runtimeConfigUpdate"
  }
}