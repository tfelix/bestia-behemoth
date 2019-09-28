package net.bestia.zoneserver.script.env

import net.bestia.zoneserver.config.ZoneserverNodeConfig
import net.bestia.zoneserver.script.api.ScriptRootApi
import org.springframework.stereotype.Component

@Component
class GlobalEnv(
    private val api: ScriptRootApi,
    private val config: ZoneserverNodeConfig
) : ScriptEnv {
  override fun setupEnvironment(bindings: MutableMap<String, Any?>) {
    bindings["Bestia"] = api
    bindings["SERVER_VERSION"] = config.serverVersion
  }
}