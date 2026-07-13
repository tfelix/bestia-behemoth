package net.bestia.zone.battle.buff

import org.springframework.stereotype.Service

/**
 * In-memory store of the buff catalog, keyed by buff id. Populated once at boot by
 * [net.bestia.zone.boot.BuffImporterBootRunner] from `buffs.yml`; the catalog is config, not
 * player state, so it is never persisted to the database (mirrors
 * `net.bestia.zone.battle.skill.MasterSkillTreeRegistry`).
 */
@Service
class BuffDefinitionRegistry {

  private var definitionsById: Map<Long, BuffDefinition> = emptyMap()

  fun load(definitions: List<BuffDefinition>) {
    definitionsById = definitions.associateBy { it.id }
  }

  fun findById(id: Long): BuffDefinition? = definitionsById[id]

  fun getOrThrow(id: Long): BuffDefinition =
    findById(id) ?: throw BuffDefinitionNotFoundException(id)

  fun all(): Collection<BuffDefinition> = definitionsById.values
}
