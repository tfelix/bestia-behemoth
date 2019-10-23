package net.bestia.zoneserver.actor.config

import akka.actor.AbstractActor
import java.util.Optional
import akka.cluster.ddata.*
import mu.KotlinLogging
import net.bestia.zoneserver.actor.Actor
import net.bestia.zoneserver.actor.routing.DynamicMessageRoutingActor
import net.bestia.zoneserver.config.RuntimeConfig
import java.time.Duration
import java.util.function.Function

private val LOG = KotlinLogging.logger { }

@Actor
class RuntimeConfigurationActor : AbstractActor() {
  data class SaveRuntimeConfig(
      val newConfig: RuntimeConfig
  )

  object GetRuntimeConfig

  private val replicator = DistributedData.get(context.system).replicator()
  private val node = DistributedData.get(context.system).selfUniqueAddress()

  override fun createReceive(): Receive {
    return receiveBuilder()
        .match(SaveRuntimeConfig::class.java, this::sendUpdatedRuntimeConfigToCluster)
        .build()
  }

  private fun sendUpdatedRuntimeConfigToCluster(msg: SaveRuntimeConfig) {
    LOG.debug { "Saving config: $msg" }
    val register = LWWRegister.create(node, msg.newConfig)
    val key = LWWRegisterKey.create<RuntimeConfig>(KEY)
    val modifyFn = Function<LWWRegister<RuntimeConfig>, LWWRegister<RuntimeConfig>> { it.merge(register) }
    val reqContext = Optional.empty<Any>()
    val writeTwo = Replicator.WriteTo(2, Duration.ofSeconds(3))
    val updateMsg = Replicator.Update(
        key,
        register,
        writeTwo,
        reqContext,
        modifyFn
    )
    replicator.tell(updateMsg, self)
  }

  companion object {
    private const val KEY = "runtimeConfig"
  }
}