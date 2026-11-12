package net.bestia.zone.battle.status.scripts

import io.mockk.mockk
import io.mockk.verify
import net.bestia.zone.battle.Element
import net.bestia.zone.battle.StatusEffectService
import net.bestia.zone.battle.damage.DamageModifiers
import net.bestia.zone.battle.damage.HitForm
import net.bestia.zone.battle.damage.HitKind
import net.bestia.zone.battle.damage.HitReach
import net.bestia.zone.battle.ecs.damage.IncomingDamage
import net.bestia.zone.battle.ecs.effects.ActiveStatusEffect
import net.bestia.zone.battle.ecs.status.Mana
import net.bestia.zone.battle.status.RemovalReason
import net.bestia.zone.battle.status.StatusEffectHookContext
import net.bestia.zone.battle.status.StatusEffectId
import net.bestia.zone.battle.status.StatusValueRecalcContext
import net.bestia.zone.battle.status.StatusValues
import net.bestia.zone.ecs.core.testWorld
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class MagicArmorWardTest {

  private val world = testWorld()
  private val service = mockk<StatusEffectService>(relaxed = true)
  private val sut = MagicArmorWard()

  @Test
  fun `the ward takes ten percent per level off every blow and spell, beside Iron Skin`() {
    val context = StatusValueRecalcContext(StatusValues(1, 1, 1, 1, 1, 1), baseSpeed = 10f)
    context.addDamageTaken(15, form = HitForm.PHYSICAL)

    sut.apply(world, 1L, context, ActiveStatusEffect(StatusEffectId.MAGIC_ARMOR.id, 3, 300f))

    val defender = DamageModifiers.Side(context.damageModifiers, kind = null, element = Element.NORMAL, boss = false)
    // Categories multiply: 100 x 0.85 x 0.7 for a blow, 100 x 0.7 for a spell.
    assertEquals(59, DamageModifiers.weigh(100, HitKind.physical(HitReach.MELEE), null, defender))
    assertEquals(70, DamageModifiers.weigh(100, HitKind.magic(), null, defender))
    assertEquals(300.0, sut.durationSeconds(1))
  }

  @Test
  fun `each hit costs two percent of the current mana and the ward breaks below a fifth`() {
    val bearer = world.createEntity { id -> add(id, Mana(250, 1000)) }
    val context = StatusEffectHookContext(world, bearer, ActiveStatusEffect(StatusEffectId.MAGIC_ARMOR.id, 1, 300f), service)

    assertEquals(40, sut.onDamageTaken(context, IncomingDamage.DamageAmount(40, 3L, HitKind.magic())))
    assertEquals(245, world.get(bearer, Mana::class)!!.current)
    // A burn is no hit and costs nothing.
    sut.onDamageTaken(context, IncomingDamage.DamageAmount(40, 3L))
    assertEquals(245, world.get(bearer, Mana::class)!!.current)
    verify(exactly = 0) { service.removeEffect(world, bearer, StatusEffectId.MAGIC_ARMOR.id, any()) }

    world.get(bearer, Mana::class)!!.current = 202
    sut.onDamageTaken(context, IncomingDamage.DamageAmount(40, 3L, HitKind.physical(HitReach.MELEE)))
    verify(exactly = 1) { service.removeEffect(world, bearer, StatusEffectId.MAGIC_ARMOR.id, RemovalReason.CANCELLED) }
  }
}
