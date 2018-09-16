package net.bestia.entity.component;

import net.bestia.zoneserver.entity.component.LevelComponent;
import org.junit.Assert;
import org.junit.Test;

public class LevelComponentTest {

	
	@Test
	public void setExp_correctExp() {
		LevelComponent lv = new LevelComponent(1);
		lv.setExp(123);
		Assert.assertEquals(123, lv.getExp());
	}
	
	@Test
	public void setExp__negExp_0Exp() {
		LevelComponent lv = new LevelComponent(1);
		lv.setExp(-10);
		Assert.assertEquals(0, lv.getExp());
	}
	
	@Test
	public void setLevel__negLvl_1Lvl() {
		LevelComponent lv = new LevelComponent(1);
		lv.setLevel(-10);
		Assert.assertEquals(1, lv.getLevel());
	}
	
	@Test
	public void setLevel__correctLevel() {
		LevelComponent lv = new LevelComponent(1);
		lv.setLevel(10);
		Assert.assertEquals(10, lv.getLevel());
	}
	
	@Test
	public void equals_differentComp_false() {
		LevelComponent lv1 = new LevelComponent(1);
		LevelComponent lv2 = new LevelComponent(1);
		
		lv1.setExp(10);
		lv2.setExp(12);
		
		Assert.assertFalse(lv1.equals(lv2));
	}
}
