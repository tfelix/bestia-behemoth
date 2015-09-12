package net.bestia.zoneserver.ecs;

import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import net.bestia.model.domain.PlayerBestia;
import net.bestia.zoneserver.ecs.BestiaRegister.InputControllerCallback;
import net.bestia.zoneserver.manager.PlayerBestiaManager;

import org.junit.Assert;
import org.junit.Test;

public class InputControllerTest {

	@Test
	public void account_add_callback_test() {
		InputControllerCallback callback = getCallback();
		BestiaRegister pic = new BestiaRegister();
		pic.addCallback(callback);

		List<PlayerBestiaManager> managerList = new ArrayList<PlayerBestiaManager>();
		managerList.add(getMockedManager());

		verify(callback, times(1)).addedAccount(anyLong());
		verify(callback, times(1)).addedBestia(anyLong(), anyInt());
	}

	@Test
	public void account_remove_callback_test() {
		InputControllerCallback callback = getCallback();
		BestiaRegister pic = new BestiaRegister();
		pic.addCallback(callback);

		List<PlayerBestiaManager> managerList = new ArrayList<PlayerBestiaManager>();
		managerList.add(getMockedManager());

		pic.removeAccount(1);

		verify(callback, times(1)).removedAccount(anyLong());
		verify(callback, times(1)).removedBestia(anyLong(), anyInt());
	}



	public void test_multiadd_active_bestia_different_account_test() {

	}

	public void test_multiadd_active_bestia_same_account_test() {

	}

	@Test
	public void get_active_bestias_test() {
		InputControllerCallback callback = getCallback();
		BestiaRegister pic = new BestiaRegister();
		pic.addCallback(callback);
		
		PlayerBestiaManager pbm = getMockedManager();		
		pic.addPlayerBestia(1, pbm);
		
		Set<PlayerBestiaManager> actives = pic.getSpawnedBestias(1);
		
		Assert.assertNotNull(actives);
		Assert.assertTrue(actives.contains(pbm));
		Assert.assertEquals(1,  actives.size());
	}

	@Test
	public void remove_bestia_test() {
		InputControllerCallback callback = getCallback();
		BestiaRegister pic = new BestiaRegister();
		pic.addCallback(callback);
		
		PlayerBestiaManager pbm = getMockedManager();
		
		pic.addPlayerBestia(1, pbm);
		pic.removePlayerBestia(1, pbm);
		
		Assert.assertNull(pic.getSpawnedBestias(1));
		
		verify(callback, times(1)).addedAccount(anyLong());
		verify(callback, times(1)).addedBestia(anyLong(), anyInt());
		verify(callback, times(1)).removedBestia(anyLong(), anyInt());
		verify(callback, times(1)).removedAccount(anyLong());
	}


	private PlayerBestiaManager getMockedManager() {
		PlayerBestia pb = mock(PlayerBestia.class);
		PlayerBestiaManager pbm = mock(PlayerBestiaManager.class);
		
		when(pb.getId()).thenReturn(1);
		when(pbm.getPlayerBestia()).thenReturn(pb);
		
		return pbm;
	}

	private BestiaRegister.InputControllerCallback getCallback() {
		return mock(BestiaRegister.InputControllerCallback.class);
	}
}
