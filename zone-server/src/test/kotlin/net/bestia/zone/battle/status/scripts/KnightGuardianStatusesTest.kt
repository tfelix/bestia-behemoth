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
import net.bestia.zone.battle.ecs.status.CombatStats
import net.bestia.zone.battle.gear.ArmorType
import net.bestia.zone.battle.status.CombatValues
import net.bestia.zone.battle.status.RemovalReason
import net.bestia.zone.battle.status.StatusEffectHookContext
import net.bestia.zone.battle.status.StatusEffectId
import net.bestia.zone.battle.status.StatusEffectTickContext
import net.bestia.zone.battle.status.StatusValueRecalcContext
import net.bestia.zone.battle.status.StatusValues
import net.bestia.zone.ecs.core.testWorld
import net.bestia.zone.entity.ecs.Dead
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.movement.ecs.Position
import net.bestia.zone.util.EntityId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class KnightGuardianStatusesTest {

  private val world = testWorld()
  private val service = mockk<StatusEffectService>(relaxed = true)

  @Test
  fun `Shield Wall cuts physical damage taken and slows the Knight while a shield is held`() {
    val context = recalc(shield = ArmorType.HEAVY)

    ShieldWalled().apply(world, 1L, context, ActiveStatusEffect(StatusEffectId.SHIELD_WALL.id, 1, Float.POSITIVE_INFINITY))

    assertEquals(85, DamageModifiers.weigh(100, HitKind.physical(HitReach.MELEE), null, defender(context)))
    assertEquals(100, DamageModifiers.weigh(100, HitKind.magic(), null, defender(context)))
    assertEquals(5f, context.speed, 0.001f)
    assertEquals(listOf(15, 20, 25, 30, 35), (1..5).map { ShieldWalled.cutPercent(it) })
    assertEquals(listOf(50, 40, 30, 30, 30), (1..5).map { ShieldWalled.slowPercent(it) })
  }

  @Test
  fun `Shield Wall adds to Iron Skin and multiplies with Magic Armor`() {
    val context = recalc(shield = ArmorType.HEAVY)
    context.addDamageTaken(30, form = HitForm.PHYSICAL)

    ShieldWalled().apply(world, 1L, context, ActiveStatusEffect(StatusEffectId.SHIELD_WALL.id, 5, Float.POSITIVE_INFINITY))
    MagicArmorWard().apply(world, 1L, context, ActiveStatusEffect(StatusEffectId.MAGIC_ARMOR.id, 3, 300f))

    // 100 x (1 - 0.30 - 0.35) x (1 - 0.30)
    assertEquals(24, DamageModifiers.weigh(100, HitKind.physical(HitReach.MELEE), null, defender(context)))
  }

  @Test
  fun `without a shield the wall does nothing and drops on its next tick`() {
    val context = recalc(shield = null)
    val knight = world.createEntity { id -> add(id, CombatStats(combat(shield = null))) }
    val effect = ActiveStatusEffect(StatusEffectId.SHIELD_WALL.id, 3, Float.POSITIVE_INFINITY)

    ShieldWalled().apply(world, knight, context, effect)
    ShieldWalled().onTick(StatusEffectTickContext(world, knight, effect, service))

    assertEquals(DamageModifiers.NONE, context.damageModifiers)
    verify(exactly = 1) { service.removeEffect(world, knight, StatusEffectId.SHIELD_WALL.id, RemovalReason.CANCELLED) }
  }

  @Test
  fun `at Lv 5 the attacker gets a tenth of what the wall stopped`() {
    // The wall's 35% beside Iron Skin's 30%: a 100 blow lands as 35, and the wall stopped 35 of it.
    val knight = world.createEntity { id -> add(id, CombatStats(combat(shield = ArmorType.HEAVY, physicalCut = 65))) }
    val wolf = world.createEntity { }

    val landed = onDamageTaken(knight, level = 5, IncomingDamage.DamageAmount(35, wolf, HitKind.physical(HitReach.MELEE)))

    assertEquals(35, landed)
    assertEquals(listOf(IncomingDamage.DamageAmount(3, knight)), world.get(wolf, IncomingDamage::class)?.amounts)
    assertEquals(35, ShieldWalled.stoppedByWall(landed = 35, wallCutPercent = 35, physicalCutPercent = 65))
  }

  @Test
  fun `below Lv 5, or against a spell, nothing goes back`() {
    val knight = world.createEntity { id -> add(id, CombatStats(combat(shield = ArmorType.HEAVY, physicalCut = 30))) }
    val wolf = world.createEntity { }

    onDamageTaken(knight, level = 4, IncomingDamage.DamageAmount(70, wolf, HitKind.physical(HitReach.MELEE)))
    onDamageTaken(knight, level = 5, IncomingDamage.DamageAmount(70, wolf, HitKind.magic()))

    assertNull(world.get(wolf, IncomingDamage::class))
  }

  @Test
  fun `a devoted ally's blows and spells land on the Knight instead, as they would have hurt the ally`() {
    val knight = body(Vec3L(0, 0, 0))
    val ally = body(Vec3L(7, 0, 0))
    val wolf = world.createEntity { }
    val context = devotedTo(knight, ally)

    assertEquals(0, Devoted().onDamageTaken(context, IncomingDamage.DamageAmount(40, wolf, HitKind.physical(HitReach.MELEE))))
    assertEquals(0, Devoted().onDamageTaken(context, IncomingDamage.DamageAmount(25, wolf, HitKind.magic())))
    // A poison tick stays with the ally.
    assertEquals(5, Devoted().onDamageTaken(context, IncomingDamage.DamageAmount(5, wolf)))

    assertEquals(
      listOf(IncomingDamage.DamageAmount(40, wolf), IncomingDamage.DamageAmount(25, wolf)),
      world.get(knight, IncomingDamage::class)?.amounts
    )
    assertEquals(listOf(15.0, 30.0, 45.0), (1..3).map { Devoted().durationSeconds(it) })
  }

  @Test
  fun `Devotion ends once the ally is more than 7 m from the Knight`() {
    val knight = body(Vec3L(0, 0, 0))
    val ally = body(Vec3L(8, 0, 0))
    val context = devotedTo(knight, ally)

    assertEquals(40, Devoted().onDamageTaken(context, IncomingDamage.DamageAmount(40, 9L, HitKind.physical(HitReach.MELEE))))
    Devoted().onTick(StatusEffectTickContext(world, ally, context.effect, service))

    assertNull(world.get(knight, IncomingDamage::class))
    verify(exactly = 2) { service.removeEffect(world, ally, StatusEffectId.DEVOTED.id, RemovalReason.CANCELLED) }
  }

  @Test
  fun `Devotion ends when the Knight falls`() {
    val knight = body(Vec3L(0, 0, 0)).also { world.add(it, Dead()) }
    val ally = body(Vec3L(1, 0, 0))

    Devoted().onTick(StatusEffectTickContext(world, ally, devotedTo(knight, ally).effect, service))

    verify(exactly = 1) { service.removeEffect(world, ally, StatusEffectId.DEVOTED.id, RemovalReason.CANCELLED) }
  }

  private fun body(at: Vec3L): EntityId {
    return world.createEntity { id -> add(id, Position.fromVec3(at)) }
  }

  private fun devotedTo(knight: EntityId, ally: EntityId): StatusEffectHookContext {
    val effect = ActiveStatusEffect(StatusEffectId.DEVOTED.id, 1, 15f, sourceEntityId = knight)

    return StatusEffectHookContext(world, ally, effect, service)
  }

  private fun onDamageTaken(knight: EntityId, level: Int, hit: IncomingDamage.DamageAmount): Int {
    val effect = ActiveStatusEffect(StatusEffectId.SHIELD_WALL.id, level, Float.POSITIVE_INFINITY)

    return ShieldWalled().onDamageTaken(StatusEffectHookContext(world, knight, effect, service), hit)
  }

  private fun recalc(shield: ArmorType?): StatusValueRecalcContext {
    return StatusValueRecalcContext(StatusValues(1, 1, 1, 1, 1, 1), baseSpeed = 10f).also { it.shieldArmorType = shield }
  }

  private fun combat(shield: ArmorType?, physicalCut: Int = 0): CombatValues {
    val modifiers = DamageModifiers(takenByForm = mapOf(HitForm.PHYSICAL to physicalCut))

    return CombatValues.ofBase(1, StatusValues(1, 1, 1, 1, 1, 1)).copy(shieldArmorType = shield, damageModifiers = modifiers)
  }

  private fun defender(context: StatusValueRecalcContext): DamageModifiers.Side {
    return DamageModifiers.Side(context.damageModifiers, kind = null, element = Element.NORMAL, boss = false)
  }
}
