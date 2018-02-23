package net.bestia.zoneserver.battle;

import org.junit.Assert;
import org.junit.Test;

import bestia.model.domain.Element;

public class ElementModifierTest {

	@Test
	public void getModifier_differentTests() {

		Assert.assertEquals(100, ElementModifier.getModifier(Element.EARTH, Element.EARTH));
		Assert.assertEquals(0, ElementModifier.getModifier(Element.FIRE, Element.WATER_3));
		Assert.assertEquals(100, ElementModifier.getModifier(Element.POISON, Element.NORMAL_2));
		Assert.assertEquals(0, ElementModifier.getModifier(Element.GHOST, Element.NORMAL_2));
		Assert.assertEquals(-75, ElementModifier.getModifier(Element.SHADOW, Element.UNDEAD_3));
	}

	@Test(expected = IllegalArgumentException.class)
	public void getModifier_nonLv1AttackElement_throws() {
		ElementModifier.getModifier(Element.EARTH_3, Element.FIRE_2);
	}
}
