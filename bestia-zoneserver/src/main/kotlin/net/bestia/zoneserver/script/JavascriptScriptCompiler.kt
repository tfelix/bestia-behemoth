package net.bestia.zoneserver.script

import org.springframework.core.io.Resource
import org.springframework.stereotype.Service
import java.io.InputStreamReader
import javax.script.Compilable
import javax.script.CompiledScript
import javax.script.ScriptEngineManager

@Service
class JavascriptScriptCompiler : ScriptCompiler {
  private val engine = ScriptEngineManager().getEngineByName("nashorn")

  override fun compile(fileResource: Resource): CompiledScript {
    InputStreamReader(fileResource.inputStream).use {
      return (engine as Compilable).compile(it)
    }
  }
}