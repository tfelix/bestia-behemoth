package net.bestia.zoneserver.game.battle;

import net.bestia.model.domain.Element;
import net.bestia.zoneserver.game.battle.ElementModifier;

import org.junit.*;


public class ElementModifierTest {
	@Test
	public void controlsample_modifier_lv1_test() {
		int result = ElementModifier.getModifier(Element.POISON, Element.FIRE);
		Assert.assertEquals(125, result);
		
		result = ElementModifier.getModifier(Element.HOLY, Element.UNDEAD);
		Assert.assertEquals(150, result);
		
		result = ElementModifier.getModifier(Element.NORMAL, Element.SHADOW);
		Assert.assertEquals(100, result);
		
		result = ElementModifier.getModifier(Element.GHOST, Element.WATER);
		Assert.assertEquals(100, result);
		
		result = ElementModifier.getModifier(Element.EARTH, Element.FIRE);
		Assert.assertEquals(50, result);
	}
	
	@Test
	public void controlsample_modifier_lv2_test() {
		int result = ElementModifier.getModifier(Element.FIRE, Element.NORMAL_2);
		Assert.assertEquals(100, result);
		
		result = ElementModifier.getModifier(Element.HOLY, Element.EARTH_2);
		Assert.assertEquals(100, result);
		
		result = ElementModifier.getModifier(Element.NORMAL, Element.FIRE_2);
		Assert.assertEquals(100, result);
		
		result = ElementModifier.getModifier(Element.GHOST, Element.SHADOW_2);
		Assert.assertEquals(50, result);
		
		result = ElementModifier.getModifier(Element.WIND, Element.GHOST_2);
		Assert.assertEquals(100, result);
	}
	
	@Test
	public void controlsample_modifier_lv3_test() {
		int result = ElementModifier.getModifier(Element.FIRE, Element.WATER_3);
		Assert.assertEquals(0, result);
		
		result = ElementModifier.getModifier(Element.HOLY, Element.UNDEAD_3);
		Assert.assertEquals(200, result);
		
		result = ElementModifier.getModifier(Element.EARTH, Element.POISON_3);
		Assert.assertEquals(100, result);
		
		result = ElementModifier.getModifier(Element.WIND, Element.HOLY_3);
		Assert.assertEquals(25, result);
		
		result = ElementModifier.getModifier(Element.GHOST, Element.EARTH_3);
		Assert.assertEquals(50, result);
	}
	
	@Test
	public void controlsample_modifier_lv4_test() {
		int result = ElementModifier.getModifier(Element.FIRE, Element.NORMAL_4);
		Assert.assertEquals(100, result);
		
		result = ElementModifier.getModifier(Element.WATER, Element.WATER_4);
		Assert.assertEquals(-50, result);
		
		result = ElementModifier.getModifier(Element.HOLY, Element.WIND_4);
		Assert.assertEquals(75, result);
		
		result = ElementModifier.getModifier(Element.POISON, Element.GHOST_4);
		Assert.assertEquals(25, result);
		
		result = ElementModifier.getModifier(Element.GHOST, Element.UNDEAD_4);
		Assert.assertEquals(175, result);
	}
}
