package net.bestia.zoneserver.service;

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyLong;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import akka.testkit.TestProbe;
import net.bestia.model.dao.AccountDAO;
import net.bestia.model.domain.Account;
import net.bestia.model.domain.Account.UserLevel;
import net.bestia.model.domain.PlayerBestia;
import net.bestia.zoneserver.BasicMocks;
import net.bestia.zoneserver.actor.ZoneAkkaApi;
import net.bestia.zoneserver.configuration.RuntimeConfigurationService;
import net.bestia.zoneserver.entity.Entity;
import net.bestia.zoneserver.entity.PlayerBestiaEntityFactory;
import net.bestia.zoneserver.entity.PlayerEntityService;

@RunWith(MockitoJUnitRunner.class)
public class LoginServiceTest {

	private final static long USER_ACC_ID = 134;
	private final static long GM_ACC_ID = 135;
	private final static long USER_BANNED_ACC_ID = 566;
	private final static String LOGIN_OK_TOKEN = "beste-is-awesome";
	private final static String LOGIN_WRONG_TOKEN = "beste-is-shit";
	private final static String ACC_NAME = "testacc";

	private BasicMocks mocks = new BasicMocks();

	@Mock
	private RuntimeConfigurationService config;

	@Mock
	private AccountDAO accountDao;

	@Mock
	private ConnectionService connectionService;

	@Mock
	private PlayerBestiaService playerBestiaService;

	@Mock
	private ZoneAkkaApi akkaApi;

	@Mock
	private PlayerBestiaEntityFactory playerEntityFactory;

	private LoginService loginService;
	private TestProbe clientConnection;

	@Mock
	private Account userAccount;

	@Mock
	private Account gmAccount;

	@Mock
	private PlayerBestia playerBestia;

	@Mock
	private Entity bestiaEntity;
	private PlayerEntityService playerEntityService;

	@Before
	public void setup() {

		when(userAccount.getName()).thenReturn(ACC_NAME);
		when(gmAccount.getName()).thenReturn(ACC_NAME);

		when(userAccount.getLoginToken()).thenReturn(LOGIN_OK_TOKEN);
		when(userAccount.getUserLevel()).thenReturn(UserLevel.USER);

		when(gmAccount.getLoginToken()).thenReturn(LOGIN_OK_TOKEN);
		when(gmAccount.getUserLevel()).thenReturn(UserLevel.SUPER_GM);

		when(accountDao.findOne(USER_ACC_ID)).thenReturn(userAccount);
		when(accountDao.findOne(GM_ACC_ID)).thenReturn(gmAccount);

		when(playerBestiaService.getMaster(USER_ACC_ID)).thenReturn(playerBestia);
		when(playerEntityFactory.build(playerBestia)).thenReturn(bestiaEntity);

		when(playerEntityService.getMasterEntity(anyLong())).thenReturn(Optional.empty());
		when(playerEntityService.getMasterEntity(USER_ACC_ID)).thenReturn(Optional.of(bestiaEntity));
		when(playerEntityService.getMasterEntity(GM_ACC_ID)).thenReturn(Optional.of(bestiaEntity));
		when(playerEntityService.getPlayerEntities(USER_ACC_ID))
				.thenReturn(Stream.of(bestiaEntity).collect(Collectors.toSet()));

		clientConnection = new TestProbe(mocks.actorSystem(), "client");

		loginService = new LoginService(config, 
				accountDao,
				playerEntityService, 
				connectionService, 
				playerBestiaService,
				akkaApi, 
				playerEntityFactory);
	}

	@Test(expected = IllegalArgumentException.class)
	public void login_invalidAccId_throws() {
		loginService.login(-123, clientConnection.ref());
	}

	@Test(expected = NullPointerException.class)
	public void login_invalidActorRef_throws() {
		loginService.login(13, null);
	}

	@Test
	public void login_validData_accountIdLoggedIn() {
		loginService.login(USER_ACC_ID, clientConnection.ref());

		verify(accountDao).findOne(USER_ACC_ID);
		verify(connectionService).addClient(USER_ACC_ID, clientConnection.ref().path());
		verify(playerEntityFactory).build(any());
		verify(akkaApi).sendToClient(any());
		verify(playerBestiaService).getMaster(USER_ACC_ID);
		verify(playerEntityService).putPlayerEntity(bestiaEntity);
	}

	@Test(expected = IllegalArgumentException.class)
	public void logout_invalidAccId_throws() {
		loginService.logout(-168239);
	}

	@Test
	public void logout_validLoggedInAccId_accountIsLoggedOut() {
		loginService.login(USER_ACC_ID, clientConnection.ref());
		loginService.logout(USER_ACC_ID);

		verify(connectionService).removeClient(USER_ACC_ID);

		verify(playerEntityService).save(bestiaEntity);
		verify(playerEntityService).removePlayerBestias(USER_ACC_ID);
	}

	@Test
	public void canLogin_invalidAcc_false() {
		boolean login = loginService.canLogin(17396, LOGIN_OK_TOKEN);
		Assert.assertFalse(login);
	}

	@Test(expected = NullPointerException.class)
	public void canLogin_nullToken_throws() {
		loginService.canLogin(123, null);
	}

	@Test
	public void canLogin_validDataUserCanLogin_true() {
		boolean canLogin = loginService.canLogin(USER_ACC_ID, LOGIN_OK_TOKEN);
		Assert.assertTrue(canLogin);
	}

	@Test
	public void canLogin_userBanned_false() {
		boolean canLogin = loginService.canLogin(USER_BANNED_ACC_ID, LOGIN_OK_TOKEN);
		Assert.assertFalse(canLogin);
	}

	@Test
	public void canLogin_serverInMaintenenace_false() {
		when(config.isMaintenanceMode()).thenReturn(true);
		boolean canLogin = loginService.canLogin(USER_ACC_ID, LOGIN_OK_TOKEN);
		Assert.assertFalse(canLogin);
	}

	@Test
	public void canLogin_serverInMaintenenaceUserLevelSuperGM_true() {
		when(config.isMaintenanceMode()).thenReturn(true);
		boolean canLogin = loginService.canLogin(GM_ACC_ID, LOGIN_OK_TOKEN);
		Assert.assertTrue(canLogin);
	}

	@Test
	public void canLogin_wrongLoginToken_false() {
		boolean login = loginService.canLogin(17396, LOGIN_WRONG_TOKEN);
		Assert.assertFalse(login);
	}

}
