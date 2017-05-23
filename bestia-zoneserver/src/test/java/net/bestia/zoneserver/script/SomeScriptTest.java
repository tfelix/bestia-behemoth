package net.bestia.zoneserver.script;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;

import javax.script.Bindings;
import javax.script.Compilable;
import javax.script.CompiledScript;
import javax.script.Invocable;
import javax.script.ScriptContext;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;
import javax.script.SimpleBindings;

import org.junit.Test;

public class SomeScriptTest {
	
	@Test
	public void bindings() throws FileNotFoundException, ScriptException, NoSuchMethodException {
		
		// Setup
		final ScriptEngineManager manager = new ScriptEngineManager();

		// Setup the global bindings.
		Bindings globalBindings = new SimpleBindings();

		globalBindings.put("GLOB", "Hello from Global");

		manager.setBindings(globalBindings);

		ScriptEngine engine = manager.getEngineByName("nashorn");
		
		ClassLoader classLoader = getClass().getClassLoader();
		File testFile1 = new File(classLoader.getResource("script/test.js").getFile());
		File testFile2 = new File(classLoader.getResource("script/test2.js").getFile());
		
		final CompiledScript script1 = ((Compilable) engine).compile(new FileReader(testFile1));
		//script1.eval(engine.getContext());
		
		final CompiledScript script2 = ((Compilable) engine).compile(new FileReader(testFile2));
		//script2.eval(engine.getContext());

		// Bindings müssen vor jedem Call erneut gesetzt werden (oder als Binding gespeichert)

		final Bindings scriptBindings = script1.getEngine().getBindings(ScriptContext.ENGINE_SCOPE);
		scriptBindings.put("MYSCRIPT", "test1.js");
		scriptBindings.put("MYTYPE", "status");
		script1.eval(script1.getEngine().getContext());
		((Invocable) script1.getEngine()).invokeFunction("main");
		
		final Bindings scriptBindings2 = script2.getEngine().getBindings(ScriptContext.ENGINE_SCOPE);
		scriptBindings2.put("MYSCRIPT", "test2.js");
		scriptBindings2.put("MYTYPE", "attack");
		script2.eval(script1.getEngine().getContext());
		((Invocable) script2.getEngine()).invokeFunction("main");
		
	}

}
