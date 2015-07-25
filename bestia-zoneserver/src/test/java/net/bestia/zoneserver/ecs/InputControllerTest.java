package net.bestia.zoneserver.ecs;

import static org.mockito.Mockito.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.Set;

import org.junit.Assert;
import org.junit.Test;

import net.bestia.messages.InputMessage;
import net.bestia.messages.Message;
import net.bestia.model.domain.PlayerBestia;
import net.bestia.zoneserver.ecs.InputController;
import net.bestia.zoneserver.ecs.InputController.InputControllerCallback;
import net.bestia.zoneserver.game.manager.PlayerBestiaManager;

public class InputControllerTest {

	@Test
	public void account_add_callback_test() {
		InputControllerCallback callback = getCallback();
		InputController pic = new InputController();
		pic.addCallback(callback);

		List<PlayerBestiaManager> managerList = new ArrayList<PlayerBestiaManager>();
		managerList.add(getMockedManager());

		verify(callback, times(1)).addedAccount(anyLong());
		verify(callback, times(1)).addedBestia(anyLong(), anyInt());
	}

	@Test
	public void account_remove_callback_test() {
		InputControllerCallback callback = getCallback();
		InputController pic = new InputController();
		pic.addCallback(callback);

		List<PlayerBestiaManager> managerList = new ArrayList<PlayerBestiaManager>();
		managerList.add(getMockedManager());

		pic.removeAccount(1);

		verify(callback, times(1)).removedAccount(anyLong());
		verify(callback, times(1)).removedBestia(anyLong(), anyInt());
	}

	@Test
	public void account_queue_registered_message_test() {
		InputControllerCallback callback = getCallback();
		InputController pic = new InputController();
		pic.addCallback(callback);

		List<PlayerBestiaManager> managerList = new ArrayList<PlayerBestiaManager>();
		managerList.add(getMockedManager());

		InputMessage msg = mock(InputMessage.class);
		when(msg.getPlayerBestiaId()).thenReturn(1);
		when(msg.getAccountId()).thenReturn(1L);

		pic.sendInput(msg);

		Queue<Message> queue = pic.getInput(1);
		
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
		InputController pic = new InputController();
		pic.addCallback(callback);
		
		InputMessage msg = mock(InputMessage.class);
		when(msg.getPlayerBestiaId()).thenReturn(1);
		when(msg.getAccountId()).thenReturn(1L);
		pic.sendInput(msg);	

		Queue<Message> queue = pic.getInput(1);
		Assert.assertNull(queue);
	}

	@Test
	public void add_bestia_without_account_test() {
		InputControllerCallback callback = getCallback();
		InputController pic = new InputController();
		pic.addCallback(callback);
		
		final int pbid = 1337;
		PlayerBestiaManager pbm = getMockedManager();
		when(pbm.getBestia().getId()).thenReturn(pbid);
		
		pic.addPlayerBestia(1, pbm);
		
		Queue<Message> queue = pic.getInput(pbid);
		Assert.assertNotNull(queue);
		Assert.assertEquals(0, queue.size());
		verify(callback, times(1)).addedAccount(anyLong());
		verify(callback, times(1)).addedBestia(anyLong(), anyInt());
	}

	@Test
	public void get_active_bestias_test() {
		InputControllerCallback callback = getCallback();
		InputController pic = new InputController();
		pic.addCallback(callback);
		
		PlayerBestiaManager pbm = getMockedManager();		
		pic.addPlayerBestia(1, pbm);
		
		Set<PlayerBestiaManager> actives = pic.getActiveBestias(1);
		
		Assert.assertNotNull(actives);
		Assert.assertTrue(actives.contains(pbm));
		Assert.assertEquals(1,  actives.size());
	}

	@Test
	public void remove_bestia_test() {
		InputControllerCallback callback = getCallback();
		InputController pic = new InputController();
		pic.addCallback(callback);
		
		PlayerBestiaManager pbm = getMockedManager();
		
		pic.addPlayerBestia(1, pbm);
		pic.removePlayerBestia(1, pbm);
		
		Assert.assertNull(pic.getInput(1));
		Assert.assertNull(pic.getActiveBestias(1));
		
		verify(callback, times(1)).addedAccount(anyLong());
		verify(callback, times(1)).addedBestia(anyLong(), anyInt());
		verify(callback, times(1)).removedBestia(anyLong(), anyInt());
		verify(callback, times(1)).removedAccount(anyLong());
	}

	@Test
	public void message_removed_after_acc_removed_test() {
		InputControllerCallback callback = getCallback();
		InputController pic = new InputController();
		pic.addCallback(callback);

		List<PlayerBestiaManager> managerList = new ArrayList<PlayerBestiaManager>();
		managerList.add(getMockedManager());

		InputMessage msg = mock(InputMessage.class);
		when(msg.getPlayerBestiaId()).thenReturn(1);
		when(msg.getAccountId()).thenReturn(1L);

		pic.sendInput(msg);

		// Remove account now.
		pic.removeAccount(1);

		Queue<Message> queue = pic.getInput(1);
		Assert.assertNull(queue);
	}

	private PlayerBestiaManager getMockedManager() {
		PlayerBestia pb = mock(PlayerBestia.class);
		PlayerBestiaManager pbm = mock(PlayerBestiaManager.class);
		
		when(pb.getId()).thenReturn(1);
		when(pbm.getBestia()).thenReturn(pb);
		
		return pbm;
	}

	private InputController.InputControllerCallback getCallback() {
		return mock(InputController.InputControllerCallback.class);
	}
}
