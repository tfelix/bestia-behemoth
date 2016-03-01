package net.bestia.zoneserver.command;

import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;

import net.bestia.util.BestiaConfiguration;
import net.bestia.zoneserver.Zoneserver;
import net.bestia.zoneserver.command.CommandContext.CommandContextBuilder;
import net.bestia.zoneserver.messaging.AccountRegistry;
import net.bestia.zoneserver.messaging.routing.MessageRouter;
import net.bestia.zoneserver.script.ScriptManager;

public class CommandContextTest {
	
	private BestiaConfiguration config = Mockito.mock(BestiaConfiguration.class);
	private Zoneserver server = Mockito.mock(Zoneserver.class);
	private ScriptManager scriptManager = Mockito.mock(ScriptManager.class);
	private MessageRouter router = Mockito.mock(MessageRouter.class);
	private AccountRegistry registry = Mockito.mock(AccountRegistry.class);

	/*
	 * 
	 * private BestiaConfiguration configuration; private Zoneserver server;
	 * private ServiceLocator serviceLocator; private ScriptManager
	 * scriptManager; private MessageRouter messageRouter; private
	 * AccountRegistry accountRegistry;
	 */

	@Test
	public void builder_allSet_ok() {

		final CommandContextBuilder b = getBuilderFull();
		final CommandContext ctx = b.build();

		Assert.assertEquals("Configuration not equal.", config, ctx.getConfiguration());
		Assert.assertEquals("Zonserver not equal.", server, ctx.getServer());
		Assert.assertEquals("ScriptManager not equal.", scriptManager, ctx.getScriptManager());
		Assert.assertNotNull("ServiceLocator must not be null.", ctx.getServiceLocator());
	}

	@Test(expected = IllegalArgumentException.class)
	public void ctor_nullConfig_throw() {
		final CommandContextBuilder b = getBuilderFull();
		b.setConfiguration(null);
		b.build();
	}

	@Test(expected = IllegalArgumentException.class)
	public void ctor_nullServer_throw() {
		final CommandContextBuilder b = getBuilderFull();
		b.setServer(null);
		b.build();
	}

	@Test(expected = IllegalArgumentException.class)
	public void ctor_nullScriptManager_throw() {
		final CommandContextBuilder b = getBuilderFull();
		b.setScriptManager(null);
		b.build();
	}

	@Test(expected = IllegalArgumentException.class)
	public void ctor_nullLocator_throw() {
		final CommandContextBuilder b = getBuilderFull();
		b.setServiceLocator(null);
		b.build();
	}

	public CommandContextBuilder getBuilderFull() {

		CommandContextBuilder b = new CommandContextBuilder();

		b.setAccountRegistry(registry)
				.setConfiguration(config)
				.setScriptManager(scriptManager)
				.setMessageRouter(router)
				.setServer(server);

		return b;
	}
}
