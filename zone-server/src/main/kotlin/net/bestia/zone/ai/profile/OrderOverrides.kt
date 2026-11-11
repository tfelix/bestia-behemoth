package net.bestia.zone.ai.profile

import jakarta.persistence.Column
import jakarta.persistence.Embeddable

/**
 * The numbers an expert set by hand in Fine-tune, each in place of what the courage step and the job choose. Null
 * means "not overridden". Every value is kept inside [LIMITS], so no order can turn into an unbounded planner input.
 */
@Embeddable
data class OrderOverrides(
  /** Retreats home below this share of its health. 0 never retreats. */
  @Column(name = "retreat_below_health_pct")
  val retreatBelowHealthPct: Int? = null,

  @Column(name = "eat_below_stamina_pct")
  val eatBelowStaminaPct: Int? = null,

  @Column(name = "heal_below_health_pct")
  val healBelowHealthPct: Int? = null,

  /** Carries its bag to the chest once it is this full. */
  @Column(name = "haul_at_load_pct")
  val haulAtLoadPct: Int? = null,

  /** How far from home it may stray, and so the size of its work area, in tiles. */
  @Column(name = "area_radius")
  val areaRadius: Int? = null,
) {

  /** These overrides with every value moved inside its limit. */
  fun clamped(): OrderOverrides = OrderOverrides(
    retreatBelowHealthPct = retreatBelowHealthPct?.coerceIn(LIMITS.retreat),
    eatBelowStaminaPct = eatBelowStaminaPct?.coerceIn(LIMITS.eat),
    healBelowHealthPct = healBelowHealthPct?.coerceIn(LIMITS.heal),
    haulAtLoadPct = haulAtLoadPct?.coerceIn(LIMITS.haul),
    areaRadius = areaRadius?.coerceIn(LIMITS.areaRadius),
  )

  /** These overrides, with [own] taking the place of each value it sets. */
  fun overlaidWith(own: OrderOverrides): OrderOverrides = OrderOverrides(
    retreatBelowHealthPct = own.retreatBelowHealthPct ?: retreatBelowHealthPct,
    eatBelowStaminaPct = own.eatBelowStaminaPct ?: eatBelowStaminaPct,
    healBelowHealthPct = own.healBelowHealthPct ?: healBelowHealthPct,
    haulAtLoadPct = own.haulAtLoadPct ?: haulAtLoadPct,
    areaRadius = own.areaRadius ?: areaRadius,
  )

  class Limits(
    val retreat: IntRange,
    val eat: IntRange,
    val heal: IntRange,
    val haul: IntRange,
    val areaRadius: IntRange,
  )

  companion object {
    val LIMITS = Limits(
      retreat = 0..90,
      eat = 10..90,
      heal = 10..90,
      haul = 30..100,
      // Below 4 a gatherer finds nothing and a guard cannot turn round; past 64 its area leaves the loaded ground.
      areaRadius = 4..64,
    )
  }
}
