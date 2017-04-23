package net.bestia.zoneserver.entity;

import org.junit.Assert;
import org.junit.Test;

import net.bestia.zoneserver.entity.components.Component;
import net.bestia.zoneserver.entity.components.PositionComponent;
import net.bestia.zoneserver.entity.components.VisibleComponent;

public class EntityTest {

	@Test
	public void ctor_id_works() {
		Entity e = new Entity(1337);
		Assert.assertEquals(1337, e.getId());
	}

	@Test(expected = NullPointerException.class)
	public void addComponent_null_throws() {
		Entity e = new Entity(1337);
		e.addComponent(null);
	}

	@Test
	public void addComponent_component_works() {
		Entity e = new Entity(1337);
		e.addComponent(new PositionComponent(1));
	}

	@Test
	public void removeComponent_addedComponent_isRemoved() {
		Entity e = new Entity(1337);
		Component c = new PositionComponent(1);
		e.addComponent(c);
		e.removeComponent(c);
		Assert.assertEquals(0, e.getComponentId(PositionComponent.class));
	}

	@Test
	public void removeComponent_unknownComponent_nothingHappens() {
		Entity e = new Entity(1337);
		Component c = new PositionComponent(1);
		e.addComponent(c);
		e.removeComponent(new VisibleComponent(2));
		Assert.assertEquals(1, e.getComponentId(PositionComponent.class));
	}
	
	@Test(expected = NullPointerException.class)
	public void getComponentId_null_throws() {
		Entity e = new Entity(1337);
		Component c = new PositionComponent(1);
		e.addComponent(c);
		e.getComponentId(null);
	}
}
