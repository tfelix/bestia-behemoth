package net.bestia.zoneserver.script

import org.junit.Assert
import org.junit.Test
import java.io.InputStream

class ClasspathScriptFileResolverTest {

  @Test
  fun `loader sanity test`() {
    val sanityTest = this.javaClass.getResourceAsStream("/script/scriptFileResolverTest.js")
    Assert.assertNotNull(sanityTest)
  }

  @Test
  fun getGlobalHelperScriptFile() {
    val resolver = ClasspathScriptFileResolver("classpath:script")
    val input = resolver.getScriptInputStream("helper")
    val data = input.bufferedReader().use { it.readText() }
    Assert.assertTrue(data.isNotEmpty())
  }

  @Test
  fun getScriptFile_withoutJsEnding() {
    val resolver = ClasspathScriptFileResolver("classpath:script")
    val input = resolver.getScriptInputStream("scriptFileResolverTest")
    assertValidInputStream(input)
  }

  @Test
  fun getScriptFile_withJsEnding() {
    val resolver = ClasspathScriptFileResolver("classpath:script")
    val input = resolver.getScriptInputStream("scriptFileResolverTest.js")
    assertValidInputStream(input)
  }

  private fun assertValidInputStream(stream: InputStream) {
    val data = stream.bufferedReader().use { it.readText() }
    Assert.assertEquals(SCRIPTFILE_CONTENT, data)
  }

  companion object {
    private const val SCRIPTFILE_CONTENT = "HELLOWORLD"
  }
}