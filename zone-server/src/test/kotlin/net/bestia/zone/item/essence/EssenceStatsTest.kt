package net.bestia.zone.item.essence

import net.bestia.zone.battle.BestiaKind
import net.bestia.zone.battle.Element
import net.bestia.zone.battle.ecs.status.BaseStatusValues
import net.bestia.zone.battle.gear.ToolTerm
import net.bestia.zone.battle.status.CombatValues
import net.bestia.zone.battle.status.StatusEffectId
import net.bestia.zone.battle.status.StatusValueRecalcContext
import net.bestia.zone.bestia.persistence.Bestia
import net.bestia.zone.item.ecs.Equipment
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EssenceStatsTest {

  private val boar = species(1, "boar", EssenceDefinition(SocketGroup.WEAPON, EssenceEffect.BANE, "BEAST"))
  private val hare = species(2, "hare", EssenceDefinition(SocketGroup.FOOTGEAR, EssenceEffect.SWIFT))
  private val ogre = species(3, "ogre", EssenceDefinition(SocketGroup.WEAPON, EssenceEffect.GIANT_SLAYER, drawback = EssenceDrawback.HIT))
  private val owl = species(4, "owl", EssenceDefinition(SocketGroup.HEADGEAR, EssenceEffect.CLEAR_MIND, "BLIND"))
  private val salamander = species(5, "salamander", EssenceDefinition(SocketGroup.ARMOR, EssenceEffect.ELEMENT_BODY, "FIRE"))
  private val brute = species(6, "brute", EssenceDefinition(SocketGroup.ACCESSORY, EssenceEffect.ATTRIBUTE_PAIR, "STR_VIT"))
  private val kobold = species(7, "kobold", EssenceDefinition(SocketGroup.TOOL, EssenceEffect.MINING_SPEED))

  private val stats = essenceStatsOf(boar, hare, ogre, owl, salamander, brute, kobold)
  private val context = StatusValueRecalcContext(BaseStatusValues(10, 10, 10, 10, 10, 10), baseSpeed = 1f)

  @Test
  fun `a ruby bane adds its eight points against its kind`() {
    stats.applyTo(context, wearing(Essence(boar.id, GemTier.RUBY)))

    assertEquals(24, context.damageModifiers.dealtVsKind[BestiaKind.BEAST])
  }

  @Test
  fun `the swift cap holds for the sum of all gems`() {
    stats.applyTo(context, wearing(Essence(hare.id, GemTier.DIAMOND), Essence(hare.id, GemTier.DIAMOND)))

    assertEquals(1.1f, context.speed, 0.0001f)
  }

  @Test
  fun `a drawback gives one and a half times the points and costs its price`() {
    stats.applyTo(context, wearing(Essence(ogre.id, GemTier.RUBY)))

    assertEquals(24, context.damageModifiers.dealtVsBoss)
    assertEquals(-10, context.hit)
  }

  @Test
  fun `clear mind resists below diamond and makes immune at diamond`() {
    stats.applyTo(context, wearing(Essence(owl.id, GemTier.RUBY)))
    assertEquals(32, context.statusResistance[StatusEffectId.BLIND])
    assertFalse(StatusEffectId.BLIND in context.statusImmunities)

    stats.applyTo(context, wearing(Essence(owl.id, GemTier.DIAMOND)))
    assertTrue(StatusEffectId.BLIND in context.statusImmunities)
  }

  @Test
  fun `element body stays at level one and puts the rest into max hp`() {
    stats.applyTo(context, wearing(Essence(salamander.id, GemTier.RUBY)))

    assertEquals(Element.FIRE, context.elementOverride)
    assertEquals(5, context.maxHpPercent)
  }

  @Test
  fun `attribute pair gives the larger half to the first attribute`() {
    stats.applyTo(context, wearing(Essence(brute.id, GemTier.AMETHYST)))

    assertEquals(12, context.strength)
    assertEquals(11, context.vitality)
  }

  @Test
  fun `a tool essence cuts the work of its tool, but never all of it`() {
    stats.applyTo(context, wearing(Essence(kobold.id, GemTier.RUBY)))
    assertEquals(24, context.toolTerms[ToolTerm.MINING_TIME_CUT])

    val fourDiamonds = List(4) { Essence(kobold.id, GemTier.DIAMOND) }
    stats.applyTo(context, wearing(*fourDiamonds.toTypedArray()))
    val values = CombatValues.OBJECT_BASELINE.copy(toolTerms = context.toolTerms)

    assertEquals(75, values.toolPercent(ToolTerm.MINING_TIME_CUT))
  }

  @Test
  fun `an essence of a species without one adds nothing`() {
    stats.applyTo(context, wearing(Essence(bestiaId = 99, tier = GemTier.DIAMOND)))

    assertEquals(StatusValueRecalcContext(BaseStatusValues(10, 10, 10, 10, 10, 10), 1f).damageModifiers, context.damageModifiers)
  }

  private fun wearing(vararg essences: Essence): List<Equipment.EquippedItem> {
    return listOf(Equipment.EquippedItem(itemId = 1, uniqueId = 1, slots = essences.size, essences = essences.toList()))
  }

  private fun species(id: Long, identifier: String, essence: EssenceDefinition): Bestia {
    return Bestia(
      id = id, identifier = identifier, levelMin = 1, levelMax = 1, experienceReward = 0, health = 1, mana = 1,
      essence = essence
    )
  }
}
