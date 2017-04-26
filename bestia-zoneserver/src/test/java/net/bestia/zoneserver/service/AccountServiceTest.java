package net.bestia.zoneserver.service;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;

import net.bestia.model.dao.AccountDAO;
import net.bestia.model.dao.BestiaDAO;
import net.bestia.model.dao.PlayerBestiaDAO;
import net.bestia.model.domain.PlayerClass;

@RunWith(SpringRunner.class)
@SpringBootTest
@ActiveProfiles("test")
public class AccountServiceTest {

	@Autowired
	private AccountService accService;
	
	@MockBean
	private AccountDAO accountDao;
	
	@MockBean
	private PlayerBestiaDAO playerBestiaDao;
	
	@MockBean
	private BestiaDAO bestiaDao;

	@MockBean
	private ConnectionService connectionService;

	@Test(expected = IllegalArgumentException.class)
	public void createNewAccount_noMail_fail() {
		accService.createNewAccount("", "Ignatz", "test123", PlayerClass.KNIGHT);
	}

	@Test(expected = IllegalArgumentException.class)
	public void createNewAccount_nullMail_fail() {
		accService.createNewAccount(null, "Ignatz", "test123", PlayerClass.KNIGHT);
	}

	@Test(expected = IllegalArgumentException.class)
	public void createNewAccount_duplicateMail_fail() {
		accService.createNewAccount("thomas.new123@tfelix.de", "Ignatz", "test123", PlayerClass.KNIGHT);
		accService.createNewAccount("thomas.new123@tfelix.de", "Ignatz2", "test123", PlayerClass.KNIGHT);
	}

	@Test
	public void createNewAccount_ok_success() {
		accService.createNewAccount("thomas.felix@tfelix.de", "Ignatz", "test123", PlayerClass.KNIGHT);
	}

	@Test(expected = IllegalArgumentException.class)
	public void createNewAccount_noMasterName_fail() {
		accService.createNewAccount("thomas.felix@tfelix.de", "", "test123", PlayerClass.KNIGHT);
	}

	@Test(expected = IllegalArgumentException.class)
	public void createNewAccount_nullMasterName_fail() {
		accService.createNewAccount("thomas.felix@tfelix.de", null, "test123", PlayerClass.KNIGHT);
	}

	@Test(expected = IllegalArgumentException.class)
	public void createNewAccount_duplicateMasterName_fail() {
		accService.createNewAccount("thomas.felix@tfelix.de", "Ignatz", "test123", PlayerClass.KNIGHT);
		accService.createNewAccount("thomas.felix2@tfelix.de", "Ignatz", "test123", PlayerClass.KNIGHT);
	}

	@Test(expected = IllegalArgumentException.class)
	public void createNewAccount_nullPassword_fail() {
		accService.createNewAccount("thomas.felix@tfelix.de", "Ignatz", null, PlayerClass.KNIGHT);
	}

	@Test(expected = IllegalArgumentException.class)
	public void createNewAccount_emptyPassword_fail() {
		accService.createNewAccount("thomas.felix@tfelix.de", "Ignatz", "", PlayerClass.KNIGHT);
	}
}
