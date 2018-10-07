package net.bestia.zoneserver.script

import org.junit.Assert
import org.junit.BeforeClass
import org.junit.Test
import java.io.File
import java.io.InputStream
import java.nio.file.Files

class FilesystemScriptFileResolverTest {

  @Test
  fun getScriptFile_withoutJsEnding() {
    val resolver = FilesystemScriptFileResolver(basePath)
    val input = resolver.getScriptInputStream(scriptFilenameWithoutExt)
    assertValidInputStream(input)
  }

  @Test
  fun getScriptFile_withJsEnding() {
    val resolver = FilesystemScriptFileResolver(basePath)
    val input = resolver.getScriptInputStream(scriptFilename)
    assertValidInputStream(input)
  }

  private fun assertValidInputStream(stream: InputStream) {
    val data = stream.bufferedReader().use { it.readText() }
    Assert.assertEquals(SCRIPTFILE_CONTENT, data)
  }

  companion object {
    private const val SCRIPTFILE_CONTENT = "HELLOWORLD"

    private lateinit var scriptFile: File
    private lateinit var scriptFilename: String
    private lateinit var scriptFilenameWithoutExt: String
    private lateinit var basePath: String

    @BeforeClass
    @JvmStatic
    fun beforeClass() {
      scriptFile = Files.createTempFile("bestia-filesystemscriptresolver-test", ".js").toFile()
      scriptFile.deleteOnExit()
      scriptFile.writeText(SCRIPTFILE_CONTENT)
      basePath = scriptFile.parent
      scriptFilename = scriptFile.name
      scriptFilenameWithoutExt = scriptFile.nameWithoutExtension
    }
  }
}