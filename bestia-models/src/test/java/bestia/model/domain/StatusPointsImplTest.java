package bestia.model.domain;

import org.junit.Assert;
import org.junit.Test;

public class StatusPointsImplTest {

	@Test
	public void check_invalid_sp_armor() {
		StatusPointsImpl sp = new StatusPointsImpl();

		sp.setMagicDefense(-10);
		Assert.assertEquals(0, sp.getMagicDefense());
		sp.setMagicDefense(10);
		Assert.assertEquals(10, sp.getMagicDefense());
		sp.setMagicDefense(1100);
		Assert.assertEquals(1000, sp.getMagicDefense());
	}
	
	@Test
	public void check_invalid_armor() {
		StatusPointsImpl sp = new StatusPointsImpl();

		sp.setDefense(-10);
		Assert.assertEquals(0, sp.getDefense());
		sp.setDefense(10);
		Assert.assertEquals(10, sp.getDefense());
		sp.setDefense(1100);
		Assert.assertEquals(1000, sp.getDefense());
	}

	@Test
	public void check_add() {
		StatusPointsImpl sp1 = new StatusPointsImpl();

		sp1.setStrenght(10);
		sp1.setVitality(10);
		sp1.setIntelligence(10);
		sp1.setWillpower(10);
		sp1.setAgility(10);
		sp1.setDexterity(10);
		
		sp1.setDefense(10);
		sp1.setMagicDefense(10);
	
		
		sp1.add(sp1);
		
		Assert.assertEquals(20, sp1.getDefense());
		Assert.assertEquals(20, sp1.getMagicDefense());
		
		Assert.assertEquals(20, sp1.getIntelligence());
		Assert.assertEquals(20, sp1.getVitality());
		Assert.assertEquals(20, sp1.getVitality());
	}
	
	@Test
	public void test_instanciation() {
		StatusPoints sp = new StatusPointsImpl();

		Assert.assertEquals(0, sp.getAgility());
		Assert.assertEquals(0, sp.getVitality());
		Assert.assertEquals(0, sp.getIntelligence());
		Assert.assertEquals(0, sp.getAgility());
		Assert.assertEquals(0, sp.getWillpower());
		Assert.assertEquals(0, sp.getStrength());
		
		Assert.assertEquals(0, sp.getDefense());
		Assert.assertEquals(0, sp.getMagicDefense());
	}
}
