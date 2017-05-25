package net.bestia.zoneserver.entity;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import com.hazelcast.core.HazelcastInstance;

import net.bestia.messages.bestia.BestiaActivateMessage;
import net.bestia.model.domain.PlayerBestia;
import net.bestia.model.geometry.Rect;
import net.bestia.zoneserver.BasicMocks;
import net.bestia.zoneserver.actor.ZoneAkkaApi;
import net.bestia.zoneserver.entity.component.PlayerComponent;
import net.bestia.zoneserver.service.PlayerBestiaService;

public class PlayerEntityServiceTest {
	
	private static final long MASTER_ENTITY_ID = 71;
	private static final long PLAYER_ENTITY_ID = 72;

	private final static long KNOWN_ACC_ID = 666;
	private static final long UNKNOWN_ENTITY_ID = 12;
	
	private static final long MASTER_PB_ID = 42;
	private static final long PLAYER_PB_ID = 43;

	private final static BasicMocks mocks = new BasicMocks();
	private final static HazelcastInstance hz = mocks.hazelcastMock();

	private EntityService entityService;
	private PlayerBestiaService playerBestiaService;
	private ZoneAkkaApi akkaApi;

	private Entity playerEntity;
	private Entity masterEntity;
	
	private PlayerEntityService pbeService;
	private PlayerBestia masterPlayerBestia;
	
	private Map<Long, Entity> allEntities = new HashMap<>();
	

	@Before
	public void setup() {

		entityService = mock(EntityService.class);
		playerBestiaService = mock(PlayerBestiaService.class);
		akkaApi = mock(ZoneAkkaApi.class);

		playerEntity = mock(Entity.class);
		masterEntity = mock(Entity.class);
		
		when(playerEntity.getId()).thenReturn(PLAYER_ENTITY_ID);
		when(masterEntity.getId()).thenReturn(MASTER_ENTITY_ID);
		
		allEntities.put(PLAYER_ENTITY_ID, playerEntity);
		allEntities.put(MASTER_ENTITY_ID, masterEntity);

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

		when(entityService.hasComponent(any(Entity.class), any())).thenReturn(false);
		when(entityService.hasComponent(playerEntity, PlayerComponent.class)).thenReturn(true);
		when(entityService.hasComponent(masterEntity, PlayerComponent.class)).thenReturn(true);
		
		when(entityService.getComponent(any(Entity.class), any())).thenReturn(Optional.empty());
		when(entityService.getComponent(playerEntity, PlayerComponent.class)).thenReturn(Optional.of(playerComponent));
		when(entityService.getComponent(masterEntity, PlayerComponent.class)).thenReturn(Optional.of(masterComponent));
		
		when(entityService.getAllEntities(any())).then(new Answer<Map<Long, Entity>>() {
			@Override
			public Map<Long, Entity> answer(InvocationOnMock invocation) throws Throwable {
				
				@SuppressWarnings("unchecked")
				final Set<Long> ids = (Set<Long>)invocation.getArguments()[0];
				final Map<Long, Entity> foundEntities = new HashMap<>();
				
				if(ids.contains(PLAYER_ENTITY_ID)) {
					foundEntities.put(PLAYER_ENTITY_ID, playerEntity);
				}
				if(ids.contains(MASTER_ENTITY_ID)) {
					foundEntities.put(MASTER_ENTITY_ID, masterEntity);
				}
				
				return foundEntities;
			}
		});
		when(entityService.getEntity(PLAYER_ENTITY_ID)).thenReturn(playerEntity);
		when(entityService.getEntity(MASTER_ENTITY_ID)).thenReturn(masterEntity);
		
		when(playerBestiaService.getMaster(KNOWN_ACC_ID)).thenReturn(masterPlayerBestia);

		pbeService = new PlayerEntityService(hz, entityService, playerBestiaService, akkaApi);

	}

