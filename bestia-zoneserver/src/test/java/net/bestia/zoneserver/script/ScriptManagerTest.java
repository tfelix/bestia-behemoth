package net.bestia.zoneserver.script;

import javax.script.Bindings;
import javax.script.CompiledScript;
import javax.script.SimpleBindings;

import static org.mockito.Mockito.*;

import org.junit.Test;

public class ScriptManagerTest {

	@Test
	public void execute_script() {
		ScriptManager manager = new ScriptManager();
		Bindings bindings = new SimpleBindings();
		
		ScriptCompiler cache = mock(ScriptCompiler.class);
		CompiledScript compScript = mock(CompiledScript.class);
		stub(cache.getScript("apple")).toReturn(compScript);
		
		manager.addCache("item", cache, bindings);
		
		Script script = mock(Script.class);
		stub(script.getScriptKey()).toReturn("item");
		stub(script.getName()).toReturn("apple");
		stub(script.getBindings()).toReturn(bindings);
		
		manager.executeScript(script);
	}
	
	@Test(expected = IllegalArgumentException.class)
	public void execute_null_script() {
		ScriptManager manager = new ScriptManager();
		manager.executeScript(null);
	}
	
	@Test(expected = IllegalArgumentException.class)
	public void add_null_script() {
		ScriptManager manager = new ScriptManager();
		manager.addCache(null, null, null);
	}
	
	
}
