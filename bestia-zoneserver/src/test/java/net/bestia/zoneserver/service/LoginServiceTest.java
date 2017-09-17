package net.bestia.zoneserver.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.*;

import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import akka.testkit.TestProbe;
import net.bestia.entity.Entity;
import net.bestia.entity.EntityService;
import net.bestia.entity.PlayerBestiaEntityFactory;
import net.bestia.entity.PlayerEntityService;
import net.bestia.messages.web.AccountLoginRequest;
import net.bestia.model.dao.AccountDAO;
import net.bestia.model.domain.Account;
import net.bestia.model.domain.Account.UserLevel;
import net.bestia.model.server.MaintenanceLevel;
import net.bestia.model.domain.Password;
import net.bestia.model.domain.PlayerBestia;
import net.bestia.testing.BasicMocks;
import net.bestia.zoneserver.actor.zone.ZoneAkkaApi;
import net.bestia.zoneserver.configuration.RuntimeConfigService;

@RunWith(MockitoJUnitRunner.class)
public class LoginServiceTest {

	private final static long USER_ACC_ID = 134;
	private final static long GM_ACC_ID = 135;
	private final static long USER_BANNED_ACC_ID = 566;
	private final static long EXISTING_ENTITY_ID = 4589;

	private final static String LOGIN_OK_TOKEN = "beste-is-awesome";
	private final static String LOGIN_WRONG_TOKEN = "beste-is-shit";
	private final static String ACC_NAME = "testacc";
	private final static String ACC_EMAIL = "max.muster@testmail.net";
	private final static String VALID_PASSWORD = "helloworld";
	private final static String INVALID_PASSWORD = "penis123";
	private final static String INVALID_ACC_EMAIL = "wrongmail@testmail.net";

	private BasicMocks mocks = new BasicMocks();

	@Mock
	private RuntimeConfigService config;

	@Mock
	private AccountDAO accountDao;

	@Mock
	private ConnectionService connectionService;

	@Mock
	private EntityService entityService;

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
	private PlayerBestia playerBestiaWithEntity;

	@Mock
	private Entity bestiaEntity;

	@Mock
	private PlayerEntityService playerEntityService;

	@Mock
	private Password password;

	@Before
	public void setup() {

		when(userAccount.getName()).thenReturn(ACC_NAME);
		// when(gmAccount.getName()).thenReturn(ACC_NAME);

		when(password.matches(INVALID_PASSWORD)).thenReturn(false);
		when(password.matches(VALID_PASSWORD)).thenReturn(true);

		when(userAccount.getLoginToken()).thenReturn(LOGIN_OK_TOKEN);
		when(userAccount.getUserLevel()).thenReturn(UserLevel.USER);
		when(userAccount.getId()).thenReturn(USER_ACC_ID);

		when(userAccount.getPassword()).thenReturn(password);

		when(gmAccount.getLoginToken()).thenReturn(LOGIN_OK_TOKEN);
		when(gmAccount.getUserLevel()).thenReturn(UserLevel.SUPER_GM);

		when(accountDao.findOne(USER_ACC_ID)).thenReturn(userAccount);
		when(accountDao.findOne(GM_ACC_ID)).thenReturn(gmAccount);
		when(accountDao.findByUsernameOrEmail(ACC_EMAIL)).thenReturn(userAccount);

		when(playerBestiaService.getMaster(USER_ACC_ID)).thenReturn(playerBestia);
		when(playerEntityFactory.build(playerBestia)).thenReturn(bestiaEntity);

		when(playerBestiaWithEntity.getEntityId()).thenReturn(EXISTING_ENTITY_ID);

		when(entityService.getEntity(EXISTING_ENTITY_ID)).thenReturn(bestiaEntity);

		// when(playerEntityService.getMasterEntity(anyLong())).thenReturn(Optional.empty());
		// when(playerEntityService.getMasterEntity(USER_ACC_ID)).thenReturn(Optional.of(bestiaEntity));
		// when(playerEntityService.getMasterEntity(GM_ACC_ID)).thenReturn(Optional.of(bestiaEntity));
		when(playerEntityService.getPlayerEntities(USER_ACC_ID))
				.thenReturn(Stream.of(bestiaEntity).collect(Collectors.toSet()));

		clientConnection = new TestProbe(mocks.actorSystem(), "client");

		loginService = new LoginService(config,
				accountDao,
				playerEntityService,
				connectionService,
				playerBestiaService,
				akkaApi,
				playerEntityFactory,
				entityService);
	}

