package net.bestia.entity;

import org.junit.Assert;
import org.junit.Test;

import net.bestia.entity.component.Component;

public class EntityTest {

	private static class MyComponent extends Component {
		public MyComponent(long id) {
			super(id, 0);
		}
	}

	private static class MyComponent2 extends Component {
		public MyComponent2(long id) {
			super(id, 0);
		}
	}

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
		e.addComponent(new MyComponent(1));
		Assert.assertEquals(1, e.getComponentId(MyComponent.class));
	}

	@Test
	public void removeComponent_addedComponent_isRemoved() {
		Entity e = new Entity(1337);
		Component c = new MyComponent(1);
		e.addComponent(c);
		e.removeComponent(c);
		Assert.assertEquals(0, e.getComponentId(MyComponent.class));
	}

	@Test
	public void removeComponent_unknownComponent_nothingHappens() {
		Entity e = new Entity(1337);
		Component c = new MyComponent(1);
		e.addComponent(c);
		e.removeComponent(new MyComponent2(2));
		Assert.assertEquals(1, e.getComponentId(MyComponent.class));
	}
	
	@Test(expected = NullPointerException.class)
	public void getComponentId_null_throws() {
		Entity e = new Entity(1337);
		Component c = new MyComponent(1);
		e.addComponent(c);
		e.getComponentId(null);
	}
}
