package net.bestia.core.game.service;

import java.util.Date;

import net.bestia.core.net.Messenger;
import net.bestia.model.Account;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class AccountServiceTest {
	
	private Account acc;
	private AccountService service;
	private Messenger messenger;
	
	@Before
	public void setup() {
		
		acc = mock(Account.class);
		
		when(acc.getId()).thenReturn(1);
		when(acc.getAdditionalBestiaSlots()).thenReturn(0);
		when(acc.getBannedUntilDate()).thenReturn(null);
		when(acc.getEmail()).thenReturn("john.doe@example.com");
		when(acc.getGold()).thenReturn(1337);
		when(acc.getRegisterDate()).thenReturn(new Date());
		
		messenger = mock(Messenger.class);
		
		service = new AccountService(acc, messenger);
		
	}

	@Test
	public void test_getAccountId() {
		assertEquals(1, service.getAccountId());
	}

	@Test
	public void test_addGold() {

		service.addGold(10, 0);
		
		verify(acc).setGold(1337 + 10 * 100);
		//verify(messenger).sendMessage(msg);
	}
	
	@Test
	public void test_dropGold() {
		boolean s = service.dropGold(4, 0);
		
		assertEquals(true, s);
		verify(acc).setGold(1337 + 4 * 100);
	}
	
	@Test
	public void test_dropTooMuchGold() {
		boolean s = service.dropGold(9999, 0);
		
		assertEquals(false, s);
		verify(acc, never()).setGold(1337 - 9999 * 100);
	}
}
