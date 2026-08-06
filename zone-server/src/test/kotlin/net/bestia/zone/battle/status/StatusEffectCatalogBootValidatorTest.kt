package net.bestia.zone.battle.status

import net.bestia.zone.boot.StatusEffectImporterBootRunner
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows

class StatusEffectCatalogBootValidatorTest {

  /**
   * Runs the real importer over the real `status_effects.yml`, so adding an effect to one side and
   * forgetting the other fails here rather than at server boot.
   */
  @Test
  fun `the shipped status-effects-yml and StatusEffectId agree`() {
    val registry = loadShippedCatalog()

    assertEquals(
      StatusEffectId.entries.size,
      registry.all().size,
      "one status_effects.yml entry per StatusEffectId constant"
    )
    assertDoesNotThrow { StatusEffectCatalogBootValidator(registry).validateStatusEffectCatalog() }
  }

  /**
   * Every constant must resolve to a definition whose script the [StatusEffectScriptRegistry] can
   * key on. Only the name is checked here (the bean side is a Spring concern), but a renamed script
   * class that nobody updated in the yml still shows up as a mismatch at boot.
   */
  @Test
  fun `the shipped status-effects-yml parses script and client sync per constant`() {
    val registry = loadShippedCatalog()

    val blessing = registry.getOrThrow(StatusEffectId.BLESSING)
    assertEquals("BLESSING", blessing.identifier)
    assertEquals("BlessingStatusEffect", blessing.script)
    assertTrue(blessing.isSyncedToClient)

    val marker = registry.getOrThrow(StatusEffectId.MASTER_INTRO_MARKER)
    assertEquals("MasterIntroMarker", marker.script)
    assertFalse(marker.isSyncedToClient, "the intro marker is server-internal")
  }

  @Test
  fun `a StatusEffectId with no catalog entry fails the boot`() {
    val registry = StatusEffectDefinitionRegistry().apply { load(emptyList()) }

    val e = assertThrows<StatusEffectCatalogMismatchException> {
      StatusEffectCatalogBootValidator(registry).validateStatusEffectCatalog()
    }

    assertTrue(e.message!!.contains("SWIFTNESS"), "should name the orphaned constant: ${e.message}")
  }

  @Test
  fun `a catalog entry with no StatusEffectId constant fails the boot`() {
    val registry = StatusEffectDefinitionRegistry().apply {
      load(
        StatusEffectId.entries.map { definitionOf(it.id, it.name) } +
          definitionOf(id = 4242, identifier = "GHOST")
      )
    }

    val e = assertThrows<StatusEffectCatalogMismatchException> {
      StatusEffectCatalogBootValidator(registry).validateStatusEffectCatalog()
    }

    assertTrue(e.message!!.contains("GHOST"), "should name the unapplicable effect: ${e.message}")
  }

  /**
   * Matching ids but a renamed identifier is the sneaky case - everything still resolves, but the
   * name in code no longer means what the catalog says it does.
   */
  @Test
  fun `an identifier that drifted from the enum name fails the boot`() {
    val registry = StatusEffectDefinitionRegistry().apply {
      load(
        StatusEffectId.entries.map { effect ->
          val identifier = if (effect == StatusEffectId.SWIFTNESS) "RENAMED_HASTE" else effect.name
          definitionOf(effect.id, identifier)
        }
      )
    }

    val e = assertThrows<StatusEffectCatalogMismatchException> {
      StatusEffectCatalogBootValidator(registry).validateStatusEffectCatalog()
    }

    assertTrue(e.message!!.contains("RENAMED_HASTE"), "should name the mismatch: ${e.message}")
  }

  private fun loadShippedCatalog(): StatusEffectDefinitionRegistry {
    val registry = StatusEffectDefinitionRegistry()
    StatusEffectImporterBootRunner(registry).run()

    return registry
  }

  private fun definitionOf(id: Long, identifier: String) = StatusEffectDefinition(
    id = id,
    identifier = identifier,
    isSyncedToClient = true,
    script = "Swiftness"
  )
}
