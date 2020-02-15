package net.bestia.zoneserver.script

import org.springframework.stereotype.Component
import java.lang.IllegalArgumentException
import javax.script.CompiledScript

@Component
class ScriptCache {
  private val cache = mutableMapOf<String, CompiledScript>()

  fun addScript(key: String, script: CompiledScript) {
    cache[key] = script
  }

  fun getScript(key: String): CompiledScript {
    return cache[key]
        ?: throw IllegalArgumentException("There is no compiled script with key '$key'")
  }

  companion object {
    const val RUNTIME_KEY = "runtime"
  }
}