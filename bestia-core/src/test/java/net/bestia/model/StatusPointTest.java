package net.bestia.model;


import net.bestia.core.game.model.StatusPoint;

import org.junit.Assert;
import org.junit.Test;

public class StatusPointTest {
	
	@Test
	public void test_instanciation() {
		StatusPoint sp = new StatusPoint();
		Assert.assertEquals(0, sp.getAtk());
		Assert.assertEquals(0, sp.getDef());
		Assert.assertEquals(0, sp.getCurrentHp());
		Assert.assertEquals(0, sp.getCurrentMana());
		Assert.assertEquals(0, sp.getSpAtk());
		Assert.assertEquals(0, sp.getSpd());
		Assert.assertEquals(0, sp.getSpDef());
	}
	
	@Test
	public void test_CriticalValues() {
		StatusPoint sp = new StatusPoint();
		sp.setMaxHp(110);
		sp.setCurrentHp(100);
		Assert.assertEquals(100, sp.getCurrentHp());
		sp.setMaxHp(50);
		Assert.assertEquals(50, sp.getCurrentHp());
		
		sp.setMaxMana(200);
		sp.setCurrentMana(100);
		Assert.assertEquals(100, sp.getCurrentMana());
		sp.setMaxMana(20);
		Assert.assertEquals(20, sp.getCurrentMana());
	}
	
	@Test(expected=IllegalArgumentException.class)
	public void test_IllegalMana() {
		StatusPoint sp = new StatusPoint();
		sp.setCurrentMana(-10);
	}
	
	@Test(expected=IllegalArgumentException.class)
	public void test_IllegalHp() {
		StatusPoint sp = new StatusPoint();
		sp.setCurrentHp(-10);
	}
	
	@Test(expected=IllegalArgumentException.class)
	public void test_IllegalMaxHp() {
		StatusPoint sp = new StatusPoint();
		sp.setMaxHp(-10);
	}
	
	@Test(expected=IllegalArgumentException.class)
	public void test_IllegalMaxMana() {
		StatusPoint sp = new StatusPoint();
		sp.setMaxMana(-10);
	}
	
	//@Test
	public void test_Addition() {
		// TODO
	}
	
	// TODO: Hashvalue und Equals noch testen.
}

