package net.bestia.entity;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;

import javax.script.Compilable;
import javax.script.CompiledScript;
import javax.script.Invocable;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;

import org.junit.Test;

public class ScriptTest {

	@Test
	public void test() throws Exception {

		final ScriptEngine engine = new ScriptEngineManager().getEngineByName("nashorn");

		InputStream in = this.getClass().getClassLoader().getResourceAsStream("script/test.js");

		Reader scriptReader = new InputStreamReader(in);

		final CompiledScript script = ((Compilable) engine).compile(scriptReader);
		script.eval();
		Invocable invocable = (Invocable) script.getEngine();
        invocable.invokeFunction("test");

	}

}
