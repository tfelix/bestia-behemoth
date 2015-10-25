package net.bestia.model.domain;

import org.junit.Assert;
import org.junit.Test;

public class StatusPointsTest {

	@Test
	public void check_invalid_hp() {
		StatusPoints sp = new StatusPointsBasic();

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
		StatusPoints sp = new StatusPointsBasic();

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
		StatusPoints sp = new StatusPointsBasic();

		sp.setArmorSpDef(-10);
		Assert.assertEquals(1, sp.getArmorSpDef());
		sp.setArmorSpDef(10);
		Assert.assertEquals(10, sp.getArmorSpDef());
		sp.setArmorSpDef(110);
		Assert.assertEquals(100, sp.getArmorSpDef());
	}
	
	@Test
	public void check_invalid_armor() {
		StatusPoints sp = new StatusPointsBasic();

		sp.setArmorDef(-10);
		Assert.assertEquals(1, sp.getArmorDef());
		sp.setArmorDef(10);
		Assert.assertEquals(10, sp.getArmorDef());
		sp.setArmorDef(110);
		Assert.assertEquals(100, sp.getArmorDef());
	}

	@Test
	public void check_add() {
		StatusPoints sp1 = new StatusPointsBasic();

		sp1.setArmorDef(10);
		sp1.setArmorSpDef(10);
		sp1.setAtk(10);
		sp1.setDef(10);
		
		sp1.setMaxHp(100);
		sp1.setMaxMana(100);
		sp1.setCurrentHp(10);
		sp1.setCurrentMana(10);
		
		sp1.setSpAtk(10);
		sp1.setSpDef(10);
	
		sp1.setSpd(10);
		
		sp1.add(sp1);
		
		Assert.assertEquals(20, sp1.getArmorDef());
		Assert.assertEquals(20, sp1.getArmorSpDef());
		Assert.assertEquals(20, sp1.getAtk());
		Assert.assertEquals(20, sp1.getDef());
		
		Assert.assertEquals(200, sp1.getMaxHp());
		Assert.assertEquals(200, sp1.getMaxMana());
		
		Assert.assertEquals(20, sp1.getCurrentHp());
		Assert.assertEquals(20, sp1.getCurrentMana());
		Assert.assertEquals(20, sp1.getSpAtk());
		Assert.assertEquals(20, sp1.getSpDef());
	}
	
	@Test
	public void test_instanciation() {
		StatusPoints sp = new StatusPointsBasic();

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
		StatusPoints sp = new StatusPointsBasic();

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
		StatusPoints sp = new StatusPointsBasic();

		sp.setCurrentMana(-10);
		Assert.assertEquals(0, sp.getCurrentMana());
	}
	
	@Test
	public void illegal_low_hp() {
		StatusPoints sp = new StatusPointsBasic();

		sp.setCurrentHp(-10);
		Assert.assertEquals(0, sp.getCurrentHp());
	}
	
	@Test
	public void illegal_low_maxHp() {
		StatusPoints sp = new StatusPointsBasic();

		sp.setMaxHp(-10);
		Assert.assertEquals(1, sp.getMaxHp());
	}
	
	@Test
	public void illegal_low_maxMana() {
		StatusPoints sp = new StatusPointsBasic();
		sp.setMaxMana(-10);
		Assert.assertEquals(1, sp.getMaxMana());
	}
}
