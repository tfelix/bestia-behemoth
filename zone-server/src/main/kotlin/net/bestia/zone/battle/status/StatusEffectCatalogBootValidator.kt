package net.bestia.zone.battle.status

import net.bestia.zone.BestiaException
import net.bestia.zone.util.CatalogValidator
import org.springframework.stereotype.Component

/**
 * Cross-checks the [StatusEffectId] enum against `status_effects.yml` in both directions and fails
 * the boot on any drift. The two exist together on purpose - the yml owns each effect's metadata,
 * the enum gives call sites a compiler-checked symbol - and this is what stops that duplication from
 * rotting. Mirrors [net.bestia.zone.dialog.DialogCatalogBootValidator].
 *
 * Also checks that every entry's `script` names a [StatusEffectScript] bean.
 *
 * Throws instead of logging: a [StatusEffectId] with no catalog entry throws at the moment someone
 * tries to apply it, a catalog entry with no enum constant is an effect nothing can ever apply, and an
 * unknown script throws when the effect is applied. All are authoring mistakes with no legitimate
 * in-between state.
 *
 * Runs from [net.bestia.zone.boot.ContentValidationBootRunner], after
 * [net.bestia.zone.boot.StatusEffectImporterBootRunner] has filled the catalog.
 */
@Component
class StatusEffectCatalogBootValidator(
  private val statusEffectDefinitionRegistry: StatusEffectDefinitionRegistry,
  private val statusEffectScriptRegistry: StatusEffectScriptRegistry,
) : CatalogValidator {

  override fun validate() {
    val problems = mutableListOf<String>()

    StatusEffectId.entries.forEach { effect ->
      val definition = statusEffectDefinitionRegistry.findById(effect.id)

      when {
        definition == null ->
          problems += "StatusEffectId.${effect.name} (id=${effect.id}) has no entry in status_effects.yml"

        definition.identifier != effect.name ->
          problems += "StatusEffectId.${effect.name} (id=${effect.id}) does not match status_effects.yml " +
            "identifier '${definition.identifier}'"
      }
    }

    statusEffectDefinitionRegistry.all()
      .filter { StatusEffectId.findById(it.id) == null }
      .forEach { definition ->
        problems += "status_effects.yml '${definition.identifier}' (id=${definition.id}) has no " +
          "StatusEffectId constant, so nothing can apply it"
      }

    statusEffectDefinitionRegistry.all()
      .filter { statusEffectScriptRegistry.get(it.script) == null }
      .forEach { definition ->
        problems += "status_effects.yml '${definition.identifier}' names script '${definition.script}', " +
          "which no StatusEffectScript bean implements"
      }

    if (problems.isNotEmpty()) {
      throw StatusEffectCatalogMismatchException(problems)
    }
  }
}

class StatusEffectCatalogMismatchException(problems: List<String>) : BestiaException(
  code = "STATUS_EFFECT_CATALOG_MISMATCH",
  message = "StatusEffectId and status_effects.yml are out of sync:\n" +
    problems.joinToString("\n") { "  - $it" }
)
