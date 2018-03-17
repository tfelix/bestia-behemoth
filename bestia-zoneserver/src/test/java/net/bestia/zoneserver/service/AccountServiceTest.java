package net.bestia.zoneserver.service;

import static org.mockito.Mockito.*;
import static org.mockito.Mockito.when;

import net.bestia.zoneserver.connection.AccountService;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import net.bestia.messages.account.AccountRegistration;
import net.bestia.messages.account.AccountRegistrationError;
import bestia.model.dao.AccountDAO;
import bestia.model.dao.BestiaDAO;
import bestia.model.dao.PlayerBestiaDAO;
import bestia.model.domain.Account;
import bestia.model.domain.Bestia;
import bestia.model.domain.Gender;
import bestia.model.domain.Hairstyle;
import bestia.model.domain.Password;
import bestia.model.domain.PlayerBestia;

@RunWith(MockitoJUnitRunner.class)
public class AccountServiceTest {

	private static final String EXISTING_MAIL = "thomas.exists@tfelix.de";
	private static final String NOT_EXISTING_MAIL = "thomas.exist23434@tfelix.de";
	private static final String EXISTING_MASTER_NAME = "IgnatzDerDicke";

	private static final String NOT_EXISTING_PASSWORD = "notexisting";
	private static final String EXISTING_PASSWORD = "existing";

	private static final String OLD_PSWD = "oldpassword";
	private static final String NEW_PSWD = "newpassword";
	private static final String VALID_USERNAME = "dixfax";

	private AccountService accService;

	@Mock
	private AccountDAO accountDao;

	@Mock
	private PlayerBestiaDAO playerBestiaDao;

	@Mock
	private BestiaDAO bestiaDao;

	@Mock
	private Password password;

	@Mock
	private Account account;

	@Mock
	private Bestia bestia;

	@Mock
	private PlayerBestia masterBestia;

	private final AccountRegistration regisValid;
	private final AccountRegistration regisMailMissing;
	private final AccountRegistration regisDuplicateMail;
	private final AccountRegistration regisEmptyUserName;
	private final AccountRegistration regisDoublicateUserName;
	private final AccountRegistration regisEmptyPassword;

	public AccountServiceTest() {

		regisValid = getValidRegistration();
		regisMailMissing = getValidRegistration();
		regisMailMissing.setEmail("");

		regisDuplicateMail = getValidRegistration();
		regisDuplicateMail.setEmail(EXISTING_MAIL);

		regisEmptyUserName = getValidRegistration();
		regisEmptyUserName.setUsername("");

		regisDoublicateUserName = getValidRegistration();
		regisDoublicateUserName.setUsername(EXISTING_MASTER_NAME);

		regisEmptyPassword = getValidRegistration();
		regisEmptyPassword.setPassword("");
	}

	private AccountRegistration getValidRegistration() {

		AccountRegistration regis = new AccountRegistration();
		regis.setCampaignCode("Test123");
		regis.setEmail(NOT_EXISTING_MAIL);
		regis.setGender(Gender.FEMALE);
		regis.setHairstyle(Hairstyle.female_01);
		regis.setPassword(EXISTING_PASSWORD);
		regis.setUsername(VALID_USERNAME);
		return regis;
	}

	@Before
	public void setup() {

		when(bestiaDao.findOne(1)).thenReturn(bestia);
		
		when(accountDao.findByEmail(EXISTING_MAIL)).thenReturn(account);
		when(accountDao.findByUsernameOrEmail(EXISTING_MAIL)).thenReturn(account);
		
		when(playerBestiaDao.findMasterBestiaWithName(EXISTING_MASTER_NAME)).thenReturn(masterBestia);

		when(account.getPassword()).thenReturn(password);
		when(account.isActivated()).thenReturn(true);

		when(password.matches(EXISTING_PASSWORD)).thenReturn(true);
		when(password.matches(OLD_PSWD)).thenReturn(true);

		accService = new AccountService(accountDao, playerBestiaDao, bestiaDao);
	}

	@Test(expected = IllegalArgumentException.class)
	public void createNewAccount_noMail_fail() {
		AccountRegistrationError error = accService.createNewAccount(regisMailMissing);
		Assert.assertEquals(AccountRegistrationError.EMAIL_INVALID, error);
	}

