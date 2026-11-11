package net.bestia.zone.battle.status.scripts

import io.mockk.mockk
import io.mockk.verify
import net.bestia.zone.battle.BestiaKind
import net.bestia.zone.battle.Element
import net.bestia.zone.battle.Size
import net.bestia.zone.battle.StatusEffectService
import net.bestia.zone.battle.damage.HitKind
import net.bestia.zone.battle.damage.HitReach
import net.bestia.zone.battle.ecs.damage.IncomingDamage
import net.bestia.zone.battle.ecs.effects.ActiveStatusEffect
import net.bestia.zone.battle.ecs.effects.StatusEffects
import net.bestia.zone.battle.ecs.status.Health
import net.bestia.zone.battle.ecs.status.Nature
import net.bestia.zone.battle.status.DefenseValues
import net.bestia.zone.battle.status.RemovalReason
import net.bestia.zone.battle.status.StatusEffectHookContext
import net.bestia.zone.battle.status.StatusEffectId
import net.bestia.zone.battle.status.StatusValueRecalcContext
import net.bestia.zone.battle.status.StatusValues
import net.bestia.zone.ecs.core.testWorld
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class PriestStatusesTest {

  private val world = testWorld()
  private val service = mockk<StatusEffectService>(relaxed = true)

  @Test
  fun `the sigil cuts physical defence by 10 percent per level, twice that on a demon or an undead`() {
    assertEquals(30, SignumCrucisSigil.defenseCutPercent(3, null))
    assertEquals(60, SignumCrucisSigil.defenseCutPercent(3, Nature(Element.UNDEAD_2, Size.MEDIUM)))
    assertEquals(20, SignumCrucisSigil.defenseCutPercent(1, Nature(Element.FIRE, Size.MEDIUM, kind = BestiaKind.DEMON)))
  }

  @Test
  fun `a cut leaves the magic defence alone`() {
    val cut = DefenseValues(magicDefense = 40, defense = 20, defenseSpread = 10, hardDefense = 50).withPhysicalDefenseCutBy(30)

    assertEquals(DefenseValues(magicDefense = 40, defense = 14, defenseSpread = 7, hardDefense = 35), cut)
  }

  @Test
  fun `the mark doubles the next hit once and lets a poison tick through untouched`() {
    val effect = ActiveStatusEffect(StatusEffectId.LEX_AETERNA.id, 1, 20f)
    val context = StatusEffectHookContext(world, 7L, effect, service)
    val sut = LexAeternaMark()

    assertEquals(10, sut.onDamageTaken(context, IncomingDamage.DamageAmount(10, 3L)))
    assertEquals(80, sut.onDamageTaken(context, IncomingDamage.DamageAmount(40, 3L, HitKind.physical(HitReach.MELEE))))

    verify(exactly = 1) { service.removeEffect(world, 7L, StatusEffectId.LEX_AETERNA.id, RemovalReason.CANCELLED) }
  }

  @Test
  fun `Increase AGI adds speed at every level and AGI and attack speed per level`() {
    val context = recalc()

    AgilityIncreased().apply(world, 1L, context, ActiveStatusEffect(StatusEffectId.INCREASE_AGI.id, 3, 160f))

    assertEquals(11.5f, context.speed, 0.001f)
    assertEquals(9, context.agility)
    assertEquals(5, context.aspdPercent)
    assertEquals(listOf(80.0, 120.0, 160.0), (1..3).map { AgilityIncreased().durationSeconds(it) })
  }

  @Test
  fun `Kyrie soaks a share of max HP from each hit and breaks after as many hits as its level`() {
    val bearer = world.createEntity { id -> add(id, Health(1000, 1000)) }
    val effect = ActiveStatusEffect(StatusEffectId.KYRIE_ELEISON.id, 2, 60f)
    val context = StatusEffectHookContext(world, bearer, effect, service)
    val sut = KyrieEleisonWard()
    sut.onApply(context)

    assertEquals(5, sut.onDamageTaken(context, IncomingDamage.DamageAmount(5, 3L)))
    assertEquals(0, sut.onDamageTaken(context, IncomingDamage.DamageAmount(150, 3L, HitKind.magic())))
    verify(exactly = 0) { service.removeEffect(world, bearer, StatusEffectId.KYRIE_ELEISON.id, any()) }
    assertEquals(300, sut.onDamageTaken(context, IncomingDamage.DamageAmount(500, 3L, HitKind.physical(HitReach.MELEE))))
    verify(exactly = 1) { service.removeEffect(world, bearer, StatusEffectId.KYRIE_ELEISON.id, RemovalReason.CANCELLED) }
  }

  @Test
  fun `Suffragium shortens casting, Magnificat speeds regeneration per level`() {
    val context = recalc()

    SuffragiumGrace().apply(world, 1L, context, ActiveStatusEffect(StatusEffectId.SUFFRAGIUM.id, 1, 30f))
    MagnificatHymn().apply(world, 1L, context, ActiveStatusEffect(StatusEffectId.MAGNIFICAT.id, 2, 90f))

    assertEquals(30, context.castTimeReduction)
    assertEquals(20, context.hpRegen.percent)
    assertEquals(20, context.manaRegen.percent)
  }

  @Test
  fun `Gloria gives a crafting roll bonus only while it holds`() {
    val blessed = world.createEntity { id ->
      add(id, StatusEffects(mutableListOf(ActiveStatusEffect(StatusEffectId.GLORIA.id, 1, 90f))))
    }
    val plain = world.createEntity { }

    assertEquals(15, GloriaGrace.rollChanceBonusOf(world, blessed))
    assertEquals(0, GloriaGrace.rollChanceBonusOf(world, plain))
  }

  private fun recalc() = StatusValueRecalcContext(StatusValues(1, 1, 1, 1, 1, 1), baseSpeed = 10f)
}
