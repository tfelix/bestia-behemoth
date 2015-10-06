package net.bestia.zoneserver.script;

import static org.mockito.Mockito.*;

import javax.script.CompiledScript;
import javax.script.SimpleBindings;


import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

public class ScriptManagerTest {

	private static ScriptCompiler compiler = mock(ScriptCompiler.class);
	
	@BeforeClass
	public static void init() {
		
		CompiledScript compScript = mock(CompiledScript.class);
		
		when(compiler.getScript("known")).thenReturn(compScript);
	}
	

	@Test(expected = IllegalArgumentException.class)
	public void setStdBindings_null_exception() {
		final ScriptManager manager = new ScriptManager(compiler);
		manager.setStdBindings(null);
	}

	@Test
	public void setStdBindings_notnull_noexception() {
		final ScriptManager manager = new ScriptManager(compiler);
		manager.setStdBindings(new SimpleBindings());
	}

	@Test
	public void execute_unloadedscript_false() {
		final ScriptManager manager = new ScriptManager(compiler);

		Script script = mock(Script.class);
		when(script.getScriptKey()).thenReturn("unknown");
		when(script.execute(any(), any())).thenReturn(true);

		Assert.assertFalse(manager.execute(script));
	}

	@Test
	public void execute_loadedscript_true() {
		final ScriptManager manager = new ScriptManager(compiler);

		Script script = mock(Script.class);
		when(script.getScriptKey()).thenReturn("known");

		Assert.assertTrue(manager.execute(script));
	}
}
