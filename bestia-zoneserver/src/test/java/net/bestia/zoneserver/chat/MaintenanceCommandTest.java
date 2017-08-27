package net.bestia.zoneserver.chat;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.mockito.Mockito.*;

import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import net.bestia.messages.chat.ChatMessage;
import net.bestia.model.dao.AccountDAO;
import net.bestia.model.domain.Account;
import net.bestia.model.domain.Account.UserLevel;
import net.bestia.model.server.MaintenanceLevel;
import net.bestia.zoneserver.actor.zone.ZoneAkkaApi;
import net.bestia.zoneserver.configuration.RuntimeConfigService;
import net.bestia.zoneserver.service.LoginService;

@RunWith(MockitoJUnitRunner.class)
public class MaintenanceCommandTest {

	private MaintenanceCommand cmd;

	@Mock
	private Account acc;

	@Mock
	private AccountDAO accDao;

	@Mock
	private ZoneAkkaApi akkaApi;

	@Mock
	private LoginService loginService;

	@Mock
	private RuntimeConfigService config;

	@Before
	public void setup() {

		cmd = new MaintenanceCommand(akkaApi, loginService, config);
	}

	@Test
	public void isCommand_okayCommand_true() {
		Assert.assertTrue(cmd.isCommand("/maintenance true"));
		Assert.assertTrue(cmd.isCommand("/maintenance false"));
		Assert.assertTrue(cmd.isCommand("/maintenance blabla"));
	}

	@Test
	public void isCommand_falseCommand_false() {
		Assert.assertFalse(cmd.isCommand("/maintenance9340"));
		Assert.assertFalse(cmd.isCommand("/main9340"));
		Assert.assertFalse(cmd.isCommand("/.maintenance true"));
		Assert.assertFalse(cmd.isCommand("maintenance false"));
	}

	@Test
	public void executeCommand_wrongArgs_sendsMessage() {
		cmd.executeCommand(acc, "/maintenance bla");

		verify(akkaApi).sendToClient(any(ChatMessage.class));
		verify(config, times(0)).setMaintenanceMode(any());
		verify(loginService, times(0)).logoutAllUsersBelow(any());
	}

	@Test
	public void executeCommand_true_switchesServerModeLogoutUsers() {
		cmd.executeCommand(acc, "/maintenance true");
		verify(config).setMaintenanceMode(MaintenanceLevel.PARTIAL);
		verify(loginService).logoutAllUsersBelow(UserLevel.SUPER_GM);
	}
	
	@Test
	public void executeCommand_false_switchesServerModeLogoutUsers() {
		cmd.executeCommand(acc, "/maintenance false");
		verify(config).setMaintenanceMode(MaintenanceLevel.NONE);
		verify(loginService, times(0)).logoutAllUsersBelow(any());
	}

}
