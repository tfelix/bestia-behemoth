package net.bestia.zoneserver.battle

import org.junit.Assert
import org.junit.Test

import net.bestia.model.battle.Element

class ElementModifierTest {

  @Test
  fun getModifier_differentTests() {
    Assert.assertEquals(100, ElementModifier.getModifier(Element.EARTH, Element.EARTH).toLong())
    Assert.assertEquals(0, ElementModifier.getModifier(Element.FIRE, Element.WATER_3).toLong())
    Assert.assertEquals(100, ElementModifier.getModifier(Element.POISON, Element.NORMAL_2).toLong())
    Assert.assertEquals(0, ElementModifier.getModifier(Element.GHOST, Element.NORMAL_2).toLong())
    Assert.assertEquals(-75, ElementModifier.getModifier(Element.SHADOW, Element.UNDEAD_3).toLong())
  }

  @Test(expected = IllegalArgumentException::class)
  fun getModifier_nonLv1AttackElement_throws() {
    ElementModifier.getModifier(Element.EARTH_3, Element.FIRE_2)
  }
}
