package net.bestia.entity.component;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

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
		Assert.assertNotNull(statusComp.getUnmodifiedStatusPoints());
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

}
