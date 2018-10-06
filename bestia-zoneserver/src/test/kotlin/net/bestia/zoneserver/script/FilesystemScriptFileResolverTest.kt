package net.bestia.zoneserver.script

import org.junit.Assert
import org.junit.Test

class FilesystemScriptFileResolverTest {

  @Test
  fun getGlobalHelperScriptFile() {
    val resolver = FilesystemScriptFileResolver("classpath:script")
    Assert.assertNotNull(resolver.globalScriptFile)
  }

  @Test
  fun getScriptFile_withoutJsEnding() {
    val resolver = FilesystemScriptFileResolver("classpath:script")
    val file = resolver.getScriptFile("scriptFileResolverTest")
    Assert.assertTrue(file.absolutePath.endsWith(".js"))
  }

  @Test
  fun getScriptFile_withJsEnding() {
    val resolver = FilesystemScriptFileResolver("classpath:script")
    val file = resolver.getScriptFile("scriptFileResolverTest.js")
    Assert.assertTrue(file.absolutePath.endsWith(".js"))
  }
}