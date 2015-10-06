package net.bestia.zoneserver.script;

import java.io.File;
import java.io.IOException;

import javax.script.Bindings;
import javax.script.CompiledScript;
import javax.script.ScriptException;
import javax.script.SimpleBindings;

import org.junit.Test;
import org.springframework.util.Assert;

import groovy.lang.Closure;

public class TempTest {

	@Test
	public void closureTest() {
		ScriptCompiler cache = new ScriptCompiler();
		File folder = new File(getClass().getResource("/data/script/test").getFile());

		try {
			cache.load(folder);
		} catch (IOException e) {
			e.printStackTrace();
		}

		CompiledScript script = cache.getScript("closure");
		
		Bindings bindings = new SimpleBindings();
		
		try {
			script.eval(bindings);
		} catch (ScriptException e) {	
			e.printStackTrace();
		}
		
		Closure<Void> test = (Closure<Void>) bindings.get("test");
		test.call();
	}
}
