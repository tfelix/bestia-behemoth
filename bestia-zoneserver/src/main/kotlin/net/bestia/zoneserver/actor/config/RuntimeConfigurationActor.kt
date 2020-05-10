package net.bestia.zoneserver.actor.config

import akka.actor.AbstractActor
import akka.actor.ActorRef
import java.util.Optional
import akka.cluster.ddata.*
import mu.KotlinLogging
import net.bestia.zoneserver.actor.Actor
import net.bestia.zoneserver.config.RuntimeConfig
import java.time.Duration
import java.util.function.Function

private val LOG = KotlinLogging.logger { }

object GetRuntimeConfig

@Actor
class RuntimeConfigurationActor : AbstractActor() {
  data class SaveRuntimeConfig(
      val newConfig: RuntimeConfig
  )

  private val key = LWWRegisterKey.create<RuntimeConfig>(KEY)
  private val writeTwo = Replicator.WriteTo(2, Duration.ofSeconds(3))
  private val replicator = DistributedData.get(context.system).replicator()
  private val node = DistributedData.get(context.system).selfUniqueAddress()

  override fun createReceive(): Receive {
    return receiveBuilder()
        .match(SaveRuntimeConfig::class.java, this::saveRuntimeConfig)
        .match(Replicator.GetSuccess::class.java, this::getRequestReturned)
        .match(Replicator.NotFound::class.java, this::notFoundReturned)
        .matchEquals(GetRuntimeConfig, { getRuntimeConfig(sender) })
        .build()
  }

  private fun notFoundReturned(msg: Replicator.NotFound<*>) {
    val defaultConfig = RuntimeConfig()
    saveRuntimeConfig(SaveRuntimeConfig(defaultConfig))

    @Suppress("UNCHECKED_CAST")
    val replyTo = msg.request as Optional<ActorRef>
    replyTo.ifPresent { getRuntimeConfig(it) }
  }

  private fun getRequestReturned(msg: Replicator.GetSuccess<*>) {
    @Suppress("UNCHECKED_CAST")
    msg as Replicator.GetSuccess<LWWRegister<RuntimeConfig>>
    val data = msg.dataValue().value

    @Suppress("UNCHECKED_CAST")
    val replyTo = msg.request.get() as ActorRef
    replyTo.tell(data, self)
  }

  private fun getRuntimeConfig(replyTo: ActorRef) {
    LOG.trace { "Get config" }
    @Suppress("UNCHECKED_CAST")
    val getMsg = Replicator.Get<LWWRegister<RuntimeConfig>>(key, Replicator.readLocal(), Optional.of(replyTo) as Optional<Any>)
    replicator.tell(getMsg, self)
  }

  private fun saveRuntimeConfig(msg: SaveRuntimeConfig) {
    LOG.trace { "Saving config: $msg" }
    val register = LWWRegister.create(node, msg.newConfig)
    val modifyFn = Function<LWWRegister<RuntimeConfig>, LWWRegister<RuntimeConfig>> { it.merge(register) }
    val reqContext = Optional.empty<Any>()
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