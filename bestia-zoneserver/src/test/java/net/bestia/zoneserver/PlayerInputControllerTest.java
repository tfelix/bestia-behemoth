package net.bestia.zoneserver;
import static org.mockito.Mockito.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Queue;

import org.junit.Assert;
import org.junit.Test;

import net.bestia.messages.InputMessage;
import net.bestia.zoneserver.PlayerInputController.PlayerRegisterCallback;
import net.bestia.zoneserver.game.manager.PlayerBestiaManager;

public class PlayerInputControllerTest {

	@Test
	public void empty_account_add_test() {
		PlayerRegisterCallback callback = getCallback();
		PlayerInputController pic = new PlayerInputController(callback);
		
		pic.registerAccount(1, new ArrayList<PlayerBestiaManager>());
	}
	
	@Test
	public void account_add_callback_test() {
		PlayerRegisterCallback callback = getCallback();
		PlayerInputController pic = new PlayerInputController(callback);
		
		List<PlayerBestiaManager> managerList = new ArrayList<PlayerBestiaManager>();
		managerList.add(mock(PlayerBestiaManager.class));
		
		pic.registerAccount(1, managerList);
		verify(callback, times(1)).addedAccount(anyLong());
		verify(callback, times(1)).addedBestia(anyLong(), anyInt());
	}
	
	@Test
	public void account_remove_callback_test() {
		PlayerRegisterCallback callback = getCallback();
		PlayerInputController pic = new PlayerInputController(callback);
		
		List<PlayerBestiaManager> managerList = new ArrayList<PlayerBestiaManager>();
		managerList.add(mock(PlayerBestiaManager.class));
		
		pic.registerAccount(1, managerList);
		pic.removeAccount(1);
		
		verify(callback, times(1)).removedAccount(anyLong());
		verify(callback, times(1)).removedBestia(anyLong(), anyInt());
	}
	
	@Test
	public void account_queue_registered_message_test() {
		PlayerRegisterCallback callback = getCallback();
		PlayerInputController pic = new PlayerInputController(callback);
		
		List<PlayerBestiaManager> managerList = new ArrayList<PlayerBestiaManager>();
		managerList.add(mock(PlayerBestiaManager.class));
		
		pic.registerAccount(1, managerList);
		
		InputMessage msg = mock(InputMessage.class);
		when(msg.getPlayerBestiaId()).thenReturn(1);
		when(msg.getAccountId()).thenReturn(1L);
		
		pic.sendInput(msg);
		
		Queue<InputMessage> queue = pic.getInput(1);
		Assert.assertEquals(1, queue.size());
	}
	
	@Test
	public void account_queue_unregistered_message_test() {
		PlayerRegisterCallback callback = getCallback();
		PlayerInputController pic = new PlayerInputController(callback);
		
		List<PlayerBestiaManager> managerList = new ArrayList<PlayerBestiaManager>();
		managerList.add(mock(PlayerBestiaManager.class));
		
		pic.registerAccount(1, managerList);
		pic.removeAccount(1);
		
		InputMessage msg = mock(InputMessage.class);
		when(msg.getPlayerBestiaId()).thenReturn(1);
		when(msg.getAccountId()).thenReturn(1L);
		
		pic.sendInput(msg);
		
		Queue<InputMessage> queue = pic.getInput(1);
		Assert.assertNull(queue);
	}
	
	public void add_bestia_test() {
		
	}
	
	public void remove_bestia_test() {
		
	}
	
	@Test
	public void message_removed_after_acc_removed_test() {
		PlayerRegisterCallback callback = getCallback();
		PlayerInputController pic = new PlayerInputController(callback);
		
		List<PlayerBestiaManager> managerList = new ArrayList<PlayerBestiaManager>();
		managerList.add(mock(PlayerBestiaManager.class));
		
		pic.registerAccount(1, managerList);
		
		InputMessage msg = mock(InputMessage.class);
		when(msg.getPlayerBestiaId()).thenReturn(1);
		when(msg.getAccountId()).thenReturn(1L);
		
		pic.sendInput(msg);
		
		// Remove account now.
		pic.removeAccount(1);
		
		Queue<InputMessage> queue = pic.getInput(1);
		Assert.assertNull(queue);
	}
	
	@Test(expected=IllegalArgumentException.class)
	public void exception_empty_callback() {
		@SuppressWarnings("unused")
		PlayerInputController pic = new PlayerInputController(null);
	}
	
	private PlayerInputController.PlayerRegisterCallback getCallback() {
		return mock(PlayerInputController.PlayerRegisterCallback.class);
	}
}
