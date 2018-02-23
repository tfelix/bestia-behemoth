package net.bestia.zoneserver.script;

import org.junit.Before;
import org.junit.Test;
import static org.mockito.Mockito.*;

import java.io.File;

public class ScriptCacheTest {

	private final static String VALID_SCRIPT = "valid.js";
	private ScriptCache cache;

	private ScriptCompiler compiler;
	private ScriptFileResolver resolver;
	private File validFile = new File("/valid/script/valid.js");

	@Before
	public void setup() {

		compiler = mock(ScriptCompiler.class);
		resolver = mock(ScriptFileResolver.class);

		when(resolver.getScriptFile(eq(VALID_SCRIPT), any(ScriptType.class)))
				.thenReturn(validFile);

		cache = new ScriptCache(compiler, resolver);
	}

	@Test(expected = NullPointerException.class)
	public void addFolder_nullFolder_throws() {
		cache.addFolder(null, ScriptType.ATTACK);
	}

	@Test
	public void addFolder_validFolder_compilesContent() {

	}

	@Test(expected = NullPointerException.class)
	public void getScript_nullName_throws() {
		cache.getScript(ScriptType.ATTACK, null);
	}

	@Test
	public void getScript_validNameAndType_compiles() {
		cache.getScript(ScriptType.ATTACK, VALID_SCRIPT);
		
		verify(compiler).compileScript(validFile);
	}

}
