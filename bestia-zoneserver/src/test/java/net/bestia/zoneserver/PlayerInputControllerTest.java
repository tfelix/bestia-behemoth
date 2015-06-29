package net.bestia.zoneserver;

import static org.mockito.Mockito.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.Set;

import org.junit.Assert;
import org.junit.Test;

import net.bestia.messages.InputMessage;
import net.bestia.zoneserver.ECSInputControler.InputControllerCallback;
import net.bestia.zoneserver.game.manager.PlayerBestiaManager;

public class PlayerInputControllerTest {

	@Test
	public void empty_account_add_test() {
		ECSInputControler pic = new ECSInputControler();
		pic.registerAccount(1, new ArrayList<PlayerBestiaManager>());
	}

	@Test
	public void account_add_callback_test() {
		InputControllerCallback callback = getCallback();
		ECSInputControler pic = new ECSInputControler();
		pic.addCallback(callback);

		List<PlayerBestiaManager> managerList = new ArrayList<PlayerBestiaManager>();
		managerList.add(mock(PlayerBestiaManager.class));

		pic.registerAccount(1, managerList);
		verify(callback, times(1)).addedAccount(anyLong());
		verify(callback, times(1)).addedBestia(anyLong(), anyInt());
	}

	@Test
	public void account_remove_callback_test() {
		InputControllerCallback callback = getCallback();
		ECSInputControler pic = new ECSInputControler();
		pic.addCallback(callback);

		List<PlayerBestiaManager> managerList = new ArrayList<PlayerBestiaManager>();
		managerList.add(mock(PlayerBestiaManager.class));

		pic.registerAccount(1, managerList);
		pic.removeAccount(1);

		verify(callback, times(1)).removedAccount(anyLong());
		verify(callback, times(1)).removedBestia(anyLong(), anyInt());
	}

	@Test
	public void account_queue_registered_message_test() {
		InputControllerCallback callback = getCallback();
		ECSInputControler pic = new ECSInputControler();
		pic.addCallback(callback);

		List<PlayerBestiaManager> managerList = new ArrayList<PlayerBestiaManager>();
		managerList.add(mock(PlayerBestiaManager.class));

		pic.registerAccount(1, managerList);

		InputMessage msg = mock(InputMessage.class);
		when(msg.getPlayerBestiaId()).thenReturn(1);
		when(msg.getAccountId()).thenReturn(1L);

		pic.sendInput(msg);

		Queue<InputMessage> queue = pic.getInput(1);
		
		Assert.assertEquals(1, queue.size());
		verify(callback, times(0)).removedAccount(anyLong());
		verify(callback, times(0)).removedBestia(anyLong(), anyInt());
		
	}

	public void test_multiadd_active_bestia_different_account_test() {

	}

	public void test_multiadd_active_bestia_same_account_test() {

	}

	@Test
	public void account_queue_unregistered_message_test() {
		InputControllerCallback callback = getCallback();
		ECSInputControler pic = new ECSInputControler();
		pic.addCallback(callback);
		
		InputMessage msg = mock(InputMessage.class);
		when(msg.getPlayerBestiaId()).thenReturn(1);
		when(msg.getAccountId()).thenReturn(1L);
		pic.sendInput(msg);	

		Queue<InputMessage> queue = pic.getInput(1);
		Assert.assertNull(queue);
	}

	@Test
	public void add_bestia_without_account_test() {
		InputControllerCallback callback = getCallback();
		ECSInputControler pic = new ECSInputControler();
		pic.addCallback(callback);
		
		final int pbid = 1337;
		PlayerBestiaManager pbm = mock(PlayerBestiaManager.class);
		when(pbm.getBestia().getId()).thenReturn(pbid);
		
		pic.addPlayerBestia(1, pbm);
		
		Queue<InputMessage> queue = pic.getInput(pbid);
		Assert.assertNotNull(queue);
		Assert.assertEquals(0, queue.size());
		verify(callback, times(1)).addedAccount(anyLong());
		verify(callback, times(1)).addedBestia(anyLong(), anyInt());
	}

	@Test
	public void get_active_bestias_test() {
		InputControllerCallback callback = getCallback();
		ECSInputControler pic = new ECSInputControler();
		pic.addCallback(callback);
		
		final int pbid = 1337;
		PlayerBestiaManager pbm = mock(PlayerBestiaManager.class);
		when(pbm.getBestia().getId()).thenReturn(pbid);
		
		pic.addPlayerBestia(1, pbm);
		
		Set<PlayerBestiaManager> actives = pic.getActiveBestias(1);
		
		Assert.assertNotNull(actives);
		Assert.assertTrue(actives.contains(pbm));
		Assert.assertEquals(1,  actives.size());
	}

	@Test
	public void remove_bestia_test() {
		InputControllerCallback callback = getCallback();
		ECSInputControler pic = new ECSInputControler();
		pic.addCallback(callback);
		
		final int pbid = 1337;
		PlayerBestiaManager pbm = mock(PlayerBestiaManager.class);
		when(pbm.getBestia().getId()).thenReturn(pbid);
		
		pic.addPlayerBestia(1, pbm);
		pic.removePlayerBestia(1, pbm);
		
		Assert.assertNull(pic.getInput(pbid));
		Assert.assertNull(pic.getActiveBestias(1));
		
		verify(callback, times(1)).addedAccount(anyLong());
		verify(callback, times(1)).addedBestia(anyLong(), anyInt());
		verify(callback, times(1)).removedBestia(anyLong(), anyInt());
		verify(callback, times(1)).removedAccount(anyLong());
	}

	@Test
	public void message_removed_after_acc_removed_test() {
		InputControllerCallback callback = getCallback();
		ECSInputControler pic = new ECSInputControler();
		pic.addCallback(callback);

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

	// @TODO Das hier fertig machen.
	private PlayerBestiaManager getMockedManager() {
		
	}

	private ECSInputControler.InputControllerCallback getCallback() {
		return mock(ECSInputControler.InputControllerCallback.class);
	}
}
