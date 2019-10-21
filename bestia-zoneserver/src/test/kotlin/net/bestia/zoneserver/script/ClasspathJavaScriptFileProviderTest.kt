package net.bestia.zoneserver.script

import org.junit.jupiter.api.Test
import org.springframework.test.util.AssertionErrors.assertTrue
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
      assertTrue("Does not contain '$script'", foundScripts.contains(script))
    }
  }

  private fun fileNameWithParentDir(file: File): String {
    return "${file.parentFile.name}/${file.nameWithoutExtension}"
  }
}