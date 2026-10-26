package net.bestia.zone.crafting.persistence

import org.springframework.data.jpa.repository.JpaRepository

interface KnownRecipeRepository : JpaRepository<KnownRecipe, Long> {

  fun findAllByMasterId(masterId: Long): List<KnownRecipe>

  fun existsByMasterIdAndRecipeId(masterId: Long, recipeId: Long): Boolean

  fun deleteAllByMasterId(masterId: Long)
}
