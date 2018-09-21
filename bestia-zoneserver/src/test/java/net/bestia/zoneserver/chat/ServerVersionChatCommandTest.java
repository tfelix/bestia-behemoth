package net.bestia.zoneserver.chat;

import net.bestia.messages.MessageApi;
import net.bestia.messages.client.ClientEnvelope;
import net.bestia.model.domain.Account;
import net.bestia.zoneserver.configuration.ZoneserverConfig;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import static org.mockito.Mockito.any;
import static org.mockito.Mockito.verify;

@RunWith(MockitoJUnitRunner.class)
public class ServerVersionChatCommandTest {
	
	private ServerVersionChatCommand cmd;
	
	@Mock
	private MessageApi akkaApi;
	
	@Mock
	private Account acc;
	
	@Mock
	private ZoneserverConfig config;
	
	@Before
	public void setup() {
		
		cmd = new ServerVersionChatCommand(akkaApi, config);
	}

	@Test
	public void isCommand_invalidCommand_false() {
		Assert.assertFalse(cmd.isCommand("/server."));
		Assert.assertFalse(cmd.isCommand("//server"));
		Assert.assertFalse(cmd.isCommand("/serverr "));
	}

	@Test
	public void isCommand_validCommand_true() {
		Assert.assertTrue(cmd.isCommand("/net/bestia/server"));
		Assert.assertTrue(cmd.isCommand("/net/bestia/server "));
		Assert.assertTrue(cmd.isCommand("/server test "));
	}

	@Test
	public void executeCommand_validCommand_sendsServerVersion() {
		cmd.executeCommand(acc, "/version");
		
		verify(akkaApi).send(any(ClientEnvelope.class));
	}

}
