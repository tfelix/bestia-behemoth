package net.bestia.zoneserver.manager;

import net.bestia.model.service.InventoryService;
import net.bestia.zoneserver.Zoneserver;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestExecutionListeners;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.test.context.support.DependencyInjectionTestExecutionListener;

import com.github.springtestdbunit.DbUnitTestExecutionListener;
import com.github.springtestdbunit.annotation.DatabaseSetup;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations = { "/spring-config.xml" })
@TestExecutionListeners({ DependencyInjectionTestExecutionListener.class, DbUnitTestExecutionListener.class })
@DatabaseSetup("/db/items.xml")
public class InventoryManagerTest {

	@Autowired
	private InventoryService inventoryService;

	@Test(expected = IllegalArgumentException.class)
	public void ctor_nullOwner_throws() {
		new InventoryManager(null, inventoryService, getServer());
	}

	@Test(expected = IllegalArgumentException.class)
	public void ctor_nullService_throws() {
		new InventoryManager(getPlayerBestiaManager(), null, getServer());
	}

	@Test(expected = IllegalArgumentException.class)
	public void ctor_nullServer_throws() {
		new InventoryManager(getPlayerBestiaManager(), inventoryService, null);
	}

	public void addItem_knownItemId_true() {

	}

	public void addItem_tooMuchWeight_false() {

	}

	public void addItem_unknownItemId_false() {

	}

	public void getCurrentWeight_correctWeight() {

	}

	public void addItem_knownItemDbName_true() {

	}

	public void addItem_unknownItemDbName_false() {

	}

	public void hasPlayerItem_ownedId_true() {

	}

	public void hasPlayerItem_notOwnedId_false() {

	}

	public void hasPlayerItem_notOwnedAmount_false() {

	}

	public void hasPlayerItem_ownedItemName_true() {

	}

	public void hasPlayerItem_notOwnedItemName_false() {

	}

	public void removeItem_ownedId_true() {

	}

	public void removeItem_ownedItemDbName_true() {

	}

	public void getInventoryListMessage_correctList() {

	}
	
	public void getMaxWeight_correct() {
		
	}
	
	public void getPlayerItem_id_ok() {
		
	}
	
	public void getPlayerItem_playerItemId_ok() {
		
	}

	private Zoneserver getServer() {
		final Zoneserver server = Mockito.mock(Zoneserver.class);

		return server;
	}

	private PlayerBestiaManager getPlayerBestiaManager() {
		final PlayerBestiaManager manager = Mockito.mock(PlayerBestiaManager.class);

		return manager;
	}
}
