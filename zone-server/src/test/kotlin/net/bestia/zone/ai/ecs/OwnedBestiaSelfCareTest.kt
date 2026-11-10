package net.bestia.zone.ai.ecs

import net.bestia.zone.ai.profile.AiConfig
import net.bestia.zone.ai.profile.BestiaJob
import net.bestia.zone.ai.profile.Courage
import net.bestia.zone.battle.ecs.status.Health
import net.bestia.zone.battle.ecs.status.Stamina
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.item.care.TestBagCare.BANDAGE
import net.bestia.zone.item.care.TestBagCare.BREAD
import net.bestia.zone.item.care.TestBagCare.POTION
import net.bestia.zone.item.care.TestBagCare.bagWith
import net.bestia.zone.item.ecs.Inventory
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * An owned bestia looking after itself while nobody drives it: eating and bandaging from its own bag, and
 * walking home out of a fight its courage says it is losing.
 */
class OwnedBestiaSelfCareTest {

  private lateinit var ai: AiPipelineFixture

  private val home = Vec3L(0, 0, 0)
  private val afield = Vec3L(15, 0, 0)

  @BeforeEach
  fun setup() {
    ai = AiPipelineFixture()
  }

  private fun resting(courage: Courage = Courage.STEADY, health: Int = 10) =
    ai.spawnMob("passive_wanderer", home, health = health, orders = AiConfig(BestiaJob.REST, courage))

  // ----------------------------------------------------------------------- care

  @Test
  fun `a worn out bestia eats from its bag until it is above its care line`() {
    val bestia = resting()
    ai.world.add(bestia, Stamina(10, 100))
    ai.world.add(bestia, bagWith(BREAD to 3))

    ai.tickUntil(describe = { "stamina stayed at ${ai.world.getOrThrow(bestia, Stamina::class).current}" }) {
      ai.world.getOrThrow(bestia, Stamina::class).current >= 40
    }

    // 10 + 35 is already over the line: one loaf, not three.
    assertEquals(2, ai.world.getOrThrow(bestia, Inventory::class).getItem(BREAD.toInt())?.amount)
  }

  @Test
  fun `a hurt bestia puts on a bandage`() {
    val bestia = ai.spawnMob(
      "passive_wanderer", home, health = 30, maxHealth = 100,
      orders = AiConfig(BestiaJob.REST, Courage.BRAVE),
    )
    ai.world.add(bestia, bagWith(BANDAGE to 1))

    ai.tickUntilGoal(bestia, "HealFromBag")
    ai.tickUntil { ai.world.getOrThrow(bestia, Health::class).current == 50 }
  }

  @Test
  fun `a hurt bestia leaves a potion alone`() {
    val bestia = ai.spawnMob(
      "passive_wanderer", home, health = 30, maxHealth = 100,
      orders = AiConfig(BestiaJob.REST, Courage.BRAVE),
    )
    ai.world.add(bestia, bagWith(POTION to 1))

    ai.tick(times = 20 * 20)

    assertEquals(30, ai.world.getOrThrow(bestia, Health::class).current)
    assertEquals(1, ai.world.getOrThrow(bestia, Inventory::class).getItem(POTION.toInt())?.amount)
  }

  // -------------------------------------------------------------------- courage

  @Test
  fun `a bestia hurt below its retreat line walks home`() {
    val bestia = resting(Courage.STEADY, health = 3)
    ai.teleport(bestia, afield)

    ai.tickUntilGoal(bestia, "Retreat")
    ai.tickUntil(describe = { "it stopped at ${ai.positionOf(bestia)}" }) { ai.positionOf(bestia).distance(home) <= 3 }
  }

  @Test
  fun `a brave bestia never retreats`() {
    val bestia = resting(Courage.BRAVE, health = 1)
    ai.teleport(bestia, afield)

    repeat(20 * 20) {
      ai.tick()
      assertFalse(ai.goalNameOf(bestia) == "Retreat", "a brave bestia walked away from the field")
    }
  }

  @Test
  fun `a timid bestia retreats from an enemy it merely sees`() {
    val bestia = resting(Courage.TIMID)
    ai.teleport(bestia, afield)
    ai.spawnPlayer(afield + Vec3L(3, 0, 0))

    ai.tickUntilGoal(bestia, "Retreat")
  }

  @Test
  fun `a wild mob keeps fighting however hurt it is`() {
    val wild = ai.spawnMob("passive_wanderer", home, health = 1)

    assertTrue(ai.agentOf(wild).goals.none { it.name == "Retreat" })
  }
}
