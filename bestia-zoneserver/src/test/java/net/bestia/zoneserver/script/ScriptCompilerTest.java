package net.bestia.zoneserver.script;

import java.io.File;
import java.io.IOException;

import org.junit.Test;
import org.springframework.util.Assert;

public class ScriptCompilerTest {

	@Test
	public void load_scripts_from_folder() throws IOException {
		ScriptCompiler compiler = new ScriptCompiler();
		
		File folder = new File(getClass().getResource("/data/script/item").getFile());
		
		compiler.load(folder);
		
		Assert.notNull(compiler.getScript("apple"));
	}
	
	@Test
	public void log_on_faulty_script() throws IOException {
		ScriptCompiler compiler = new ScriptCompiler();
		
		File folder = new File(getClass().getResource("/data/script/item_faulty").getFile());
		
		compiler.load(folder);
		
		Assert.isNull(compiler.getScript("apple"));
	}
	
	@Test
	public void null_on_unknown_script() throws IOException {
		ScriptCompiler compiler = new ScriptCompiler();
		
		File folder = new File(getClass().getResource("/data/script/item").getFile());
		
		compiler.load(folder);
		
		Assert.isNull(compiler.getScript("apple123"));
	}
}
