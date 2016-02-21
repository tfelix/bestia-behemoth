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

	@Test
	public void createNewAccount_noMail_fail() {
		boolean flag = accService.createNewAccount("", "Ignatz", "test123", Master.KNIGHT);
		Assert.assertFalse(flag);
	}
	
	@Test
	public void createNewAccount_nullMail_fail() {
		boolean flag = accService.createNewAccount(null, "Ignatz", "test123", Master.KNIGHT);
		Assert.assertFalse(flag);
	}

	@Test
	public void createNewAccount_duplicateMail_fail() {
		boolean flag = accService.createNewAccount("thomas.felix@tfelix.de", "Ignatz", "test123", Master.KNIGHT);
		Assert.assertTrue(flag);
		flag = accService.createNewAccount("thomas.felix@tfelix.de", "Ignatz2", "test123", Master.KNIGHT);
		Assert.assertFalse(flag);
	}

	// TODO Bestia DB anlegen.
	@Test
	public void createNewAccount_ok_success() {
		boolean flag = accService.createNewAccount("thomas.felix@tfelix.de", "Ignatz", "test123", Master.KNIGHT);
		Assert.assertTrue(flag);
	}

	@Test
	public void createNewAccount_noMasterName_fail() {
		boolean flag = accService.createNewAccount("thomas.felix@tfelix.de", "", "test123", Master.KNIGHT);
		Assert.assertFalse(flag);
	}
	
	@Test
	public void createNewAccount_nullMasterName_fail() {
		boolean flag = accService.createNewAccount("thomas.felix@tfelix.de", null, "test123", Master.KNIGHT);
		Assert.assertFalse(flag);
	}

	@Test
	public void createNewAccount_duplicateMasterName_fail() {
		boolean flag = accService.createNewAccount("thomas.felix@tfelix.de", "Ignatz", "test123", Master.KNIGHT);
		Assert.assertTrue(flag);
		flag = accService.createNewAccount("thomas.felix2@tfelix.de", "Ignatz", "test123", Master.KNIGHT);
		Assert.assertFalse(flag);
	}

	@Test
	public void createNewAccount_nullPassword_fail() {
		boolean flag = accService.createNewAccount("thomas.felix@tfelix.de", "Ignatz", null, Master.KNIGHT);
		Assert.assertFalse(flag);
	}

	@Test
	public void createNewAccount_emptyPassword_fail() {
		boolean flag = accService.createNewAccount("thomas.felix@tfelix.de", "Ignatz", "", Master.KNIGHT);
		Assert.assertFalse(flag);
	}

	@Test
	public void getAllBesitas_wrongId_null() {
		final Set<PlayerBestia> result = accService.getAllBestias(1337);
		Assert.assertNull(result);
	}

}
