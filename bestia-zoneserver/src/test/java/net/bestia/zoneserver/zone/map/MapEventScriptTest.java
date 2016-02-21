package net.bestia.zoneserver.zone.map;

import net.bestia.zoneserver.zone.shape.CollisionShape;
import net.bestia.zoneserver.zone.shape.Rect;

import org.junit.Test;

public class MapEventScriptTest {
	
	private final CollisionShape shape = new Rect(10, 10);

	@Test
	public void ctor_nullName_execption() {
		new MapScriptTemplate(null, shape, 10);
	}

	@Test
	public void ctor_emptyName_execption() {
		new MapScriptTemplate("", shape, 10);
	}

	@Test
	public void ctor_nullShape_execption() {
		new MapScriptTemplate("test", null, 10);
	}

	@Test(expected=IllegalArgumentException.class)
	public void ctor_negativeTick_execption() {
		new MapScriptTemplate("test", shape, -10);
	}
	
	@Test
	public void ctor_correct() {
		new MapScriptTemplate("test", shape, 10);
		new MapScriptTemplate("test", shape);
	}
}