	@Test(expected = IllegalArgumentException.class)
	public void setActiveEntity_unknownEntityId_throws() {
		pbeService.setActiveEntity(KNOWN_ACC_ID, UNKNOWN_ENTITY_ID);
	}

	@Test
	public void setActiveEntity_idSetAndMessageSendToClient() {

		pbeService.setActiveEntity(KNOWN_ACC_ID, PLAYER_ENTITY_ID);

		ArgumentCaptor<BestiaActivateMessage> captor = ArgumentCaptor.forClass(BestiaActivateMessage.class);
		verify(akkaApi, times(1)).sendToClient(captor.capture());
		
		Entity active = pbeService.getActivePlayerEntity(KNOWN_ACC_ID);
		
		assertEquals(playerEntity, active);
		assertEquals(KNOWN_ACC_ID, captor.getValue().getAccountId());
		assertEquals(PLAYER_ENTITY_ID, captor.getValue().getPlayerBestiaId());
	}

	@Test
	public void isActiveEntity_knownIdNotActive_false() {
		pbeService.setActiveEntity(KNOWN_ACC_ID, PLAYER_ENTITY_ID);
		assertFalse(pbeService.isActiveEntity(KNOWN_ACC_ID, MASTER_ENTITY_ID));
	}

	@Test
	public void isActiveEntity_knownIdActive_true() {
		pbeService.setActiveEntity(KNOWN_ACC_ID, PLAYER_ENTITY_ID);
		assertTrue(pbeService.isActiveEntity(KNOWN_ACC_ID, PLAYER_ENTITY_ID));
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
		pbeService.putPlayerEntity(playerEntity);
		pbeService.putPlayerEntity(masterEntity);
		
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
		pbeService.putPlayerEntity(masterEntity);
		pbeService.putPlayerEntity(playerEntity);
		
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
		playerEntities.add(masterEntity);
		playerEntities.add(playerEntity);

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
		assertTrue(pbeService.hasPlayerEntity(KNOWN_ACC_ID, PLAYER_ENTITY_ID));
	}

	@Test(expected = NullPointerException.class)
	public void putPlayerEntity_null_throws() {
		pbeService.putPlayerEntity(null);
	}

	@Test(expected=IllegalArgumentException.class)
	public void putPlayerEntity_noPlayerComponent_throws() {
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
		
		pbeService.putPlayerEntity(playerEntity);
		assertTrue(pbeService.getPlayerEntities(KNOWN_ACC_ID).contains(playerEntity));
	}

	@Test
	public void removePlayerBestias_wrongAccId_nothing() {
		pbeService.removePlayerBestias(123);
	}

	@Test
	public void removePlayerBestias_accIdWithBestias_allBestiasRemoved() {

		pbeService.putPlayerEntity(masterEntity);
		pbeService.putPlayerEntity(playerEntity);
		
		Collection<Entity> entities = pbeService.getPlayerEntities(KNOWN_ACC_ID);

		assertEquals("Account should own two bestias now.", 2, entities.size());

		pbeService.removePlayerBestias(KNOWN_ACC_ID);

		assertEquals("All bestias should be removed.", 0, pbeService.getPlayerEntities(KNOWN_ACC_ID).size());
	}

	@Test(expected = NullPointerException.class)
	public void removePlayerBestia_null_throws() {

		pbeService.removePlayerBestia(null);
	}

	@Test
	public void removePlayerBestia_addedEntity_isRemoved() {
		
		pbeService.putPlayerEntity(masterEntity);
		pbeService.putPlayerEntity(playerEntity);

		assertEquals("Account should own two bestias now.", 2, pbeService.getPlayerEntities(KNOWN_ACC_ID).size());

		pbeService.removePlayerBestia(masterEntity);

		assertEquals("Account should own only one bestias now.", 1, pbeService.getPlayerEntities(KNOWN_ACC_ID).size());
		assertTrue(pbeService.getPlayerEntities(KNOWN_ACC_ID).contains(playerEntity));
	}
}
