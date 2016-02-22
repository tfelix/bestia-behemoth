package net.bestia.zoneserver.script;

import static org.mockito.Mockito.*;

import java.util.HashMap;
import java.util.Map;

import javax.script.CompiledScript;
import javax.script.SimpleBindings;


import org.junit.Assert;
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

	@Test
	public void execute_unloadedscript_false() {
		final ScriptManager manager = new ScriptManager();

		Script script = mock(Script.class);
		when(script.getScriptKey()).thenReturn("unknown");
		when(script.execute(any(), any())).thenReturn(true);

		Assert.assertFalse(manager.execute(script));
	}

	@Test
	public void execute_loadedscript_true() {
		final ScriptManager manager = new ScriptManager();
		manager.addScripts(scripts);

		Script script = mock(Script.class);
		when(script.getBindings()).thenReturn(new SimpleBindings());
		when(script.getScriptKey()).thenReturn("known");
		when(script.execute(any(), any())).thenReturn(true);

		boolean result = manager.execute(script);
		Assert.assertTrue(result);
	}
}
