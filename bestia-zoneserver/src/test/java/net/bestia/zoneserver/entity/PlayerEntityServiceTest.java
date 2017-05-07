package net.bestia.zoneserver.entity;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.*;
import static org.mockito.Matchers.*;

import java.awt.Component;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import com.hazelcast.core.HazelcastInstance;

import net.bestia.messages.bestia.BestiaActivateMessage;
import net.bestia.model.domain.PlayerBestia;
import net.bestia.model.geometry.Rect;
import net.bestia.zoneserver.BasicMocks;
import net.bestia.zoneserver.actor.ZoneAkkaApi;
import net.bestia.zoneserver.entity.components.PlayerComponent;
import net.bestia.zoneserver.service.PlayerBestiaService;

public class PlayerEntityServiceTest {

	private final static long KNOWN_ENTITY_ID = 1337;
	private final static long KNOWN_ACC_ID = 666;
	private static final long UNKNOWN_ENTITY_ID = 12;
	private static final long MASTER_PB_ID = 42;
	private static final long PLAYER_PB_ID = 43;

	private BasicMocks mocks = new BasicMocks();
	private HazelcastInstance hz = mocks.hazelcast();

	private EntityService entityService;
	private PlayerBestiaService playerBestiaService;
	private ZoneAkkaApi akkaApi;

	private Entity playerEntity;
	private Entity masterEntity;

	private PlayerEntityService pbeService;
	private PlayerBestia masterPlayerBestia;

	@Before
	public void setup() {

		entityService = mock(EntityService.class);
		playerBestiaService = mock(PlayerBestiaService.class);
		akkaApi = mock(ZoneAkkaApi.class);

		playerEntity = mock(Entity.class);
		masterEntity = mock(Entity.class);

		// Setup the player.
		PlayerComponent playerComponent = mock(PlayerComponent.class);
		when(playerComponent.getOwnerAccountId()).thenReturn(KNOWN_ACC_ID);
		when(playerComponent.getPlayerBestiaId()).thenReturn(PLAYER_PB_ID);

		// Setup the master.
		PlayerComponent masterComponent = mock(PlayerComponent.class);
		when(masterComponent.getOwnerAccountId()).thenReturn(KNOWN_ACC_ID);
		when(masterComponent.getPlayerBestiaId()).thenReturn(MASTER_PB_ID);

		masterPlayerBestia = mock(PlayerBestia.class);
		when(masterPlayerBestia.getId()).thenReturn(MASTER_PB_ID);
		when(entityService.getEntity(KNOWN_ENTITY_ID)).thenReturn(playerEntity);
		when(entityService.hasComponent(any(Entity.class), any())).thenReturn(false);
		when(entityService.getComponent(any(Entity.class), any())).thenReturn(Optional.empty());
		when(entityService.getComponent(playerEntity, PlayerComponent.class)).thenReturn(Optional.of(playerComponent));

		when(playerBestiaService.getMaster(KNOWN_ACC_ID)).thenReturn(masterPlayerBestia);

		pbeService = new PlayerEntityService(hz, entityService, playerBestiaService, akkaApi);

	}

	@Test(expected = IllegalArgumentException.class)
	public void setActiveEntity_unknownEntityId_throws() {
		pbeService.setActiveEntity(KNOWN_ACC_ID, UNKNOWN_ENTITY_ID);
	}

	@Test
	public void setActiveEntity_idSetAndMessageSendToClient() {

		pbeService.setActiveEntity(10, KNOWN_ENTITY_ID);

		ArgumentCaptor<BestiaActivateMessage> captor = ArgumentCaptor.forClass(BestiaActivateMessage.class);
		verify(akkaApi, times(1)).sendToClient(captor.capture());
		assertEquals(playerEntity, pbeService.getActivePlayerEntity(10));
		assertEquals(10, captor.getValue().getAccountId());
		assertEquals(KNOWN_ENTITY_ID, captor.getValue().getPlayerBestiaId());
	}

	@Test
	public void isActiveEntity_knownIdNotActive_false() {
		pbeService.setActiveEntity(10, KNOWN_ENTITY_ID);
		assertFalse(pbeService.isActiveEntity(10, 88));
	}

	@Test
	public void isActiveEntity_knownIdActive_true() {
		pbeService.setActiveEntity(10, KNOWN_ENTITY_ID);
		assertTrue(pbeService.isActiveEntity(10, KNOWN_ENTITY_ID));
	}

	@Test
	public void getPlayerEntities_unknownAccoundId_empty() {
		Set<Entity> entities = pbeService.getPlayerEntities(5798);
		Assert.assertEquals(0, entities.size());
	}

	@Test
	public void getPlayerEntities_knownAccoundId_returns() {
		pbeService.putPlayerEntity(playerEntity);
		Set<Entity> entities = pbeService.getPlayerEntities(KNOWN_ACC_ID);
		assertTrue(entities.contains(playerEntity));
	}

	@Test
	public void getActiveAccountIdsInRange_nullRange_throws() {
		pbeService.getActiveAccountIdsInRange(null);
	}

