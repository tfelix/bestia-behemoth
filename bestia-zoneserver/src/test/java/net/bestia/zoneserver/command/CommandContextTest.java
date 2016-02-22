package net.bestia.zoneserver.command;

import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;

import net.bestia.util.BestiaConfiguration;
import net.bestia.zoneserver.Zoneserver;
import net.bestia.zoneserver.script.ScriptManager;

public class CommandContextTest {

	@Test
	public void set_get_test() {
		final BestiaConfiguration config = Mockito.mock(BestiaConfiguration.class);
		final Zoneserver server = Mockito.mock(Zoneserver.class);
		final ScriptManager scriptManager = Mockito.mock(ScriptManager.class);
		
		final CommandContext ctx = new CommandContext(config, server, scriptManager);
		
		Assert.assertEquals("Configuration not equal.", config, ctx.getConfiguration());
		Assert.assertEquals("Zonserver not equal.", server, ctx.getServer());
		Assert.assertEquals("ScriptManager not equal.", scriptManager, ctx.getScriptManager());
		Assert.assertNotNull("ServiceLocator must not be null.", ctx.getServiceLocator());
	}
	
	@Test(expected=IllegalArgumentException.class)
	public void ctor_nullConfig_throw() {
		final Zoneserver server = Mockito.mock(Zoneserver.class);
		final ScriptManager scriptManager = Mockito.mock(ScriptManager.class);
		
		new CommandContext(null, server, scriptManager);
	}
	
	@Test(expected=IllegalArgumentException.class)
	public void ctor_nullServer_throw() {
		final BestiaConfiguration config = Mockito.mock(BestiaConfiguration.class);
		final ScriptManager scriptManager = Mockito.mock(ScriptManager.class);
		
		new CommandContext(config, null, scriptManager);
	}
	
	@Test(expected=IllegalArgumentException.class)
	public void ctor_nullManager_throw() {
		final BestiaConfiguration config = Mockito.mock(BestiaConfiguration.class);
		final Zoneserver server = Mockito.mock(Zoneserver.class);
		
		new CommandContext(config, server, null);
	}
	
	@Test(expected=IllegalArgumentException.class)
	public void ctor_nullLocator_throw() {
		final BestiaConfiguration config = Mockito.mock(BestiaConfiguration.class);
		final Zoneserver server = Mockito.mock(Zoneserver.class);
		final ScriptManager scriptManager = Mockito.mock(ScriptManager.class);
		
		new CommandContext(config, server, scriptManager, null);
	}
}
