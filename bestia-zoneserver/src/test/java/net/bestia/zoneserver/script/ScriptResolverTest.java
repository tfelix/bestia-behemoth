package net.bestia.zoneserver.script;

import org.junit.Assert;
import org.junit.Test;

public class ScriptResolverTest {

	private ScriptResolver resolver = new ScriptResolver();

	@Test
	public void resolveScriptIdent_rootName_rootIdent() {
		ScriptAnchor ident1 = resolver.resolveScriptIdent("test");
		ScriptAnchor ident2 = resolver.resolveScriptIdent("test.js");

		// Equal idents.
		Assert.assertEquals(ident1, ident2);
		Assert.assertEquals(ScriptType.NONE, ident1.getType());
		Assert.assertEquals("main", ident1.getFunctionName());
		Assert.assertEquals("test", ident1.getScriptName());
	}

	@Test
	public void resolveScriptIdent_statusScript_validIdent() {
		ScriptAnchor ident = resolver.resolveScriptIdent("/status_effect/test");

		Assert.assertEquals(ScriptType.STATUS_EFFECT, ident.getType());
		Assert.assertEquals("main", ident.getFunctionName());
		Assert.assertEquals("status_effect/test", ident.getScriptName());
	}

	@Test
	public void resolveScriptIdent_attackScript_validIdent() {
		ScriptAnchor ident = resolver.resolveScriptIdent("/attack/test");

		Assert.assertEquals(ScriptType.ATTACK, ident.getType());
		Assert.assertEquals("main", ident.getFunctionName());
		Assert.assertEquals("attack/test", ident.getScriptName());
	}

	@Test
	public void resolveScriptIdent_statusScriptWithCallback_validIdent() {
		ScriptAnchor ident = resolver.resolveScriptIdent("/attack/test:callback");

		Assert.assertEquals(ScriptType.ATTACK, ident.getType());
		Assert.assertEquals("callback", ident.getFunctionName());
		Assert.assertEquals("attack/test", ident.getScriptName());
	}

	@Test(expected = IllegalArgumentException.class)
	public void resolveScriptIdent_invaliedScriptPath_throws() {
		resolver.resolveScriptIdent("blabla/test:callback");
	}

}
