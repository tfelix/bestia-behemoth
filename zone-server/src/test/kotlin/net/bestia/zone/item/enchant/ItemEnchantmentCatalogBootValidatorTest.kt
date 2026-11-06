package net.bestia.zone.item.enchant

import io.mockk.every
import io.mockk.mockk
import net.bestia.zone.boot.ItemEnchantmentImporterBootRunner
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ItemEnchantmentCatalogBootValidatorTest {

  private val everyScript = mockk<ItemEnchantmentScripts>().also { every { it.get(any()) } returns mockk() }

  @Test
  fun `the shipped item-enchantments-yml and ItemEnchantmentId agree`() {
    val catalog = ItemEnchantmentCatalog().apply { load(ItemEnchantmentImporterBootRunner.load()) }

    assertEquals(ItemEnchantmentId.entries.size, catalog.all().size)
    assertEquals("PostalSeal", catalog.getOrThrow(ItemEnchantmentId.POSTAL_SEAL).script)
    assertDoesNotThrow { ItemEnchantmentCatalogBootValidator(catalog, everyScript).validate() }
  }

  @Test
  fun `a constant with no catalog entry fails the boot`() {
    val catalog = ItemEnchantmentCatalog().apply { load(emptyList()) }

    val e = assertThrows<ItemEnchantmentCatalogMismatchException> {
      ItemEnchantmentCatalogBootValidator(catalog, everyScript).validate()
    }

    assertTrue(e.message!!.contains("POSTAL_SEAL"), "should name the constant: ${e.message}")
  }

  @Test
  fun `an unknown script fails the boot`() {
    val catalog = ItemEnchantmentCatalog().apply {
      load(listOf(ItemEnchantmentDefinition(1, "POSTAL_SEAL", script = "Nothing")))
    }
    val noScript = mockk<ItemEnchantmentScripts>().also { every { it.get(any()) } returns null }

    val e = assertThrows<ItemEnchantmentCatalogMismatchException> {
      ItemEnchantmentCatalogBootValidator(catalog, noScript).validate()
    }

    assertTrue(e.message!!.contains("Nothing"), "should name the script: ${e.message}")
  }
}
