package net.bestia.zone.dialog

import net.bestia.zone.boot.DialogImporterBootRunner
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows

class DialogCatalogBootValidatorTest {

  /**
   * Runs the real importer over the real `dialogs.yml`, so adding a dialog to one side and
   * forgetting the other fails here rather than at server boot.
   */
  @Test
  fun `the shipped dialogs-yml and DialogId agree`() {
    val registry = DialogDefinitionRegistry()
    DialogImporterBootRunner(registry).run()

    assertEquals(DialogId.entries.size, registry.all().size, "one dialogs.yml entry per DialogId constant")
    assertDoesNotThrow { DialogCatalogBootValidator(registry).validateDialogCatalog() }
  }

  @Test
  fun `the shipped dialogs-yml parses type and declared args`() {
    val registry = DialogDefinitionRegistry()
    DialogImporterBootRunner(registry).run()

    val intro = registry.getOrThrow(DialogId.MASTER_INTRO)
    assertEquals("MASTER_INTRO", intro.identifier)
    assertEquals(DialogType.CONFIRM, intro.type)
    // Both placeholders the greeting actually renders - see `MasterIntroMarker`, which supplies exactly these
    // two, and DIALOG_1_TEXT in the client's dialogs.csv, which spends them. DialogService rejects a send
    // whose keys do not match this list, so the three have to agree.
    assertEquals(listOf("masterName", "worldName"), intro.args)
  }

  @Test
  fun `a DialogId with no catalog entry fails the boot`() {
    val registry = DialogDefinitionRegistry().apply { load(emptyList()) }

    val e = assertThrows<DialogCatalogMismatchException> {
      DialogCatalogBootValidator(registry).validateDialogCatalog()
    }

    assertTrue(e.message!!.contains("MASTER_INTRO"), "should name the orphaned constant: ${e.message}")
  }

  @Test
  fun `a catalog entry with no DialogId constant fails the boot`() {
    val registry = DialogDefinitionRegistry().apply {
      load(
        DialogId.entries.map { DialogDefinition(it.id, it.name, DialogType.CONFIRM, emptyList()) } +
          DialogDefinition(id = 4242, identifier = "GHOST", type = DialogType.CONFIRM, args = emptyList())
      )
    }

    val e = assertThrows<DialogCatalogMismatchException> {
      DialogCatalogBootValidator(registry).validateDialogCatalog()
    }

    assertTrue(e.message!!.contains("GHOST"), "should name the unsendable dialog: ${e.message}")
  }

  /**
   * Matching ids but a renamed identifier is the sneaky case - everything still resolves, but the
   * name in code no longer means what the catalog says it does.
   */
  @Test
  fun `an identifier that drifted from the enum name fails the boot`() {
    val registry = DialogDefinitionRegistry().apply {
      load(
        DialogId.entries.map { dialog ->
          val identifier = if (dialog == DialogId.MASTER_INTRO) "RENAMED_INTRO" else dialog.name
          DialogDefinition(dialog.id, identifier, DialogType.CONFIRM, emptyList())
        }
      )
    }

    val e = assertThrows<DialogCatalogMismatchException> {
      DialogCatalogBootValidator(registry).validateDialogCatalog()
    }

    assertTrue(e.message!!.contains("RENAMED_INTRO"), "should name the mismatch: ${e.message}")
  }
}
