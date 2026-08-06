package net.bestia.zone.battle.status

import net.bestia.zone.BestiaException
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component

/**
 * Cross-checks the [StatusEffectId] enum against `status_effects.yml` in both directions and fails
 * the boot on any drift. The two exist together on purpose - the yml owns each effect's metadata,
 * the enum gives call sites a compiler-checked symbol - and this is what stops that duplication from
 * rotting. Mirrors [net.bestia.zone.dialog.DialogCatalogBootValidator].
 *
 * Throws instead of logging: a [StatusEffectId] with no catalog entry throws at the moment someone
 * tries to apply it, and a catalog entry with no enum constant is an effect nothing can ever apply.
 * Both are authoring mistakes with no legitimate in-between state.
 *
 * Runs on [ApplicationReadyEvent] because the catalog is filled by
 * [net.bestia.zone.boot.StatusEffectImporterBootRunner], a `CommandLineRunner` - at
 * `@PostConstruct` time the registry is still empty and every check would trivially pass.
 */
@Component
class StatusEffectCatalogBootValidator(
  private val statusEffectDefinitionRegistry: StatusEffectDefinitionRegistry
) {

  @EventListener(ApplicationReadyEvent::class)
  fun validateStatusEffectCatalog() {
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
