package net.bestia.zoneserver.script;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;

import javax.script.Compilable;
import javax.script.CompiledScript;
import javax.script.Invocable;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;

import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

import net.bestia.model.geometry.CollisionShape;

public class ScriptTest2 {

	public class TestApi implements ScriptApi {
		
		public void setCallback(Runnable obj) {
			System.out.println(obj.toString());
			ScriptEngine eng = (ScriptEngine) obj;
			//eng.g
			
			obj.run();
			obj.run();
			obj.run();
		}
		
		public void call1() {
			System.out.println("call 1");
		}

		@Override
		public void info(String text) {
			System.out.println(text);
		}

		@Override
		public void debug(String text) {
			System.out.println(text);
		}

		@Override
		public ScriptEntityWrapper createSpellEntity(CollisionShape shape, String spriteName, int baseDuration) {
			// TODO Auto-generated method stub
			return null;
		}
	}
	
	private ScriptApi scriptApi = new TestApi();
	
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
	
	@Test
	public void test2() throws ScriptException, FileNotFoundException {
		
		ScriptEngineManager manager = new ScriptEngineManager();
		ScriptEngine engine = manager.getEngineByName("nashorn");
		
		engine.eval("print(Math.random())");
		
	}
	
	@Test
	public void test3() throws ScriptException, FileNotFoundException, NoSuchMethodException {
		
		ScriptEngineManager manager = new ScriptEngineManager();
		ScriptEngine engine = manager.getEngineByName("nashorn");
		engine.put("api", scriptApi);
		
		ClassLoader classLoader = getClass().getClassLoader();
		File testFile = new File(classLoader.getResource("script/test2.js").getFile());
		
		CompiledScript compiled = ((Compilable) engine).compile(new FileReader(testFile));
		
		compiled.eval(engine.getContext());
		System.out.println("====");
		((Invocable) compiled.getEngine()).invokeFunction("test");
		
		//engine.eval(new FileReader(testFile));
		
	}
}
