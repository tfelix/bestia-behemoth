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
import net.bestia.zone.battle.ecs.status.Nature
import net.bestia.zone.battle.status.DefenseValues
import net.bestia.zone.battle.status.RemovalReason
import net.bestia.zone.battle.status.StatusEffectHookContext
import net.bestia.zone.battle.status.StatusEffectId
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
}
