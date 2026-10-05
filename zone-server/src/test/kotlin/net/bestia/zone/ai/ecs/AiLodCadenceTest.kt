package net.bestia.zone.ai.ecs

import net.bestia.zone.battle.status.AttackSpeed
import net.bestia.zone.ai.domain.bestia.BestiaDomain
import net.bestia.zone.bestia.DefaultAttack
import net.bestia.zone.ecs.battle.status.Health
import net.bestia.zone.ecs.entity.Animation
import net.bestia.zone.ecs.movement.CoarseMovement
import net.bestia.zone.ecs.movement.Position
import net.bestia.zone.ecs.movement.Speed
import net.bestia.zone.ecs.spawn.ambient.AmbientSpawnConfig
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.util.EntityId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * An unseen creature gets less processing, never none, and a profile can keep an unseen one at full rate.
 * Ticked with the shipped tier factors, unlike the other AI scenarios.
 */
class AiLodCadenceTest {

  private val ai = AiPipelineFixture(throttleConfig = AmbientSpawnConfig())

  @Test
  fun `an unseen creature still gets hungry and still plans, only less often`() {
    val unseen = ambient(Vec3L(0, 0, 0))
    val floored = ambient(Vec3L(50, 50, 0), minDetail = AiDetail.FULL)

    val thinks = countThinks(listOf(unseen, floored), TWO_MINUTES)

    assertEquals(AiDetail.BACKGROUND, ai.agentOf(unseen).detail)
    assertTrue(thinks.getValue(unseen) > 0, "it was frozen")
    assertTrue(
      thinks.getValue(unseen) * 5 < thinks.getValue(floored),
      "it planned ${thinks.getValue(unseen)} times against ${thinks.getValue(floored)}"
    )

    // Handed the time it was skipped, so it is behind by at most one background turn of hunger.
    val unseenHunger = ai.beliefOf(unseen, BestiaDomain.HUNGER) ?: 0
    val flooredHunger = ai.beliefOf(floored, BestiaDomain.HUNGER) ?: 0
    assertTrue(flooredHunger > 0)
    assertTrue(unseenHunger >= flooredHunger - 15, "hunger $unseenHunger against $flooredHunger")
  }

  @Test
  fun `a profile floor of full keeps an unseen creature at full rate`() {
    val floored = ambient(Vec3L(0, 0, 0), minDetail = AiDetail.FULL)

    val thinks = countThinks(listOf(floored), TWO_MINUTES).getValue(floored)

    assertEquals(AiDetail.FULL, ai.agentOf(floored).detail)
    assertFalse(ai.world.has(floored, CoarseMovement::class), "it walks a step per tick")
    assertTrue(thinks >= TWO_MINUTES / THINK_PERIOD_TICKS - 10, "it planned only $thinks times")
  }

  /** How often each agent planned: its think tick only moves when it does. */
  private fun countThinks(ids: List<EntityId>, ticks: Int): Map<EntityId, Int> {
    val thinks = ids.associateWith { 0 }.toMutableMap()
    val lastThinkTick = ids.associateWith { ai.agentOf(it).nextThinkTick }.toMutableMap()

    repeat(ticks) {
      ai.tick()
      for (id in ids) {
        val next = ai.agentOf(id).nextThinkTick
        if (next != lastThinkTick[id]) thinks[id] = thinks.getValue(id) + 1
        lastThinkTick[id] = next
      }
    }

    return thinks
  }

  /** An ambient wanderer, the kind that may drop to the background tier. */
  private fun ambient(at: Vec3L, minDetail: AiDetail = AiDetail.BACKGROUND): EntityId {
    val profile = ai.profiles.getOrThrow("passive_wanderer").copy(minDetail = minDetail)

    return ai.world.createEntity { id ->
      ai.world.add(id, Position.fromVec3(at))
      ai.world.add(id, Health(10, 10))
      ai.world.add(id, Speed())
      ai.world.add(id, Animation())
      ai.world.add(id, ai.agentFactory.create(profile, DefaultAttack.MELEE, AttackSpeed.DEFAULT_SPECIES_ASPD, homePosition = at))
      ai.world.add(id, AiThrottleable)
    }
  }

  private companion object {
    const val TWO_MINUTES = 20 * 120
    const val THINK_PERIOD_TICKS = 10
  }
}
