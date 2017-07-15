package net.bestia.zoneserver.actor.entity;

import org.junit.Assert;
import org.junit.Test;


public class EntityActorTest {
	
	@Test(expected=IllegalArgumentException.class)
	public void getActorName_negativeId_throws() {
		EntityActor.getActorName(-1);
	}
	
	@Test
	public void getActorName_id_returnsName() {
		String name = EntityActor.getActorName(1);
		Assert.assertTrue(name.matches("entity-\\d"));
	}

}
