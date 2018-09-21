package net.bestia.entity.component;

import net.bestia.zoneserver.entity.component.StatusComponent;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import net.bestia.model.domain.ConditionValues;
import net.bestia.model.domain.Element;
import net.bestia.model.domain.StatusPoints;
import net.bestia.model.domain.StatusPointsImpl;
import net.bestia.model.entity.StatusBasedValuesImpl;

public class StatusComponentTest {
	
	private StatusComponent statusComp;
	
	@Before
	public void setup() {
		
		statusComp = new StatusComponent(10);
	}

	@Test
	public void getStatusPoints_returnsNunNull() {
		Assert.assertNotNull(statusComp.getStatusPoints());
	}

	@Test
	public void getOriginalStatusPoints_returnsNunNull() {
		Assert.assertNotNull(statusComp.getOriginalStatusPoints());
	}

	@Test
	public void getElement_returnsNunNull() {
		Assert.assertNotNull(statusComp.getElement());
	}

	@Test
	public void getOriginalElement_returnsNunNull() {
		Assert.assertNotNull(statusComp.getOriginalElement());
	}

	@Test
	public void getStatusBasedValues_returnsNunNull() {
		Assert.assertNotNull(statusComp.getStatusBasedValues());
	}
	
	@Test
	public void equals_sameValues_true() {
		StatusComponent c1 = getFilledComponent();
		StatusComponent c2 = getFilledComponent();
		
		Assert.assertTrue(c1.equals(c2));
	}
	
	@Test
	public void equals_differentValues_false() {
		StatusComponent c1 = getFilledComponent();
		StatusComponent c2 = getFilledComponent();
		
		c2.getStatusPoints().setDefense(67);
		
		Assert.assertFalse(c1.equals(c2));
	}

	private StatusComponent getFilledComponent() {
		StatusComponent sc = new StatusComponent(1);
		
		sc.setElement(Element.FIRE_2);
		
		StatusPoints sp = new StatusPointsImpl();
		sp.setAgility(10);
		sp.setDexterity(12);
		
		sc.setOriginalStatusPoints(sp);
		
		StatusPoints sp2 = new StatusPointsImpl(sp);
		sp2.setMagicDefense(20);
		sp2.setDefense(25);
		
		sc.setStatusPoints(sp2);
		
		sc.setStatusBasedValues(new StatusBasedValuesImpl(sp2, 5));
		
		ConditionValues cv = new ConditionValues();
		cv.setMaxHealth(100);
		cv.setMaxMana(120);
		cv.setCurrentHealth(23);
		cv.setCurrentMana(45);
		
		sc.setConditionValues(cv);
		
		return sc;
	}
}
