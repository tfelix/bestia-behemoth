package net.bestia.model.service;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import net.bestia.model.service.AccountService.Master;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations = { "/spring-config.xml" })
public class AccountServiceTest {

	@Autowired
	private AccountService accService;


	public void createNewAccount_noMail_fail() {
		
	}

	public void createNewAccount_duplicateMail_fail() {

	}

	//@Test
	public void createNewAccount_ok_success() {
		boolean flag = accService.createNewAccount("thomas.felix@tfelix.de", "Ignatz", "test123", Master.KNIGHT);
		Assert.assertTrue(flag);
	}

	public void createNewAccount_noMasterName_fail() {

	}

	public void createNewAccount_duplicateMasterName_fail() {

	}

	public void createNewAccount_nullPassword_fail() {

	}

	public void createNewAccount_emptyPassword_fail() {

	}

	public void getAllBesitas_wrongId_null() {

	}

}
