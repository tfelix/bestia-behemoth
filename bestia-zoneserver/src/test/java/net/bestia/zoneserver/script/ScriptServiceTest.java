package net.bestia.zoneserver.script;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;

import javax.script.Compilable;
import javax.script.CompiledScript;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

@RunWith(SpringRunner.class)
@SpringBootTest
public class ScriptServiceTest {
	
	@Autowired
	private ScriptApi scriptApi;
	
	@Test
	public void call_script_attaches_callbacks() throws ScriptException, FileNotFoundException {
		
		ScriptEngineManager manager = new ScriptEngineManager();
		ScriptEngine engine = manager.getEngineByName("nashorn");
		engine.put("Bestia", scriptApi);
		
		ClassLoader classLoader = getClass().getClassLoader();
		File testFile = new File(classLoader.getResource("script/attack/create_aoe_dmg.js").getFile());
		engine.eval(new FileReader(testFile));
		
		try {
			Thread.sleep(10000);
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

}
