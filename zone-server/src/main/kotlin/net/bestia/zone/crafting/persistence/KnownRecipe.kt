package net.bestia.zone.crafting.persistence

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import net.bestia.zone.account.persistence.Master
import java.time.Instant

/**
 * One blueprint a master has learned, by discovery or from a written recipe. Starter blueprints have no row; see
 * [net.bestia.zone.crafting.StarterKit].
 *
 * [recipeId] is not a foreign key, because recipes are config in `recipes.yml`, not rows.
 */
@Entity
@Table(
  name = "known_recipe",
  uniqueConstraints = [UniqueConstraint(columnNames = ["master_id", "recipe_id"])]
)
class KnownRecipe(
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "master_id", nullable = false)
  val master: Master,

  @Column(name = "recipe_id", nullable = false)
  val recipeId: Long,

  @Column(name = "learned_at", nullable = false)
  val learnedAt: Instant,
) {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  val id: Long = 0
}
