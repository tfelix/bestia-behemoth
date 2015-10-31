package net.bestia.zoneserver.command;

import net.bestia.messages.BestiaMoveMessage;
import net.bestia.messages.LoginBroadcastMessage;
import net.bestia.messages.Message;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;


@RunWith(MockitoJUnitRunner.class)
public class RoutedECSCommandFactoryTest {
	
	@Mock
	private CommandContext ctx;

	@Test
	public void return_input_command_on_input_msg() {
		ECSCommandFactory fac = new ECSCommandFactory(ctx);
		
		final Message msg = new BestiaMoveMessage();
		final Command cmd = fac.getCommand(msg);
		Assert.assertTrue("Should be instance of a InputCommand", cmd instanceof InputCommand);
	}
	
	@Test
	public void return_real_command() {
		ECSCommandFactory fac = new ECSCommandFactory(ctx);
		
		Message msg = new LoginBroadcastMessage(1L);
		
		Command cmd = fac.getCommand(msg);
		Assert.assertFalse("Instance of a InputCommand. Should not be!", cmd instanceof InputCommand);
	}
}