	@Test
	public void createNewAccount_duplicateMail_fail() {
		AccountRegistrationError error = accService.createNewAccount(regisDuplicateMail);
		Assert.assertEquals(AccountRegistrationError.EMAIL_INVALID, error);
	}

	@Test
	public void createNewAccount_ok_success() {
		AccountRegistrationError error = accService.createNewAccount(regisValid);
		Assert.assertEquals(AccountRegistrationError.NONE, error);
	}

	@Test(expected = IllegalArgumentException.class)
	public void createNewAccount_noMasterName_fail() {
		AccountRegistrationError error = accService.createNewAccount(regisEmptyUserName);
		Assert.assertEquals(AccountRegistrationError.USERNAME_INVALID, error);
	}

	@Test(expected=IllegalArgumentException.class)
	public void createNewAccount_nullMasterName_fail() {
		AccountRegistrationError error = accService.createNewAccount(regisEmptyUserName);
		Assert.assertEquals(AccountRegistrationError.USERNAME_INVALID, error);
	}

	@Test
	public void createNewAccount_duplicateMasterName_fail() {
		AccountRegistrationError error = accService.createNewAccount(regisDoublicateUserName);
		Assert.assertEquals(AccountRegistrationError.USERNAME_INVALID, error);
	}

	@Test(expected = IllegalArgumentException.class)
	public void createNewAccount_emptyPassword_fail() {
		AccountRegistrationError error = accService.createNewAccount(regisEmptyPassword);
		Assert.assertEquals(AccountRegistrationError.GENERAL_ERROR, error);
	}

	@Test(expected = NullPointerException.class)
	public void changePasswordWithoutCheck_1argNull_throws() {
		accService.changePasswordWithoutCheck(null, "test");
	}

	@Test(expected = NullPointerException.class)
	public void changePasswordWithoutCheck_2argNull_throws() {
		accService.changePasswordWithoutCheck("test", null);
	}

	@Test
	public void changePassword_validOldPassword_true() {
		boolean res = accService.changePassword(EXISTING_MAIL, OLD_PSWD, NEW_PSWD);

		verify(account).setPassword(any());
		Assert.assertTrue(res);
	}

	@Test
	public void changePassword_vinvalidOldPassword_false() {
		boolean res = accService.changePassword(EXISTING_MAIL, NOT_EXISTING_PASSWORD, NEW_PSWD);

		verify(account, times(0)).setPassword(any());
		Assert.assertFalse(res);
	}

	@Test
	public void changePasswordWithoutCheck_emptyNewPassword_false() {
		boolean res = accService.changePassword(EXISTING_MAIL, NOT_EXISTING_PASSWORD, "");

		verify(account, times(0)).setPassword(any());
		Assert.assertFalse(res);
	}

	@Test
	public void createLoginToken_invalidAccName_null() {
		Account acc = accService.createLoginToken(NOT_EXISTING_MAIL, EXISTING_PASSWORD);

		Assert.assertNull(acc);
		verify(account, times(0)).setLoginToken(any());
	}

	@Test
	public void createLoginToken_invalidPassword_null() {
		Account acc = accService.createLoginToken(NOT_EXISTING_MAIL, NOT_EXISTING_PASSWORD);

		Assert.assertNull(acc);
		verify(account, times(0)).setLoginToken(any());
	}

	@Test
	public void createLoginToken_bannedAccountValidPassword_null() {

	}

	@Test
	public void createLoginToken_validPassword_newToken() {

		Account acc = accService.createLoginToken(EXISTING_MAIL, EXISTING_PASSWORD);

		Assert.assertNotNull(acc);
		verify(account, times(1)).setLoginToken(anyString());
	}

	@Test
	public void createLoginToken_notActivatedAccount_null() {

		when(account.isActivated()).thenReturn(false);

		Account acc = accService.createLoginToken(EXISTING_MAIL, EXISTING_PASSWORD);

		Assert.assertNull(acc);
		verify(account, times(0)).setLoginToken(any());

	}

	@Test
	public void createLoginToken_emptyLoginTokensAreNotAllowed() {

	}
}
