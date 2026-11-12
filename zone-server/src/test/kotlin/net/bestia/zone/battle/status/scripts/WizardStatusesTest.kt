package net.bestia.zone.battle.status.scripts

import io.mockk.mockk
import io.mockk.verify
import net.bestia.zone.battle.StatusEffectService
import net.bestia.zone.battle.damage.HitKind
import net.bestia.zone.battle.damage.HitReach
import net.bestia.zone.battle.ecs.damage.IncomingDamage
import net.bestia.zone.battle.ecs.effects.ActiveStatusEffect
import net.bestia.zone.battle.ecs.effects.AreaEffect
import net.bestia.zone.battle.ecs.effects.AreaMechanics
import net.bestia.zone.battle.status.RemovalReason
import net.bestia.zone.battle.status.StatusEffectHookContext
import net.bestia.zone.battle.status.StatusEffectId
import net.bestia.zone.battle.status.StatusEffectTickContext
import net.bestia.zone.battle.status.StatusValueRecalcContext
import net.bestia.zone.battle.status.StatusValues
import net.bestia.zone.identity.ecs.Account
import net.bestia.zone.ecs.core.testWorld
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class WizardStatusesTest {

  private val world = testWorld()
  private val service = mockk<StatusEffectService>(relaxed = true)

  @Test
  fun `Safety Wall stops physical blows until the wall's shared hits run out, then breaks`() {
    val wall = world.createEntity { id -> add(id, wall(hits = 2)) }
    val first = context(wall, bearer = 7L)
    val second = context(wall, bearer = 8L)
    val sut = SafetyWalled()

    assertEquals(0, sut.onDamageTaken(first, IncomingDamage.DamageAmount(50, 3L, HitKind.physical(HitReach.MELEE))))
    // A spell passes and spends nothing.
    assertEquals(30, sut.onDamageTaken(second, IncomingDamage.DamageAmount(30, 3L, HitKind.magic())))
    assertEquals(0, sut.onDamageTaken(second, IncomingDamage.DamageAmount(50, 3L, HitKind.physical(HitReach.RANGED))))

    val spent = world.get(wall, AreaEffect::class)!!
    assertEquals(0, spent.hitsLeft)
    assertEquals(0, spent.remainingTicks)
    verify { service.removeEffect(world, 8L, StatusEffectId.SAFETY_WALL.id, RemovalReason.CANCELLED) }

    assertEquals(50, sut.onDamageTaken(first, IncomingDamage.DamageAmount(50, 3L, HitKind.physical(HitReach.MELEE))))
  }

  @Test
  fun `a Stoning that carries a duration hands it on to the Stone`() {
    val effect = ActiveStatusEffect(StatusEffectId.STONING.id, 2, 10f, sourceEntityId = 3L).apply { amount = 30 }

    Stoning().onRemove(StatusEffectHookContext(world, 7L, effect, service), RemovalReason.EXPIRED)

    verify { service.applyEffect(world, 7L, StatusEffectId.STONE.id, 2, sourceEntityId = 3L, durationSeconds = 30.0) }
  }

  @Test
  fun `Silenced lasts three seconds at Lv1 and two more per level`() {
    val sut = Silenced(mockk(relaxed = true))

    assertEquals(listOf(3.0, 5.0, 7.0), (1..3).map { sut.durationSeconds(it) })
  }

  @Test
  fun `the Wizard's mud cuts AGI and DEX by level, capped lower on what a player owns`() {
    val wild = world.createEntity { }
    val owned = world.createEntity { id -> add(id, Account(accountId = 5L)) }
    val sut = Mired()

    assertEquals(listOf(10, 30, 50), (1..3).map { Mired.cutPercent(it, playerOwned = false) })
    assertEquals(listOf(10, 25, 25), (1..3).map { Mired.cutPercent(it, playerOwned = true) })

    val wildValues = StatusValueRecalcContext(StatusValues(10, 10, 10, 40, 10, 20), baseSpeed = 1f)
    sut.apply(world, wild, wildValues, ActiveStatusEffect(StatusEffectId.QUAGMIRE.id, 3, 2f))
    assertEquals(20 to 10, wildValues.agility to wildValues.dexterity)

    val ownedValues = StatusValueRecalcContext(StatusValues(10, 10, 10, 40, 10, 20), baseSpeed = 1f)
    sut.apply(world, owned, ownedValues, ActiveStatusEffect(StatusEffectId.QUAGMIRE.id, 3, 2f))
    assertEquals(30 to 15, ownedValues.agility to ownedValues.dexterity)
  }

  @Test
  fun `the Wizard's mud strips Increase AGI when it lands and on every tick after`() {
    val effect = ActiveStatusEffect(StatusEffectId.QUAGMIRE.id, 1, 2f)

    Mired().onApply(StatusEffectHookContext(world, 7L, effect, service))
    Mired().onTick(StatusEffectTickContext(world, 7L, effect, service))

    verify(exactly = 2) { service.removeEffect(world, 7L, StatusEffectId.INCREASE_AGI.id, any()) }
  }

  private fun context(wall: Long, bearer: Long) =
    StatusEffectHookContext(world, bearer, ActiveStatusEffect(StatusEffectId.SAFETY_WALL.id, 1, 1f, sourceEntityId = wall), service)

  private fun wall(hits: Int) = AreaEffect(
    casterId = 1L, skillId = 103, skillLevel = 1, radiusTiles = 0, damagePerTick = 0, tickIntervalSeconds = 0.5f,
    remainingTicks = 40, mechanics = AreaMechanics(zoneStatus = StatusEffectId.SAFETY_WALL, forAllies = true, absorbsHits = hits)
  )
}
