package net.bestia.model.domain;

import org.junit.Assert;
import org.junit.Test;

public class StatusPointsTest {

	@Test
	public void check_invalid_hp() {
		StatusPoints sp = new StatusPoints();

		sp.setMaxHp(100);
		sp.setCurrentHp(0);

		Assert.assertEquals(0, sp.getCurrentHp());
		sp.setCurrentHp(-10);
		Assert.assertEquals(0, sp.getCurrentHp());
		sp.setCurrentHp(110);
		Assert.assertEquals(100, sp.getCurrentHp());
		sp.setMaxHp(90);
		Assert.assertEquals(90, sp.getCurrentHp());
	}

	@Test
	public void check_invalid_mana() {
		StatusPoints sp = new StatusPoints();

		sp.setMaxMana(100);
		sp.setCurrentMana(0);

		Assert.assertEquals(0, sp.getCurrentMana());
		sp.setCurrentMana(-10);
		Assert.assertEquals(0, sp.getCurrentMana());
		sp.setCurrentMana(110);
		Assert.assertEquals(100, sp.getCurrentMana());
		sp.setMaxMana(90);
		Assert.assertEquals(90, sp.getCurrentMana());
	}
	
	@Test
	public void check_invalid_sp_armor() {
		StatusPoints sp = new StatusPoints();

		sp.setMagicDefense(-10);
		Assert.assertEquals(1, sp.getMagicDefense());
		sp.setMagicDefense(10);
		Assert.assertEquals(10, sp.getMagicDefense());
		sp.setMagicDefense(110);
		Assert.assertEquals(100, sp.getMagicDefense());
	}
	
	@Test
	public void check_invalid_armor() {
		StatusPoints sp = new StatusPoints();

		sp.setDefense(-10);
		Assert.assertEquals(1, sp.getDefense());
		sp.setDefense(10);
		Assert.assertEquals(10, sp.getDefense());
		sp.setDefense(110);
		Assert.assertEquals(100, sp.getDefense());
	}

	@Test
	public void check_add() {
		StatusPoints sp1 = new StatusPoints();

		sp1.setStrenght(10);
		sp1.setVitality(10);
		sp1.setIntelligence(10);
		sp1.setWillpower(10);
		sp1.setAgility(10);
		sp1.setDexterity(10);
		
		sp1.setMaxHp(100);
		sp1.setMaxMana(100);
		sp1.setCurrentHp(10);
		sp1.setCurrentMana(10);
		
		sp1.setDefense(10);
		sp1.setMagicDefense(10);
	
		
		sp1.add(sp1);
		
		Assert.assertEquals(20, sp1.getDefense());
		Assert.assertEquals(20, sp1.getMagicDefense());
		
		Assert.assertEquals(20, sp1.getIntelligence());
		Assert.assertEquals(20, sp1.getVitality());
		Assert.assertEquals(20, sp1.getVitality());
		
		Assert.assertEquals(200, sp1.getMaxHp());
		Assert.assertEquals(200, sp1.getMaxMana());
		
		Assert.assertEquals(20, sp1.getCurrentHp());
		Assert.assertEquals(20, sp1.getCurrentMana());
	}
	
	@Test
	public void test_instanciation() {
		StatusPoints sp = new StatusPoints();

		Assert.assertEquals(1, sp.getAgility());
		Assert.assertEquals(1, sp.getVitality());
		Assert.assertEquals(1, sp.getIntelligence());
		Assert.assertEquals(1, sp.getAgility());
		Assert.assertEquals(1, sp.getWillpower());
		Assert.assertEquals(1, sp.getStrength());
		
		Assert.assertEquals(1, sp.getDefense());
		Assert.assertEquals(1, sp.getMagicDefense());
		
		Assert.assertEquals(1, sp.getCurrentHp());
		Assert.assertEquals(1, sp.getCurrentMana());
	}
	
	@Test
	public void test_CriticalValues() {
		StatusPoints sp = new StatusPoints();

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
	
	@Test
	public void test_IllegalMana() {
		StatusPoints sp = new StatusPoints();

		sp.setCurrentMana(-10);
		Assert.assertEquals(0, sp.getCurrentMana());
	}
	
	@Test
	public void illegal_low_hp() {
		StatusPoints sp = new StatusPoints();

		sp.setCurrentHp(-10);
		Assert.assertEquals(0, sp.getCurrentHp());
	}
	
	@Test
	public void illegal_low_maxHp() {
		StatusPoints sp = new StatusPoints();

		sp.setMaxHp(-10);
		Assert.assertEquals(1, sp.getMaxHp());
	}
	
	@Test
	public void illegal_low_maxMana() {
		StatusPoints sp = new StatusPoints();
		sp.setMaxMana(-10);
		Assert.assertEquals(1, sp.getMaxMana());
	}
}
