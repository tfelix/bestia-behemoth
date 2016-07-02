package net.bestia.zoneserver.script;

import static org.mockito.Mockito.mock;

import java.util.HashMap;
import java.util.Map;

import javax.script.CompiledScript;
import javax.script.SimpleBindings;

import org.junit.BeforeClass;
import org.junit.Test;

public class ScriptManagerTest {

	private static Map<String, CompiledScript> scripts = new HashMap<>();
	
	@BeforeClass
	public static void init() {		
		CompiledScript compScript = mock(CompiledScript.class);	
		scripts.put("known", compScript);
	}

	@Test(expected = IllegalArgumentException.class)
	public void setStdBindings_null_exception() {
		final ScriptManager manager = new ScriptManager();
		manager.setStandardBindings(null);
	}

	@Test
	public void setStdBindings_notnull_noexception() {
		final ScriptManager manager = new ScriptManager();
		manager.setStandardBindings(new SimpleBindings());
	}
}
