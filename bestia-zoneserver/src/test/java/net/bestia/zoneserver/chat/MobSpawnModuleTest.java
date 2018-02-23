package net.bestia.zoneserver.chat;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import net.entity.factory.MobFactory;
import net.bestia.messages.MessageApi;
import bestia.model.domain.Account;

@RunWith(MockitoJUnitRunner.class)
public class MobSpawnModuleTest {

	private MobSpawnModule module;

	@Mock
	private MessageApi akkaApi;

	@Mock
	private MobFactory mobFactory;

	@Mock
	private Account acc;

	@Before
	public void setup() {

		module = new MobSpawnModule(akkaApi, mobFactory);
	}

	@Test
	public void isCommand_falseCommand_false() {
		Assert.assertFalse(module.isCommand("mob."));
		Assert.assertFalse(module.isCommand(".mob"));
		Assert.assertFalse(module.isCommand("/mobb"));
	}

	@Test
	public void isCommand_validCommand_true() {
		Assert.assertTrue(module.isCommand("mob bla"));
		Assert.assertTrue(module.isCommand("mob blob 10 10"));
		Assert.assertTrue(module.isCommand("mob doommaster 10 10"));
	}

	@Test
	public void getHelpText_notEmpty() {
		Assert.assertTrue(module.getHelpText().length() > 0);
	}

	@Test
	public void executeCheckedCommand_validCommand_spawnsMob() {

		module.executeCommand(acc, "mob blob 10 10");
	}

	@Test
	public void executeCheckedCommand_invalidCommand_sendsUsageMessage() {

		module.executeCommand(acc, "mobb blob 10 10");
	}

}
