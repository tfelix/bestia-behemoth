package net.bestia.zoneserver.script

import org.junit.Assert
import org.junit.jupiter.api.Test
import java.io.File

internal class ClasspathJavaScriptFileProviderTest {

  private val sut = ClasspathJavaScriptFileProvider()

  @Test
  fun `certain test scripts are picked when discovered`() {
    val testScripts = setOf(
        "attack/fire_pillar",
        "item/apple"
    )

    val foundScripts = sut.toList().map { fileNameWithParentDir(it.resource.file) }

    testScripts.forEach { script ->
      Assert.assertTrue("Does not contain '$script'", foundScripts.contains(script))
    }
  }

  private fun fileNameWithParentDir(file: File): String {
    return "${file.parentFile.name}/${file.nameWithoutExtension}"
  }
}