package net.bestia.zoneserver.config

import akka.actor.ActorRef
import akka.pattern.Patterns
import net.bestia.zoneserver.actor.BQualifier.RUNTIME_CONFIG
import net.bestia.zoneserver.actor.config.GetRuntimeConfig
import net.bestia.zoneserver.actor.config.RuntimeConfigurationActor
import net.bestia.zoneserver.actor.routing.SystemMessageService
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import java.time.Duration

@Service
class RuntimeConfigService(
    private val systemMsgService: SystemMessageService,
    @Qualifier(RUNTIME_CONFIG)
    private val runtimeConfigActor: ActorRef
) {
  private val defaultTimeout = Duration.ofMillis(500)
  private var runtimeConfig: RuntimeConfig = RuntimeConfig()

  fun getRuntimeConfig(): RuntimeConfig {
    val response = Patterns.ask(runtimeConfigActor, GetRuntimeConfig, defaultTimeout)
    return response.toCompletableFuture().get() as RuntimeConfig
  }

  fun setRuntimeConfig(runtimeConfig: RuntimeConfig) {
    this.runtimeConfig = runtimeConfig
    val updateMessage = RuntimeConfigurationActor.SaveRuntimeConfig(runtimeConfig)
    systemMsgService.send(updateMessage)
  }
}