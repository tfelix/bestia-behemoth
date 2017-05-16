package net.bestia.zoneserver.script;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;

import org.junit.Test;

public class ScriptServiceTest {
	
	public class TestApi {
		
		public void setCallback(Runnable obj) {
			System.out.println("test");
			
			obj.run();
			obj.run();
			obj.run();
		}
		
		public void call1() {
			System.out.println("call 1");
		}
	}
	
	@Test
	public void test() throws ScriptException, FileNotFoundException {
		
		ScriptEngineManager manager = new ScriptEngineManager();
		ScriptEngine engine = manager.getEngineByName("nashorn");
		
		ClassLoader classLoader = getClass().getClassLoader();
		File file = new File(classLoader.getResource("script/globalContext.js").getFile());
		File testFile = new File(classLoader.getResource("script/test.js").getFile());
		
		TestApi api = new TestApi();
		engine.put("api", api);
		
		engine.eval(new FileReader(file));
		engine.eval(new FileReader(testFile));
		
	}

}
