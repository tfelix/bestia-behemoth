package net.bestia.zone.skill

import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component

/**
 * Cross-checks the [SkillId] enum against the imported skill catalogue and fails the boot on drift.
 * The two exist together on purpose - `skills.yml` owns each skill's metadata, the enum gives call
 * sites a compiler-checked symbol - and this is what stops that duplication from rotting. Same
 * arrangement as [net.bestia.zone.dialog.DialogCatalogBootValidator] and
 * [net.bestia.zone.battle.status.StatusEffectCatalogBootValidator].
 *
 * ### One direction only, unlike those two
 *
 * A [SkillId] that names no catalogue row is worth a dead boot because it fails *quietly* otherwise:
 * every call site resolves the identifier to an id once, lazily, and a null degrades to "nobody
 * knows this skill". A renamed or deleted skill therefore turns a crafting bonus, a weather effect
 * or a permission gate silently inert - `BasicSkillGate` even fails open by design - and nothing
 * would say so.
 *
 * The reverse is the **normal** case and must never fail: most of `skills.yml` is content no code
 * names, and adding a skill has to stay a content-only edit.
 * [net.bestia.zone.battle.skill.passive.PassiveSkillScriptRegistry] draws the same line for the same
 * reason.
 *
 * Runs on [ApplicationReadyEvent] because the catalogue is filled by
 * [net.bestia.zone.boot.SkillImporterBootRunner], a `CommandLineRunner`. At `@PostConstruct` time
 * the import has not run, so a fresh database would fail every check and an existing one would be
 * checked against the *previous* boot's content - which is the drift this is here to catch.
 */
@Component
class SkillCatalogBootValidator(
  private val skillRepository: SkillRepository
) {

  @EventListener(ApplicationReadyEvent::class)
  fun validateSkillCatalog() {
    val catalogued = skillRepository.findAll().mapTo(mutableSetOf()) { it.identifier }

    val problems = SkillId.entries
      .filter { it.name !in catalogued }
      .map { "SkillId.${it.name} names no skill in skills.yml" }

    if (problems.isNotEmpty()) {
      throw SkillCatalogMismatchException(problems)
    }
  }
}
