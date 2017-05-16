package net.bestia.zoneserver.script;

import org.junit.Assert;
import org.junit.Test;

public class ScriptFileResolverTest {

	private ScriptFileResolver resolver = new ScriptFileResolver();

	@Test
	public void getGlobalScriptFile_ok() {
		Assert.assertTrue(resolver.getGlobalScriptFile().toString().endsWith("globalContext.js"));
	}

	@Test(expected = NullPointerException.class)
	public void getScriptFile_nullScriptName_throws() {
		resolver.getScriptFile(null, ScriptType.ITEM);
	}

	@Test
	public void getScriptFile_scriptNameAndType_works() {
		String scriptFile = resolver.getScriptFile("test.js", ScriptType.ATTACK).getAbsolutePath();
		Assert.assertTrue(scriptFile.contains(("attack")));
		Assert.assertTrue(scriptFile.contains(("test.js")));
	}

	@Test
	public void getScriptFile_scriptNameWithoutJs_works() {
		String scriptFile = resolver.getScriptFile("test", ScriptType.ATTACK).getAbsolutePath();
		Assert.assertTrue(scriptFile.contains(("attack")));
		Assert.assertTrue(scriptFile.contains(("test.js")));
	}

}
