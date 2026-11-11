package net.bestia.zone.ai.ecs

import net.bestia.zone.ai.domain.bestia.BestiaDomain
import net.bestia.zone.ai.profile.AiConfig
import net.bestia.zone.ai.profile.BestiaJob
import net.bestia.zone.ai.profile.Courage
import net.bestia.zone.ai.profile.OrderOverrides
import net.bestia.zone.battle.ecs.status.Stamina
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.item.care.TestBagCare.BREAD
import net.bestia.zone.item.care.TestBagCare.bagWith
import net.bestia.zone.item.ecs.Inventory
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/** The numbers an expert set in Fine-tune taking the place of what the courage step and the job choose. */
class OrderOverridesTest {

  private lateinit var ai: AiPipelineFixture

  private val home = Vec3L(0, 0, 0)

  @BeforeEach
  fun setup() {
    ai = AiPipelineFixture()
  }

  private fun resting(overrides: OrderOverrides, courage: Courage = Courage.STEADY, health: Int = 10) =
    ai.spawnMob("passive_wanderer", home, health = health, orders = AiConfig(BestiaJob.REST, courage, overrides = overrides))

  @Test
  fun `a raised eat line has a bestia eat while the default would not`() {
    val bestia = resting(OrderOverrides(eatBelowStaminaPct = 70))
    ai.world.add(bestia, Stamina(60, 100))
    ai.world.add(bestia, bagWith(BREAD to 1))

    ai.tickUntil(describe = { "stamina stayed at ${ai.world.getOrThrow(bestia, Stamina::class).current}" }) {
      ai.world.getOrThrow(bestia, Inventory::class).getItem(BREAD.toInt()) == null
    }
  }

  @Test
  fun `a retreat line of 0 keeps a hurt bestia in the field`() {
    val bestia = resting(OrderOverrides(retreatBelowHealthPct = 0), health = 1)
    ai.teleport(bestia, Vec3L(15, 0, 0))

    repeat(20 * 20) {
      ai.tick()
      assertFalse(ai.goalNameOf(bestia) == "Retreat", "it retreated although its orders say never")
    }
  }

  @Test
  fun `numbers past their limits are clamped`() {
    val gatherer = ai.spawnMob(
      "passive_wanderer", home,
      orders = AiConfig(BestiaJob.GATHER, overrides = OrderOverrides(areaRadius = 500, haulAtLoadPct = 5)),
    )

    assertEquals(64L, ai.beliefOf(gatherer, BestiaDomain.WANDER_RADIUS))
    assertEquals(64L, ai.beliefOf(gatherer, BestiaDomain.GATHER_AREA)?.radius)
    assertEquals(30, ai.beliefOf(gatherer, BestiaDomain.HAUL_AT_LOAD_PCT))
  }
}
