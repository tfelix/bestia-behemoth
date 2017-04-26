package net.bestia.zoneserver.entity;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Set;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;

import net.bestia.messages.bestia.BestiaActivateMessage;
import net.bestia.zoneserver.actor.ZoneAkkaApi;
import net.bestia.zoneserver.service.PlayerBestiaService;

@RunWith(SpringRunner.class)
@SpringBootTest
@ActiveProfiles({ "test" })
public class PlayerEntityServiceTest {

	private final static long KNOWN_ENTITY_ID = 1337;

	@Autowired
	private PlayerEntityService pbeService;

	@MockBean
	private EntityService entityService;

	@MockBean
	private PlayerBestiaService playerBestiaService;

	@MockBean
	private ZoneAkkaApi akkaApi;

	@MockBean
	private Entity playerEntity;

	@Before
	public void setup() {

		when(entityService.getEntity(KNOWN_ENTITY_ID)).thenReturn(playerEntity);

	}

	@Test
	public void getPlayerEntities_unknownAccoundId_empty() {
		Set<Entity> entities = pbeService.getPlayerEntities(1337);
		Assert.assertEquals(0, entities.size());
	}

	@Test(expected = IllegalArgumentException.class)
	public void setActiveEntity_unknownEntityId_throws() {
		pbeService.setActiveEntity(10, 123);
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
}
