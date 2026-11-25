package net.bestia.zone.casting.essence

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.bestia.zone.battle.damage.HitKind
import net.bestia.zone.battle.damage.HitReach
import net.bestia.zone.battle.ecs.damage.LandedHits
import net.bestia.zone.battle.ecs.status.CombatStats
import net.bestia.zone.battle.status.CombatValues
import net.bestia.zone.battle.status.EssenceCast
import net.bestia.zone.battle.status.EssenceCast.Spot
import net.bestia.zone.battle.status.EssenceCast.Trigger
import net.bestia.zone.casting.SkillExecutionService
import net.bestia.zone.ecs.core.testWorld
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.movement.ecs.Position
import net.bestia.zone.skill.SkillIds
import net.bestia.zone.util.EntityId
import org.junit.jupiter.api.Test
import java.util.Random

class EssenceCastSystemTest {

  private val world = testWorld()
  private val hits = EssenceCastHits()

  private val skills = mockk<SkillExecutionService>(relaxed = true)
  private val skillIds = SkillIds().apply { load(mapOf("miasma" to MIASMA, "burning_ground" to BURNING_GROUND)) }

  /** Always rolls 3 of 100: a 4% cast goes off, a 3% one does not. */
  private val rolls = mockk<Random> { every { nextInt(100) } returns 3 }

  private val system = EssenceCastSystem(hits, skills, skillIds, rolls)

  @Test
  fun `a blow from the wearer may cast at the one it struck`() {
    val dragonBlade = wearing(EssenceCast("miasma", 4, Trigger.STRIKES, Spot.FOE), at = Position(0, 0, 0))
    val foe = standing(Position(2, 0, 0))

    land(source = dragonBlade, target = foe, HitKind.physical(HitReach.RANGED))
    tick()

    verify { skills.executeFree(world, dragonBlade, MIASMA, 1, foe, Vec3L(2, 0, 0)) }
  }

  @Test
  fun `a melee blow on the wearer may cast under the attacker`() {
    val ventShield = wearing(EssenceCast("burning_ground", 4, Trigger.STRUCK_IN_MELEE, Spot.FOE), at = Position(0, 0, 0))
    val attacker = standing(Position(1, 0, 0))

    land(source = attacker, target = ventShield, HitKind.physical(HitReach.MELEE))
    tick()

    verify { skills.executeFree(world, ventShield, BURNING_GROUND, 1, attacker, Vec3L(1, 0, 0)) }
  }

  @Test
  fun `a spell, a shot for a melee trigger or a missed roll casts nothing`() {
    val ventShield = wearing(EssenceCast("burning_ground", 4, Trigger.STRUCK_IN_MELEE, Spot.FOE), at = Position(0, 0, 0))
    val unlucky = wearing(EssenceCast("miasma", 3, Trigger.STRIKES, Spot.FOE), at = Position(0, 0, 0))
    val foe = standing(Position(1, 0, 0))

    land(source = foe, target = ventShield, HitKind.magic())
    land(source = foe, target = ventShield, HitKind.physical(HitReach.RANGED))
    land(source = unlucky, target = foe, HitKind.physical(HitReach.MELEE))
    tick()

    verify(exactly = 0) { skills.executeFree(any(), any(), any(), any(), any(), any()) }
  }

  private fun wearing(cast: EssenceCast, at: Position): EntityId = world.createEntity { id ->
    add(id, CombatStats(CombatValues.OBJECT_BASELINE.copy(essenceCasts = listOf(cast))))
    add(id, at)
  }

  private fun standing(at: Position): EntityId = world.createEntity { id -> add(id, at) }

  private fun land(source: EntityId, target: EntityId, kind: HitKind) {
    hits.landed(LandedHits.LandedHit(target, source, healthBefore = 100, healthAfter = 90, maxHealth = 100, kind))
  }

  private fun tick() {
    system.update(world, 0.05f)
    world.runPostedUntil(System.nanoTime() + POSTED_NANOS)
  }

  private companion object {
    const val MIASMA = 2062L
    const val BURNING_GROUND = 2040L
    const val POSTED_NANOS = 50_000_000L
  }
}
