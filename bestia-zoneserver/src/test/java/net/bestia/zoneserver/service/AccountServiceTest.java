package net.bestia.zoneserver.service;

import static org.mockito.Mockito.*;
import static org.mockito.Mockito.when;

import org.junit.Before;
import org.junit.Test;

import net.bestia.model.dao.AccountDAO;
import net.bestia.model.dao.BestiaDAO;
import net.bestia.model.dao.PlayerBestiaDAO;
import net.bestia.model.domain.Account;
import net.bestia.model.domain.Bestia;
import net.bestia.model.domain.PlayerBestia;

public class AccountServiceTest {

	private static final String EXISTING_MAIL = "thomas.exists@tfelix.de";
	private static final String EXISTING_MASTER_NAME = "IgnatzDerDicke";
	
	private static final String NOT_EXISTING_PASSWORD = "notexisting";
	private static final String EXISTING_PASSWORD = "existing";
	
	private AccountService accService;

	private AccountDAO accountDao;
	private PlayerBestiaDAO playerBestiaDao;
	private BestiaDAO bestiaDao;
	private ConnectionService connectionService;
	
	private Account account;
	private Bestia bestia;
	private PlayerBestia masterBestia;

	@Before
	public void setup() {

		bestia = mock(Bestia.class);
		
		accountDao = mock(AccountDAO.class);
		account = mock(Account.class);
		masterBestia = mock(PlayerBestia.class);
		
		playerBestiaDao = mock(PlayerBestiaDAO.class);
		bestiaDao = mock(BestiaDAO.class);
		connectionService = mock(ConnectionService.class);
		
		when(bestiaDao.findOne(1)).thenReturn(bestia);
		when(accountDao.findByEmail(EXISTING_MAIL)).thenReturn(account);
		when(playerBestiaDao.findMasterBestiaWithName(EXISTING_MASTER_NAME)).thenReturn(masterBestia);

		accService = new AccountService(accountDao, playerBestiaDao, bestiaDao, connectionService);
	}

	@Test(expected = IllegalArgumentException.class)
	public void createNewAccount_noMail_fail() {
		accService.createNewAccount("", "Ignatz", "test123");
	}

	@Test(expected = IllegalArgumentException.class)
	public void createNewAccount_nullMail_fail() {
		accService.createNewAccount(null, "Ignatz", "test123");
	}

	@Test(expected = IllegalArgumentException.class)
	public void createNewAccount_duplicateMail_fail() {
		accService.createNewAccount(EXISTING_MAIL, "Ignatz2", "test123");
	}

	@Test
	public void createNewAccount_ok_success() {
		accService.createNewAccount("thomas.felix@tfelix.de", "Ignatz", "test123");
	}

	@Test(expected = IllegalArgumentException.class)
	public void createNewAccount_noMasterName_fail() {
		accService.createNewAccount("thomas.felix@tfelix.de", "", "test123");
	}

	@Test(expected = IllegalArgumentException.class)
	public void createNewAccount_nullMasterName_fail() {
		accService.createNewAccount("thomas.felix@tfelix.de", null, "test123");
	}

	@Test(expected = IllegalArgumentException.class)
	public void createNewAccount_duplicateMasterName_fail() {
		accService.createNewAccount("thomas.felix2@tfelix.de", EXISTING_MASTER_NAME, "test123");
	}

	@Test(expected = IllegalArgumentException.class)
	public void createNewAccount_nullPassword_fail() {
		accService.createNewAccount("thomas.felix@tfelix.de", "Ignatz", null);
	}

	@Test(expected = IllegalArgumentException.class)
	public void createNewAccount_emptyPassword_fail() {
		accService.createNewAccount("thomas.felix@tfelix.de", "Ignatz", "");
	}
	
	@Test(expected = NullPointerException.class)
	public void changePassword_null_throws() {
		accService.changePassword(null);
	}
	
	@Test(expected = NullPointerException.class)
	public void changePassword_invalidOldPassword_false() {
		throw new IllegalStateException("Implementieren");
	}
}
