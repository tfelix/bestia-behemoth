package net.bestia.zone.crafting

import net.bestia.zone.account.persistence.MasterRepository
import net.bestia.zone.crafting.persistence.KnownRecipe
import net.bestia.zone.crafting.persistence.KnownRecipeRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

/** The database half of the blueprints a master has learned. The live half is [net.bestia.zone.crafting.ecs.KnownRecipes]. */
@Service
class KnownRecipeStore(
  private val knownRecipes: KnownRecipeRepository,
  private val masters: MasterRepository,
) {

  @Transactional(readOnly = true)
  fun load(masterId: Long): Set<Long> {
    return knownRecipes.findAllByMasterId(masterId).mapTo(HashSet()) { it.recipeId }
  }

  /** True when the master did not know it before. */
  @Transactional
  fun learn(masterId: Long, recipeId: Long): Boolean {
    if (knownRecipes.existsByMasterIdAndRecipeId(masterId, recipeId)) {
      return false
    }

    knownRecipes.save(KnownRecipe(masters.getReferenceById(masterId), recipeId, learnedAt = Instant.now()))

    return true
  }

  @Transactional
  fun deleteAllOf(masterId: Long) {
    knownRecipes.deleteAllByMasterId(masterId)
  }
}
