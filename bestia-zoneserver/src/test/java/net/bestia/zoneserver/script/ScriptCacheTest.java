package net.bestia.zoneserver.script;

import java.io.File;
import java.io.IOException;

import org.junit.Test;
import org.springframework.util.Assert;

public class ScriptCacheTest {

	@Test
	public void load_scripts_from_folder() throws IOException {
		ScriptCache cache = new ScriptCache();
		
		File folder = new File(getClass().getResource("/data/script/item").getFile());
		
		cache.load(folder);
		
		Assert.notNull(cache.getScript("apple"));
	}
	
	@Test
	public void log_on_faulty_script() throws IOException {
		ScriptCache cache = new ScriptCache();
		
		File folder = new File(getClass().getResource("/data/script/item_faulty").getFile());
		
		cache.load(folder);
		
		Assert.isNull(cache.getScript("apple"));
	}
	
	@Test
	public void null_on_unknown_script() throws IOException {
		ScriptCache cache = new ScriptCache();
		
		File folder = new File(getClass().getResource("/data/script/item").getFile());
		
		cache.load(folder);
		
		Assert.isNull(cache.getScript("apple123"));
	}
}
