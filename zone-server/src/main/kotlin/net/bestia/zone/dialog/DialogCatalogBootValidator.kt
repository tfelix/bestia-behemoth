package net.bestia.zone.dialog

import net.bestia.zone.BestiaException
import net.bestia.zone.util.CatalogValidator
import org.springframework.stereotype.Component

/**
 * Cross-checks the [DialogId] enum against `dialogs.yml` in both directions and fails the boot on
 * any drift. The two exist together on purpose - the yml owns each dialog's metadata, the enum gives
 * call sites a compiler-checked symbol - and this is what stops that duplication from rotting.
 *
 * Unlike [net.bestia.zone.casting.scripts.SkillScriptBootValidator] this throws instead of
 * logging: a `DialogId` with no catalog entry throws at the moment someone tries to send it, and a
 * catalog entry with no enum constant is a dialog nothing can ever send. Both are authoring
 * mistakes with no legitimate in-between state, so failing immediately is cheaper than a warning
 * nobody reads.
 *
 * Runs from [net.bestia.zone.boot.ContentValidationBootRunner], after
 * [net.bestia.zone.boot.DialogImporterBootRunner] has filled the catalog.
 */
@Component
class DialogCatalogBootValidator(
  private val dialogDefinitionRegistry: DialogDefinitionRegistry
) : CatalogValidator {

  override fun validate() {
    val problems = mutableListOf<String>()

    DialogId.entries.forEach { dialog ->
      val definition = dialogDefinitionRegistry.findById(dialog.id)

      when {
        definition == null ->
          problems += "DialogId.${dialog.name} (id=${dialog.id}) has no entry in dialogs.yml"

        definition.identifier != dialog.name ->
          problems += "DialogId.${dialog.name} (id=${dialog.id}) does not match dialogs.yml " +
            "identifier '${definition.identifier}'"
      }
    }

    dialogDefinitionRegistry.all()
      .filter { DialogId.findById(it.id) == null }
      .forEach { definition ->
        problems += "dialogs.yml '${definition.identifier}' (id=${definition.id}) has no DialogId constant, " +
          "so nothing can send it"
      }

    if (problems.isNotEmpty()) {
      throw DialogCatalogMismatchException(problems)
    }
  }
}

class DialogCatalogMismatchException(problems: List<String>) : BestiaException(
  code = "DIALOG_CATALOG_MISMATCH",
  message = "DialogId and dialogs.yml are out of sync:\n" + problems.joinToString("\n") { "  - $it" }
)
