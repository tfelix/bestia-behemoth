package net.bestia.zoneserver.chat;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import static org.mockito.Mockito.*;
import org.mockito.junit.MockitoJUnitRunner;

import net.bestia.messages.chat.ChatMessage;
import net.bestia.model.dao.MapParameterDAO;
import net.bestia.model.domain.Account;
import net.bestia.zoneserver.actor.zone.ZoneAkkaApi;

@RunWith(MockitoJUnitRunner.class)
public class MapParamCommandTest {

	private MapParamCommand cmd;

	@Mock
	private ZoneAkkaApi akkaApi;

	@Mock
	private Account acc;
	
	@Mock
	private MapParameterDAO mapParamDao;

	@Before
	public void setup() {

		cmd = new MapParamCommand(akkaApi, mapParamDao);
	}

	@Test
	public void getHelpText_notEmpty() {
		Assert.assertTrue(cmd.getHelpText().length() > 0);
	}
	
	@Test
	public void isCommand_validText_true() {
		Assert.assertTrue(cmd.isCommand("/mapinfo"));
		Assert.assertTrue(cmd.isCommand("/mapinfo "));
		Assert.assertTrue(cmd.isCommand("/mapinfo bla"));
	}
	
	@Test
	public void isCommand_invalidText_false() {
		Assert.assertFalse(cmd.isCommand(" /mapinfo"));
		Assert.assertFalse(cmd.isCommand(" / mapinfo"));
		Assert.assertFalse(cmd.isCommand(" /mapinfo test"));
	}
	
	@Test
	public void executeCommand_validCommand_sendsMessage() {
		cmd.executeCommand(acc, "/mapinfo");
		verify(akkaApi).sendToClient(any(ChatMessage.class));
	}

}