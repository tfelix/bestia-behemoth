package net.bestia.zoneserver.script

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.junit.MockitoJUnitRunner
import javax.script.CompiledScript

@RunWith(MockitoJUnitRunner::class)
class ScriptCacheTest {

  @Mock
  lateinit var compiler: ScriptCompiler

  @Mock
  lateinit var scriptFileResolver: ScriptFileResolver

  private val requestedScriptName = "ibims1script.js"

  private lateinit var cache: ScriptCache

  @Before
  fun setup() {
    val compiledScript = mock<CompiledScript>()
    whenever(scriptFileResolver.getScriptInputStream(any())).thenReturn(mock())
    whenever(compiler.compileScript(any())).thenReturn(compiledScript)

    cache = ScriptCache(compiler, scriptFileResolver)
  }

  @Test
  fun `getScript() returns the compiled script`() {
    val cachedScript = cache.getScript(requestedScriptName)
    verify(scriptFileResolver).getScriptInputStream(requestedScriptName)
    val cachedScript2 = cache.getScript(requestedScriptName)
    Assert.assertEquals("Script was not cached.", cachedScript, cachedScript2)
  }
}