package net.bestia.zoneserver.script

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.core.io.support.PathMatchingResourcePatternResolver

internal class JavascriptScriptCompilerTest {

  private val resolver = PathMatchingResourcePatternResolver()
  private val sut = JavascriptScriptCompiler()

  @Test
  fun `can compile files picked from classpath`() {
    val resource = resolver.getResource("script/JavascriptScriptCompilerTest.js")

    val compiled = sut.compile(resource)
    val returned = compiled.eval()

    assertEquals("Hello World", returned)
  }
}