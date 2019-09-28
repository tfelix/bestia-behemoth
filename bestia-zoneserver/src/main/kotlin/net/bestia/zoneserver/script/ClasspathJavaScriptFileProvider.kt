package net.bestia.zoneserver.script

import org.springframework.core.io.support.PathMatchingResourcePatternResolver
import org.springframework.stereotype.Component

@Component
class ClasspathJavaScriptFileProvider : ScriptFileProvider {
  private val resolver = PathMatchingResourcePatternResolver()

  private fun fetchItemScripts(): List<ScriptFile> {
    return resolver.getResources("classpath*:script/item/*.js").map {
      ScriptFile(
          ScriptKeyBuilder.getScriptKey(ScriptType.ITEM, it.file.nameWithoutExtension),
          it
      )
    }
  }

  /**
   * This approach might fail if we are inside a jar file. Maybe we need
   */
  private fun fetchAttackScripts(): List<ScriptFile> {
    return resolver.getResources("classpath*:script/attack/*.js").map {
      ScriptFile(
          ScriptKeyBuilder.getScriptKey(ScriptType.ATTACK, it.file.nameWithoutExtension),
          it
      )
    }
  }

  override fun iterator(): Iterator<ScriptFile> {
    val scriptFiles = fetchItemScripts() + fetchAttackScripts()
    return scriptFiles.iterator()
  }
}