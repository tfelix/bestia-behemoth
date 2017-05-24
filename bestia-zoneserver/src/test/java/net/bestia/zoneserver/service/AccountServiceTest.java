package net.bestia.zoneserver.service;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.Before;
import org.junit.Test;

import net.bestia.model.dao.AccountDAO;
import net.bestia.model.dao.BestiaDAO;
import net.bestia.model.dao.PlayerBestiaDAO;
import net.bestia.model.domain.Bestia;

public class AccountServiceTest {

	private AccountService accService;

	private AccountDAO accountDao;
	private PlayerBestiaDAO playerBestiaDao;
	private BestiaDAO bestiaDao;
	private ConnectionService connectionService;

	private Bestia bestia;

	@Before
	public void setup() {

		bestia = mock(Bestia.class);
		accountDao = mock(AccountDAO.class);
		playerBestiaDao = mock(PlayerBestiaDAO.class);
		bestiaDao = mock(BestiaDAO.class);
		connectionService = mock(ConnectionService.class);
		
		when(bestiaDao.findOne(1)).thenReturn(bestia);

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
		accService.createNewAccount("thomas.new123@tfelix.de", "Ignatz", "test123");
		accService.createNewAccount("thomas.new123@tfelix.de", "Ignatz2", "test123");
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
		accService.createNewAccount("thomas.felix@tfelix.de", "Ignatz", "test123");
		accService.createNewAccount("thomas.felix2@tfelix.de", "Ignatz", "test123");
	}

	@Test(expected = IllegalArgumentException.class)
	public void createNewAccount_nullPassword_fail() {
		accService.createNewAccount("thomas.felix@tfelix.de", "Ignatz", null);
	}

	@Test(expected = IllegalArgumentException.class)
	public void createNewAccount_emptyPassword_fail() {
		accService.createNewAccount("thomas.felix@tfelix.de", "Ignatz", "");
	}
}
