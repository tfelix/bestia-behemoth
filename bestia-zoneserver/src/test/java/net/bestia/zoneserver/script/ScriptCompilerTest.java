package net.bestia.zoneserver.script;

import java.io.File;
import java.io.IOException;

import org.junit.Assert;
import org.junit.Test;


public class ScriptCompilerTest {
	
	private File scriptFaulty = new File(getClass().getResource("/data/script/item_faulty/apple.groovy").getFile());
	private File scriptOk = new File(getClass().getResource("/data/script/item/apple.groovy").getFile());

	@Test
	public void load_existing_ok() throws IOException {
		ScriptCompiler compiler = new ScriptCompiler();
		String key = "test";
		compiler.load(key, scriptOk);

		Assert.assertNotNull(compiler.getCompiledScripts().get(key));
	}

	@Test
	public void load_faulty_fails() throws IOException {
		ScriptCompiler compiler = new ScriptCompiler();
		String key = "test";
		compiler.load(key, scriptFaulty);
		Assert.assertFalse(compiler.getCompiledScripts().containsKey(key));
	}

	@Test(expected = IOException.class)
	public void load_nonexisting_exception() throws IOException {
		ScriptCompiler compiler = new ScriptCompiler();
		String key = "test";
		File folder = new File("blubber.file");
		compiler.load(key, folder);
	}
	
	@Test(expected = IllegalArgumentException.class)
	public void load_key_null_exception() throws IOException {
		ScriptCompiler compiler = new ScriptCompiler();
		compiler.load("test", null);
	}
	
	@Test(expected = IllegalArgumentException.class)
	public void load_null_file_exception() throws IOException {
		ScriptCompiler compiler = new ScriptCompiler();
		compiler.load(null, scriptOk);
	}
}
