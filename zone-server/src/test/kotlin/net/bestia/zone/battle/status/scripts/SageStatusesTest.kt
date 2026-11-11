package net.bestia.zone.battle.status.scripts

import io.mockk.every
import io.mockk.mockk
import net.bestia.zone.aoi.EntityAOIService
import net.bestia.zone.battle.Element
import net.bestia.zone.battle.StatusEffectService
import net.bestia.zone.battle.ecs.effects.ActiveStatusEffect
import net.bestia.zone.battle.ecs.effects.StatusEffects
import net.bestia.zone.battle.ecs.status.Mana
import net.bestia.zone.battle.status.StatusEffectId
import net.bestia.zone.battle.status.StatusEffectTickContext
import net.bestia.zone.battle.status.StatusValueRecalcContext
import net.bestia.zone.battle.status.StatusValues
import net.bestia.zone.casting.MagicRodCatch
import net.bestia.zone.ecs.core.testWorld
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.identity.ecs.Account
import net.bestia.zone.identity.ecs.Master
import net.bestia.zone.movement.ecs.Position
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SageStatusesTest {

  private val world = testWorld()

  @Test
  fun `an endowed weapon strikes as its element with more attack per level`() {
    val context = StatusValueRecalcContext(StatusValues(1, 1, 1, 1, 1, 1), baseSpeed = 10f)

    val effect = ActiveStatusEffect(StatusEffectId.MASTER_ENDOWED.id, 3, 60f).apply { amount = Element.FIRE.ordinal }
    MasterEndowed().apply(world, 1L, context, effect)

    assertEquals(Element.FIRE, context.weaponElement)
    assertEquals(15, context.attackPercent)
  }

  @Test
  fun `the drain takes a share of a foe's max mana for the bearer and leaves friends alone`() {
    val sage = entity(Vec3L(0, 0, 0), mana = 10, account = 1L)
    val foe = entity(Vec3L(3, 0, 0), mana = 500)
    val far = entity(Vec3L(20, 0, 0), mana = 500)
    val aoi = mockk<EntityAOIService> { every { queryEntitiesInCube(any(), any(), any()) } returns setOf(sage, foe, far) }
    val effect = ActiveStatusEffect(StatusEffectId.MANA_DRAINING.id, level = 2, remainingSeconds = 20f)

    ManaDraining(aoi).onTick(StatusEffectTickContext(world, sage, effect, mockk(relaxed = true)))

    assertEquals(480, world.get(foe, Mana::class)!!.current)
    assertEquals(500, world.get(far, Mana::class)!!.current)
    assertEquals(30, world.get(sage, Mana::class)!!.current)
  }

  @Test
  fun `a rod catches a spell once and gives back a share of its cost`() {
    val statusEffects = mockk<StatusEffectService>(relaxed = true)
    val bearer = entity(Vec3L(0, 0, 0), mana = 0)
    world.add(bearer, StatusEffects(mutableListOf(ActiveStatusEffect(StatusEffectId.MAGIC_ROD.id, 2, 15f))))

    assertTrue(MagicRodCatch.catches(world, bearer, manaCost = 40, statusEffects))
    assertEquals(30, world.get(bearer, Mana::class)!!.current)

    val bare = entity(Vec3L(0, 0, 0), mana = 0)
    assertFalse(MagicRodCatch.catches(world, bare, manaCost = 40, statusEffects))
  }

  private fun entity(at: Vec3L, mana: Int, account: Long? = null) = world.createEntity { id ->
    add(id, Position.fromVec3(at))
    add(id, Mana(mana, 1000))
    account?.let {
      add(id, Account(it))
      add(id, Master(it))
    }
  }
}
