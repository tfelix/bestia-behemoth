package net.bestia.zoneserver.service;

import net.bestia.model.dao.AccountDAO;
import net.bestia.model.dao.ClientVarDAO;
import net.bestia.model.domain.Account;
import net.bestia.model.domain.ClientVar;
import net.bestia.zoneserver.connection.ClientVarService;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class ClientVarServiceTest {

	private static final long EXISTING_ACC_ID = 13;
	private static final long NON_OWNING_ACC_ID = 12;
	private static final long OWNING_ACC_ID = 14;
	private static final long NOT_EXISTING_ACC = 10;

	private static final String EXISTING_KEY = "hello";
	private static final String NOT_EXISTING_KEY = "blub";

	private static final String DATA_STR = "{\"bla\": 123, \"slot\": 10}";
	private static final String LONG_DATA_STR;
	
	static {
		StringBuilder builder = new StringBuilder();
		for(int i = 0; i < 10000; i++) {
			builder.append("A");
		}
		LONG_DATA_STR = builder.toString();
	}

	@Mock
	private AccountDAO accDao;

	@Mock
	private ClientVarDAO cvarDao;

	@Mock
	private Account account;

	@Mock
	private ClientVar existingCvar;

	private ClientVarService cvarService;

	@Before
	public void setup() {

		when(accDao.findOne(EXISTING_ACC_ID)).thenReturn(account);
		when(accDao.findOne(NOT_EXISTING_ACC)).thenReturn(null);
		when(cvarDao.findByKeyAndAccountId(EXISTING_KEY, OWNING_ACC_ID)).thenReturn(existingCvar);

		cvarService = new ClientVarService(cvarDao, accDao);
	}

	@Test(expected = NullPointerException.class)
	public void ctor_nullCvarDao_throws() {
		new ClientVarService(null, accDao);
	}

	@Test(expected = NullPointerException.class)
	public void ctor_nullAccountDao_throws() {
		new ClientVarService(cvarDao, null);
	}

	@Test
	public void isOwnerOfVar_nonOwnerAccId_false() {
		Assert.assertFalse(cvarService.isOwnerOfVar(NON_OWNING_ACC_ID, EXISTING_KEY));
	}

	@Test
	public void isOwnerOfVar_notExistingAccId_false() {
		Assert.assertFalse(cvarService.isOwnerOfVar(NOT_EXISTING_ACC, EXISTING_KEY));
	}

	@Test
	public void isOwnerOfVar_owningAccId_true() {
		Assert.assertTrue(cvarService.isOwnerOfVar(OWNING_ACC_ID, EXISTING_KEY));
	}

	@Test
	public void delete_existingAccId_deletes() {
		cvarService.delete(EXISTING_ACC_ID, EXISTING_KEY);
		verify(cvarDao).deleteByKeyAndAccountId(EXISTING_KEY, EXISTING_ACC_ID);
	}

	@Test
	public void find_existingAccId_notExistingKey_null() {
		ClientVar var = cvarService.find(EXISTING_ACC_ID, NOT_EXISTING_KEY);
		verify(cvarDao).findByKeyAndAccountId(NOT_EXISTING_KEY, EXISTING_ACC_ID);
		Assert.assertNull(var);
	}

	@Test
	public void find_existingAccIdAndKey_cvar() {

	}

	@Test(expected = NullPointerException.class)
	public void set_nullKey_throws() {
		cvarService.set(EXISTING_ACC_ID, null, DATA_STR);
	}

	@Test(expected = NullPointerException.class)
	public void set_nullData_throws() {
		cvarService.set(EXISTING_ACC_ID, EXISTING_KEY, null);
	}

	@Test(expected = IllegalArgumentException.class)
	public void set_longData_throws() {
		cvarService.set(EXISTING_ACC_ID, EXISTING_KEY, LONG_DATA_STR);
	}

	@Test(expected = IllegalArgumentException.class)
	public void set_nonExistingAccountExistingDataAndKey_throws() {
		cvarService.set(NOT_EXISTING_ACC, EXISTING_KEY, DATA_STR);
	}

	@Test
	public void set_existingAccountExistingDataAndKey_works() {

		cvarService.set(EXISTING_ACC_ID, EXISTING_KEY, DATA_STR);

		verify(cvarDao).findByKeyAndAccountId(EXISTING_KEY, EXISTING_ACC_ID);
		verify(cvarDao).save(any(ClientVar.class));
	}

	@Test
	public void set_existingAccountNotExistingDataAndKey_works() {

		cvarService.set(EXISTING_ACC_ID, NOT_EXISTING_KEY, DATA_STR);

		verify(cvarDao).findByKeyAndAccountId(NOT_EXISTING_KEY, EXISTING_ACC_ID);
		verify(cvarDao).save(any(ClientVar.class));
	}
}
