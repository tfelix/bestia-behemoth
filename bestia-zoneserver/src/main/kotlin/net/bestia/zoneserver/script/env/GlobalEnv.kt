package net.bestia.zoneserver.script.env

import net.bestia.zoneserver.config.StaticConfig
import net.bestia.zoneserver.script.api.ScriptRootApi
import org.springframework.stereotype.Component

@Component
class GlobalEnv(
        private val api: ScriptRootApi,
        private val config: StaticConfig
) : ScriptEnv {
  override fun setupEnvironment(bindings: MutableMap<String, Any?>) {
    bindings["Bestia"] = api
    bindings["SERVER_VERSION"] = config.serverVersion
  }
}