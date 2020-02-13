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
  private val helperGlobalScriptResource = PathMatchingResourcePatternResolver().getResource("script/helper.js")

  init {
    // Add global java types
    // see: https://stackoverflow.com/questions/36968431/nashorn-how-to-pre-set-java-type-vars-inside-of-java-before-javascript-execut
    engine.put("Rect", StaticClass.forClass(Rect::class.java))
    engine.put("Vec3", StaticClass.forClass(Vec3::class.java))

    // Read the helper script and put into global scope improve this
    // FIXME does not work https://stackoverflow.com/questions/33620318/executing-a-function-in-a-specific-context-in-nashorn
    InputStreamReader(helperGlobalScriptResource.inputStream).use {
      engine.eval(it)
    }
  }

  override fun compile(fileResource: Resource): CompiledScript {
    LOG.debug { "Compiling script: ${fileResource.file.absolutePath}" }

    InputStreamReader(fileResource.inputStream).use {
      return (engine as Compilable).compile(it)
    }
  }
}