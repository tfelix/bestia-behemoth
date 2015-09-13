package net.bestia.zoneserver.battle;

import net.bestia.model.domain.Element;

import org.junit.Assert;
import org.junit.Test;

public class ElementModifierTest {

	@Test
	public void query_method_test() {
		int mod = ElementModifier.getModifier(Element.EARTH, Element.EARTH_4);
		Assert.assertEquals(-25, mod);
		
		mod = ElementModifier.getModifier(Element.FIRE, Element.FIRE_3);
		Assert.assertEquals(-25, mod);
		
		mod = ElementModifier.getModifier(Element.UNDEAD, Element.SHADOW_2);
		Assert.assertEquals(0, mod);
		
		mod = ElementModifier.getModifier(Element.WIND, Element.UNDEAD);
		Assert.assertEquals(100, mod);
	}
	
	/**
	 * Attacks only have level 1. Must fail on other enums.
	 */
	@Test(expected = IllegalArgumentException.class)
	public void query_illegal_method_lv2_test() {
		ElementModifier.getModifier(Element.SHADOW_2, Element.HOLY);
	}
	
	/**
	 * Attacks only have level 1. Must fail on other enums.
	 */
	@Test(expected = IllegalArgumentException.class)
	public void query_illegal_method_lv3_test() {
		ElementModifier.getModifier(Element.WIND_3, Element.FIRE_2);
	}
	
	/**
	 * Attacks only have level 1. Must fail on other enums.
	 */
	@Test(expected = IllegalArgumentException.class)
	public void query_illegal_method_lv4_test() {
		ElementModifier.getModifier(Element.EARTH_4, Element.GHOST_4);
	}
}
