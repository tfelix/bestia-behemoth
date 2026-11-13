package net.bestia.zone.item.script

import com.tngtech.archunit.core.importer.ClassFileImporter
import com.tngtech.archunit.core.importer.ImportOption
import io.mockk.mockk
import net.bestia.zone.message.OutMessageProcessor
import java.io.File
import java.lang.reflect.Modifier
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The client lists what a Potion Pitcher may throw from `is_potion` in its Item DB, and the server decides it with
 * [RestorativeScript.isPotion]. Nothing else keeps the two in step.
 */
class ClientPotionsTest {

  @Test
  fun `the client marks exactly the server's potions`() {
    assertEquals(serverPotions(), clientPotions(), "Set is_potion in bestia-client/src/Game/Item/DB to match")
  }

  private fun serverPotions(): Set<Long> {
    val messages = mockk<OutMessageProcessor>(relaxed = true)

    return ClassFileImporter().withImportOption(ImportOption.DoNotIncludeTests())
      .importPackages(RestorativeScript::class.java.packageName)
      .map { it.reflect() }
      .filter { RestorativeScript::class.java.isAssignableFrom(it) && !Modifier.isAbstract(it.modifiers) }
      .map { type ->
        val constructor = type.constructors.single()
        constructor.newInstance(*Array(constructor.parameterCount) { messages }) as RestorativeScript
      }
      .filter { it.isPotion }
      .map { it.itemId }
      .toSet()
  }

  private fun clientPotions(): Set<Long> {
    val itemDb = File(repoRoot(), "bestia-client/src/Game/Item/DB")
    val itemId = Regex("""^item_id\s*=\s*(\d+)""", RegexOption.MULTILINE)
    val potion = Regex("""^is_potion\s*=\s*true""", RegexOption.MULTILINE)

    return itemDb.listFiles { file -> file.extension == "tres" }!!
      .map { it.readText() }
      .filter { potion.containsMatchIn(it) }
      .map { itemId.find(it)!!.groupValues[1].toLong() }
      .toSet()
  }

  /** Walks up from the working directory, which Gradle sets to the module rather than to the checkout. */
  private fun repoRoot(): File {
    var dir: File? = File("").absoluteFile
    while (dir != null && !File(dir, "bestia-client").isDirectory) dir = dir.parentFile
    return dir ?: error("No checkout root above ${File("").absolutePath}")
  }
}
