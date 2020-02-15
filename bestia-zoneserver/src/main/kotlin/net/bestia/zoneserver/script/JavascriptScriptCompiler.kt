package net.bestia.zoneserver.script

import mu.KotlinLogging
import org.springframework.core.io.Resource
import org.springframework.core.io.support.PathMatchingResourcePatternResolver
import org.springframework.stereotype.Service
import java.io.InputStreamReader
import jdk.internal.dynalink.beans.StaticClass
import net.bestia.model.geometry.Rect
import net.bestia.model.geometry.Vec3
import javax.script.*

private val LOG = KotlinLogging.logger { }

@Service
class JavascriptScriptCompiler : ScriptCompiler {
  private val engine: ScriptEngine = ScriptEngineManager().getEngineByName("nashorn")

  init {
    // Add global java types
    engine.put("Rect", StaticClass.forClass(Rect::class.java))
    engine.put("Vec3", StaticClass.forClass(Vec3::class.java))
  }

  override fun compile(fileResource: Resource): CompiledScript {
    LOG.debug { "Compiling script: ${fileResource.file.absolutePath}" }

    InputStreamReader(fileResource.inputStream).use {
      return (engine as Compilable).compile(it)
    }
  }
}