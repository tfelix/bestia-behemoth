package net.bestia.zoneserver.script

import org.springframework.core.io.support.PathMatchingResourcePatternResolver
import org.springframework.stereotype.Component

@Component
class ClasspathJavaScriptFileProvider : ScriptFileProvider {
  private val resolver = PathMatchingResourcePatternResolver()

  private fun resolveClasspathSearch(pattern: String, scriptType: ScriptType): List<ScriptFile> {
    return resolver.getResources(pattern).map {
      ScriptFile(
          ScriptKeyBuilder.getScriptKey(scriptType, it.file.nameWithoutExtension),
          it
      )
    }
  }

  private fun fetchBasicScripts(): List<ScriptFile> {
    return resolveClasspathSearch("classpath*:script/*.js", ScriptType.BASIC)
  }

  private fun fetchItemScripts(): List<ScriptFile> {
    return resolveClasspathSearch("classpath*:script/item/*.js", ScriptType.ITEM)
  }

  private fun fetchAttackScripts(): List<ScriptFile> {
    return resolveClasspathSearch("classpath*:script/attack/*.js", ScriptType.ATTACK)
  }

  override fun iterator(): Iterator<ScriptFile> {
    val scriptFiles = fetchItemScripts() +
        fetchAttackScripts() +
        fetchBasicScripts()
    return scriptFiles.iterator()
  }
}