package net.bestia.zoneserver.script

import org.springframework.core.io.support.PathMatchingResourcePatternResolver
import org.springframework.stereotype.Component

@Component
class ClasspathJavaScriptFileProvider : ScriptFileProvider {
  private val keyBuilder = ScriptKeyBuilder()
  private val resolver = PathMatchingResourcePatternResolver()
  private val scriptFiles: List<ScriptFile>

  init {
    scriptFiles = fetchItemScripts()
  }

  private fun fetchItemScripts(): List<ScriptFile> {
    return resolver.getResources("classpath*:script/item/*.js").map {
      val file = it.file
      ScriptFile(
          keyBuilder.getScriptKey(ScriptType.ITEM, file.nameWithoutExtension),
          file
      )
    }
  }

  override fun iterator(): Iterator<ScriptFile> {
    return scriptFiles.iterator()
  }
}