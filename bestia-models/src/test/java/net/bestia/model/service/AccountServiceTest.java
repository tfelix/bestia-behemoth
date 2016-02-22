package net.bestia.model.service;

import java.util.Set;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestExecutionListeners;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.test.context.support.DependencyInjectionTestExecutionListener;

import com.github.springtestdbunit.DbUnitTestExecutionListener;
import com.github.springtestdbunit.annotation.DatabaseSetup;

import net.bestia.model.domain.PlayerBestia;
import net.bestia.model.service.AccountService.Master;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations = {"/spring-config.xml"})
@TestExecutionListeners({ DependencyInjectionTestExecutionListener.class,
        DbUnitTestExecutionListener.class })
@DatabaseSetup("/db/accounts.xml")
public class AccountServiceTest {

	@Autowired
	private AccountService accService;

	@Test(expected=IllegalArgumentException.class)
	public void createNewAccount_noMail_fail() {
		accService.createNewAccount("", "Ignatz", "test123", Master.KNIGHT);
	}
	
	@Test(expected=IllegalArgumentException.class)
	public void createNewAccount_nullMail_fail() {
		accService.createNewAccount(null, "Ignatz", "test123", Master.KNIGHT);
	}

	@Test(expected=IllegalArgumentException.class)
	public void createNewAccount_duplicateMail_fail() {
		accService.createNewAccount("thomas.new123@tfelix.de", "Ignatz", "test123", Master.KNIGHT);
		accService.createNewAccount("thomas.new123@tfelix.de", "Ignatz2", "test123", Master.KNIGHT);
	}

	@Test
	public void createNewAccount_ok_success() {
		accService.createNewAccount("thomas.felix@tfelix.de", "Ignatz", "test123", Master.KNIGHT);
	}

	@Test(expected=IllegalArgumentException.class)
	public void createNewAccount_noMasterName_fail() {
		accService.createNewAccount("thomas.felix@tfelix.de", "", "test123", Master.KNIGHT);
	}
	
	@Test(expected=IllegalArgumentException.class)
	public void createNewAccount_nullMasterName_fail() {
		accService.createNewAccount("thomas.felix@tfelix.de", null, "test123", Master.KNIGHT);
	}

	@Test(expected=IllegalArgumentException.class)
	public void createNewAccount_duplicateMasterName_fail() {
		accService.createNewAccount("thomas.felix@tfelix.de", "Ignatz", "test123", Master.KNIGHT);
		accService.createNewAccount("thomas.felix2@tfelix.de", "Ignatz", "test123", Master.KNIGHT);
	}

	@Test(expected=IllegalArgumentException.class)
	public void createNewAccount_nullPassword_fail() {
		accService.createNewAccount("thomas.felix@tfelix.de", "Ignatz", null, Master.KNIGHT);
	}

	@Test(expected=IllegalArgumentException.class)
	public void createNewAccount_emptyPassword_fail() {
		accService.createNewAccount("thomas.felix@tfelix.de", "Ignatz", "", Master.KNIGHT);
	}

	@Test
	public void getAllBesitas_wrongId_null() {
		final Set<PlayerBestia> result = accService.getAllBestias(1337);
		Assert.assertNull(result);
	}

}
