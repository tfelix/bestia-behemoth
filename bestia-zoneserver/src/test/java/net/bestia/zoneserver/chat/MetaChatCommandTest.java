package net.bestia.zoneserver.chat;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

import net.bestia.model.domain.Account;
import net.bestia.model.domain.Account.UserLevel;

@RunWith(MockitoJUnitRunner.class)
public class MetaChatCommandTest {

	private static final String CMD_STR = "/test";
	
	@Mock
	private BaseChatCommand module;
	
	@Mock
	private Account acc;
	
	@Mock
	private BaseChatCommand moduleLower;
	
	private MetaChatCommand cmd;

	@Before
	public void setup() {
		
		Mockito.when(module.requiredUserLevel()).thenReturn(UserLevel.ADMIN);
		Mockito.when(moduleLower.requiredUserLevel()).thenReturn(UserLevel.USER);
		
		cmd = new MetaChatCommand(CMD_STR);
	}

	@Test(expected = NullPointerException.class)
	public void ctor_1argNull_throws() {
		new MetaChatCommand(null);
	}

	@Test(expected = NullPointerException.class)
	public void addCommandModule_null_throws() {
		cmd.addCommandModule(null);
	}

	@Test
	public void addCommandModule_module_wasAdded() {
		cmd.addCommandModule(module);
	}

	@Test
	public void isCommand_validPrefix_true() {
		Assert.assertTrue(cmd.isCommand(CMD_STR));
	}

	@Test
	public void isCommand_invalidPrefix_false() {
		Assert.assertFalse(cmd.isCommand("/ test"));
		Assert.assertFalse(cmd.isCommand("test"));
		Assert.assertFalse(cmd.isCommand("/test."));
	}

	@Test
	public void requiredUserLevel_lowestLevel() {
		cmd.addCommandModule(module);
		Assert.assertEquals(UserLevel.ADMIN, cmd.requiredUserLevel());
		cmd.addCommandModule(moduleLower);
		Assert.assertEquals(UserLevel.USER, cmd.requiredUserLevel());
	}

	@Test
	public void executeCommand_validCmdString_wasExecuted() {
		cmd.executeCommand(acc, CMD_STR);
		Mockito.verify(module).executeCommand(acc, CMD_STR);
	}
}
