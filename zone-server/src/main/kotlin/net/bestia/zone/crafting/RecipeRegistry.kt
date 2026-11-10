package net.bestia.zone.crafting

import net.bestia.zone.entity.StaticEntityKind
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service

/**
 * Every recipe in the game, held in memory and read from the tick thread.
 *
 * Loaded once by [net.bestia.zone.boot.RecipeImporterBootRunner] before the tick loop starts and never
 * written again, so the plain maps need no synchronisation - the same convention
 * [net.bestia.zone.skill.tree.MasterSkillTreeRegistry] and
 * [net.bestia.zone.prop.PropKindRegistry] follow.
 */
@Service
class RecipeRegistry {

  private var byId: Map<Long, Recipe> = emptyMap()
  private var byIdentifier: Map<String, Recipe> = emptyMap()
  private var bySkillId: Map<Long, List<Recipe>> = emptyMap()
  private var byStation: Map<StaticEntityKind?, List<Recipe>> = emptyMap()

  val size get() = byId.size

  fun load(recipes: List<Recipe>) {
    val duplicateIds = recipes.groupBy { it.id }.filterValues { it.size > 1 }.keys
    require(duplicateIds.isEmpty()) { "recipes.yml declares duplicate recipe id(s): $duplicateIds" }

    val duplicateIdentifiers = recipes.groupBy { it.identifier }.filterValues { it.size > 1 }.keys
    require(duplicateIdentifiers.isEmpty()) {
      "recipes.yml declares duplicate recipe identifier(s): $duplicateIdentifiers"
    }

    byId = recipes.associateBy { it.id }
    byIdentifier = recipes.associateBy { it.identifier }
    bySkillId = recipes.groupBy { it.requiredSkillId }
    byStation = recipes.groupBy { it.station }

    LOG.debug { "Recipe registry loaded ${recipes.size} recipe(s)" }
  }

  fun of(recipeId: Long): Recipe? = byId[recipeId]

  fun ofIdentifier(identifier: String): Recipe? = byIdentifier[identifier]

  fun all(): Collection<Recipe> = byId.values

  /**
   * Every recipe a given skill unlocks, at any level. Used by the skill scripts to answer "did
   * activating this open anything at all", and by the crafting UI to list a station's work.
   */
  fun forSkill(skillId: Long): List<Recipe> = bySkillId[skillId] ?: emptyList()

  /** Every recipe worked at [station], of any skill; null asks for the ones that need no station. */
  fun forStation(station: StaticEntityKind?): List<Recipe> {
    return byStation[station] ?: emptyList()
  }

  /** The stations [skillId] works at, in the order its recipes name them. */
  fun stationsOf(skillId: Long): List<StaticEntityKind> {
    return forSkill(skillId).mapNotNull { it.station }.distinct()
  }

  /** Every station some recipe is worked at. */
  fun stations(): List<StaticEntityKind> {
    return byStation.keys.filterNotNull()
  }

  private companion object {
    val LOG = KotlinLogging.logger { }
  }
}
