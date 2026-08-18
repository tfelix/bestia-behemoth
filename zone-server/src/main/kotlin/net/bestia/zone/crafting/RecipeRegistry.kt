package net.bestia.zone.crafting

import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service

/**
 * Every recipe in the game, held in memory and read from the tick thread.
 *
 * Loaded once by [net.bestia.zone.boot.RecipeImporterBootRunner] before the tick loop starts and never
 * written again, so the plain maps need no synchronisation - the same convention
 * [net.bestia.zone.account.master.skill.MasterSkillTreeRegistry] and
 * [net.bestia.zone.world.prop.PropKindRegistry] follow.
 */
@Service
class RecipeRegistry {

  private var byId: Map<Long, Recipe> = emptyMap()
  private var bySkillId: Map<Long, List<Recipe>> = emptyMap()

  val size get() = byId.size

  fun load(recipes: List<Recipe>) {
    val duplicateIds = recipes.groupBy { it.id }.filterValues { it.size > 1 }.keys
    require(duplicateIds.isEmpty()) { "recipes.yml declares duplicate recipe id(s): $duplicateIds" }

    val duplicateIdentifiers = recipes.groupBy { it.identifier }.filterValues { it.size > 1 }.keys
    require(duplicateIdentifiers.isEmpty()) {
      "recipes.yml declares duplicate recipe identifier(s): $duplicateIdentifiers"
    }

    byId = recipes.associateBy { it.id }
    bySkillId = recipes.groupBy { it.requiredSkillId }

    LOG.debug { "Recipe registry loaded ${recipes.size} recipe(s)" }
  }

  fun of(recipeId: Long): Recipe? = byId[recipeId]

  fun all(): Collection<Recipe> = byId.values

  /**
   * Every recipe a given skill unlocks, at any level. Used by the skill scripts to answer "did
   * activating this open anything at all", and by the crafting UI to list a station's work.
   */
  fun forSkill(skillId: Long): List<Recipe> = bySkillId[skillId] ?: emptyList()

  private companion object {
    val LOG = KotlinLogging.logger { }
  }
}
