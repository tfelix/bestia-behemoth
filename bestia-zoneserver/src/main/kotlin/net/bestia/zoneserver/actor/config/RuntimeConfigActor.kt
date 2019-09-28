package net.bestia.zoneserver.actor.config

import akka.actor.AbstractActor
import akka.cluster.pubsub.DistributedPubSub
import akka.cluster.pubsub.DistributedPubSubMediator
import mu.KotlinLogging
import net.bestia.zoneserver.actor.Actor
import net.bestia.zoneserver.config.RuntimeConfig
import net.bestia.zoneserver.config.RuntimeConfigService

data class UpdateRuntimeConfig(
    val newConfig: RuntimeConfig
)

private val LOG = KotlinLogging.logger { }

@Actor
class RuntimeConfigActor(
    val runtimeConfigService: RuntimeConfigService
) : AbstractActor() {

  private val mediator = DistributedPubSub.get(context.system).mediator()

  init {
    // subscribe to the topic updating the runtime configs
    mediator.tell(DistributedPubSubMediator.Subscribe("topic", self), self)
  }

  override fun createReceive(): Receive {
    return receiveBuilder()
        .match(UpdateRuntimeConfig::class.java, this::sendUpdatedRuntimeConfigToCluster)
        .match(RuntimeConfig::class.java, this::receive)
        .match(DistributedPubSubMediator.SubscribeAck::class.java) {
          LOG.debug { "Subscribed to RuntimeConfig channel" }
        }
        .build()
  }

  private fun receive(config: RuntimeConfig) {
    runtimeConfigService.setConfigWithoutClusterUpdate(config)
  }

  private fun sendUpdatedRuntimeConfigToCluster(msg: UpdateRuntimeConfig) {
    mediator.tell(DistributedPubSubMediator.Publish("topic", msg.newConfig), self)
  }
}