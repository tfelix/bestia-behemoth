package net.bestia.zoneserver.config

import mu.KotlinLogging
import net.bestia.zoneserver.actor.config.UpdateRuntimeConfig
import net.bestia.zoneserver.actor.routing.SystemMessageService
import org.springframework.stereotype.Service

private val LOG = KotlinLogging.logger { }

@Service
class RuntimeConfigService(
    private val systemMsgService: SystemMessageService
) {
  private var runtimeConfig: RuntimeConfig = RuntimeConfig()

  fun getRuntimeConfig(): RuntimeConfig {
    return runtimeConfig
  }

  fun setConfigWithoutClusterUpdate(runtimeConfig: RuntimeConfig) {
    LOG.trace { "Runtime config set to: $runtimeConfig" }
    this.runtimeConfig = runtimeConfig
  }

  fun setConfigWithClusterUpdate(runtimeConfig: RuntimeConfig) {
    LOG.trace { "Runtime config set and cluster updated: $runtimeConfig" }
    this.runtimeConfig = runtimeConfig
    val updateMessage = UpdateRuntimeConfig(runtimeConfig)
    systemMsgService.send(updateMessage)
  }
}