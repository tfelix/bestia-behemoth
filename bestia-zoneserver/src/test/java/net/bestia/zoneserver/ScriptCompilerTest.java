package net.bestia.zoneserver;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;

import javax.script.CompiledScript;
import javax.script.ScriptException;

import org.junit.Test;

import net.bestia.zoneserver.script.ScriptCompiler;

public class ScriptCompilerTest {
	
	private ScriptCompiler compiler = new ScriptCompiler();
	
	@Test
	public void test() throws Exception {
		URL url = this.getClass().getResource("/script/helloworld.js");
		compiler.load("test", new File(url.toURI()));
		CompiledScript sc = compiler.getCompiledScripts("test");
		sc.eval();
	}
	
	

}