	@Test(expected = IllegalArgumentException.class)
	public void login_invalidAccId_throws() {
		loginService.login(-123);
	}

	@Test
	public void login_existingEntity_dontSpawnNewEntities() {
		when(playerBestiaService.getMaster(USER_ACC_ID)).thenReturn(playerBestiaWithEntity);

		loginService.login(USER_ACC_ID);

		verify(accountDao).findOne(USER_ACC_ID);
		verify(entityService).getEntity(EXISTING_ENTITY_ID);
		verify(connectionService).connected(USER_ACC_ID, clientConnection.ref().path().address());
		verify(playerEntityFactory, times(0)).build(any());
		verify(akkaApi).sendToClient(any());
		verify(playerBestiaService).getMaster(USER_ACC_ID);
		verify(playerEntityService).putPlayerEntity(bestiaEntity);
	}

	@Test
	public void login_validData_accountIdLoggedIn() {
		loginService.login(USER_ACC_ID);

		verify(accountDao).findOne(USER_ACC_ID);
		verify(connectionService).connected(USER_ACC_ID, clientConnection.ref().path().address());
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
		loginService.login(USER_ACC_ID);
		loginService.logout(USER_ACC_ID);

		verify(connectionService).disconnected(USER_ACC_ID);

		verify(playerEntityService).save(bestiaEntity);
		verify(playerEntityService).removePlayerBestias(USER_ACC_ID);
		verify(entityService).delete(bestiaEntity);
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
		when(config.getMaintenanceMode()).thenReturn(MaintenanceLevel.PARTIAL);
		boolean canLogin = loginService.canLogin(USER_ACC_ID, LOGIN_OK_TOKEN);
		Assert.assertFalse(canLogin);
	}

	@Test
	public void canLogin_serverInMaintenenaceUserLevelSuperGM_true() {
		when(config.getMaintenanceMode()).thenReturn(MaintenanceLevel.PARTIAL);
		boolean canLogin = loginService.canLogin(GM_ACC_ID, LOGIN_OK_TOKEN);
		Assert.assertTrue(canLogin);
	}

	@Test
	public void canLogin_serverInFullMaintenenaceUserLevelSuperGM_false() {
		when(config.getMaintenanceMode()).thenReturn(MaintenanceLevel.FULL);
		boolean canLogin = loginService.canLogin(GM_ACC_ID, LOGIN_OK_TOKEN);
		Assert.assertFalse(canLogin);
	}

	@Test
	public void canLogin_wrongLoginToken_false() {
		boolean login = loginService.canLogin(17396, LOGIN_WRONG_TOKEN);
		Assert.assertFalse(login);
	}

	@Test
	public void createNewLoginToken_wrongUsername_fails() {
		AccountLoginRequest req = new AccountLoginRequest(INVALID_ACC_EMAIL, VALID_PASSWORD);
		AccountLoginRequest token = loginService.setNewLoginToken(req);
		Assert.assertEquals(0, token.getAccountId());
		Assert.assertEquals("", token.getToken());
	}

	@Test
	public void createNewLoginToken_wrongPassword_fails() {
		AccountLoginRequest req = new AccountLoginRequest(ACC_EMAIL, INVALID_PASSWORD);
		AccountLoginRequest token = loginService.setNewLoginToken(req);
		Assert.assertEquals(0, token.getAccountId());
		Assert.assertEquals("", token.getToken());
	}

	@Test
	public void createNewLoginToken_validCredentials_works() {
		AccountLoginRequest req = new AccountLoginRequest(ACC_EMAIL, VALID_PASSWORD);
		AccountLoginRequest token = loginService.setNewLoginToken(req);

		Assert.assertEquals(ACC_EMAIL, token.getUsername());
		Assert.assertEquals(USER_ACC_ID, token.getAccountId());
		Assert.assertNotNull(token.getToken());
		verify(accountDao).save(userAccount);
	}

	@Test(expected = NullPointerException.class)
	public void createNewLoginToken_nullUsername_throws() {
		loginService.setNewLoginToken(null);
	}
}