	@Test
	public void getActiveAccountIdsInRange_outOfRangerRect_empty() {
		Rect r = new Rect(100, 100, 10, 10);
		List<Long> ids = pbeService.getActiveAccountIdsInRange(r);
		assertTrue(ids.isEmpty());
	}

	@Test
	public void getActiveAccountIdsInRange_inRangerRect_containsAccId() {
		Rect r = new Rect(0, 0, 100, 100);
		List<Long> ids = pbeService.getActiveAccountIdsInRange(r);
		assertTrue(ids.contains(KNOWN_ACC_ID));
	}

	@Test
	public void getPlayerEntities_unknownId_empty() {
		Set<Entity> entities = pbeService.getPlayerEntities(123);
		assertTrue(entities.isEmpty());
	}

	@Test
	public void getPlayerEntities_knownId_entity() {
		Set<Entity> entities = pbeService.getPlayerEntities(KNOWN_ACC_ID);
		assertTrue(entities.contains(playerEntity));
	}

	@Test
	public void getMasterEntity_unknownId_empty() {
		Optional<Entity> master = pbeService.getMasterEntity(UNKNOWN_ENTITY_ID);
		assertFalse(master.isPresent());
	}

	@Test
	public void getMasterEntity_knownId_entity() {
		Optional<Entity> master = pbeService.getMasterEntity(KNOWN_ACC_ID);
		assertTrue(master.isPresent());
		assertTrue(master.get().equals(masterEntity));
	}

	@Test(expected = NullPointerException.class)
	public void putPlayerEntities_null_throws() {
		pbeService.putPlayerEntities(null);
	}

	@Test
	public void putPlayerEntities_collectionContainingEntities_works() {

		HashSet<Entity> playerEntities = new HashSet<>();

		for (int i = 0; i < 3; i++) {
			Entity player = mock(Entity.class);
			PlayerComponent pc = mock(PlayerComponent.class);
			when(pc.getId()).thenReturn(2L * i);
			when(pc.getOwnerAccountId()).thenReturn(KNOWN_ACC_ID);
			when(pc.getPlayerBestiaId()).thenReturn(i * 3L);
			when(player.getId()).thenReturn(i * 7L);
			when(entityService.hasComponent(player, PlayerComponent.class)).thenReturn(true);
			when(entityService.getComponent(player, PlayerComponent.class)).thenReturn(Optional.of(pc));
			playerEntities.add(player);
		}

		pbeService.putPlayerEntities(playerEntities);

		for (Entity e : pbeService.getPlayerEntities(KNOWN_ACC_ID)) {
			assertTrue(playerEntities.contains(e));
		}
	}

	@Test
	public void hasPlayerEntity_unknownAcc_false() {
		Assert.assertFalse(pbeService.hasPlayerEntity(67, 12));
	}

	@Test
	public void hasPlayerEntity_knownAccWrongEntityId_false() {
		Assert.assertFalse(pbeService.hasPlayerEntity(KNOWN_ACC_ID, UNKNOWN_ENTITY_ID));
	}

	@Test
	public void hasPlayerEntity_knownAccOkEntityId_true() {
		assertFalse(pbeService.hasPlayerEntity(KNOWN_ACC_ID, KNOWN_ENTITY_ID));
	}

	@Test(expected = NullPointerException.class)
	public void putPlayerEntity_null_throws() {
		pbeService.putPlayerEntity(null);
	}

	@Test
	public void putPlayerEntity_nonPlayerEntity_ignored() {
		long nonPlayerId = 69;
		Entity nonPlayer = mock(Entity.class);
		when(nonPlayer.getId()).thenReturn(nonPlayerId);
		when(entityService.hasComponent(nonPlayer, PlayerComponent.class)).thenReturn(false);
		when(entityService.getComponent(nonPlayer, PlayerComponent.class)).thenReturn(Optional.empty());
		when(entityService.getComponent(nonPlayerId, PlayerComponent.class)).thenReturn(Optional.empty());
		pbeService.putPlayerEntity(nonPlayer);

		// We have no access to the internal HZ map so we can not check the non
		// adding.
	}

	@Test
	public void putPlayerEntity_playerEntity_added() {
		final long nonPlayerId = 69;
		final long pbId = 1234;

		Entity player = mock(Entity.class);
		PlayerComponent pc = mock(PlayerComponent.class);
		when(pc.getId()).thenReturn(1L);
		when(pc.getOwnerAccountId()).thenReturn(KNOWN_ACC_ID);
		when(pc.getPlayerBestiaId()).thenReturn(pbId);
		when(player.getId()).thenReturn(nonPlayerId);
		when(entityService.hasComponent(player, PlayerComponent.class)).thenReturn(true);
		when(entityService.getComponent(player, PlayerComponent.class)).thenReturn(Optional.of(pc));
		when(entityService.getComponent(nonPlayerId, PlayerComponent.class)).thenReturn(Optional.of(pc));
		pbeService.putPlayerEntity(player);

		assertTrue(pbeService.getPlayerEntities(KNOWN_ACC_ID).contains(player));
	}

	// removePlayerBestias

	// removePlayerBestia
}
