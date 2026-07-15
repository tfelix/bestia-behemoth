package net.bestia.zone.battle.buff

import org.springframework.stereotype.Service

/**
 * In-memory store of the status effect catalog, keyed by effect id. Populated once at boot by
 * [net.bestia.zone.boot.StatusEffectImporterBootRunner] from `status_effects.yml`; the catalog is config, not
 * player state, so it is never persisted to the database (mirrors
 * `net.bestia.zone.battle.skill.MasterSkillTreeRegistry`).
 */
@Service
class StatusEffectDefinitionRegistry {

  private var definitionsById: Map<Long, StatusEffectDefinition> = emptyMap()

  fun load(definitions: List<StatusEffectDefinition>) {
    definitionsById = definitions.associateBy { it.id }
  }

  fun findById(id: Long): StatusEffectDefinition? = definitionsById[id]

  fun getOrThrow(id: Long): StatusEffectDefinition =
    findById(id) ?: throw StatusEffectDefinitionNotFoundException(id)

  fun all(): Collection<StatusEffectDefinition> = definitionsById.values
}
