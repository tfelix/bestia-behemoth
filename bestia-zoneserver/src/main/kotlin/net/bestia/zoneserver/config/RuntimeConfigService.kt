package net.bestia.zoneserver.config

import org.springframework.stereotype.Service

@Service
class RuntimeConfigService {
  private var runtimeConfig: RuntimeConfig = RuntimeConfig()

  fun setConfigWithoutClusterUpdate(runtimeConfig: RuntimeConfig) {
    this.runtimeConfig = runtimeConfig
  }

  fun setConfigWithClusterUpdate(runtimeConfig: RuntimeConfig) {
    this.runtimeConfig = runtimeConfig
    // TODO Send Message to Actor
  }
}