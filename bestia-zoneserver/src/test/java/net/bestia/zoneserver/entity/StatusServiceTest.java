package net.bestia.zoneserver.entity;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import net.bestia.model.dao.PlayerBestiaDAO;
import net.bestia.model.domain.BaseValues;
import net.bestia.model.domain.PlayerBestia;
import net.bestia.model.domain.StatusPoints;
import net.bestia.model.entity.StatusBasedValues;
import net.bestia.zoneserver.entity.component.LevelComponent;
import net.bestia.zoneserver.entity.component.PlayerComponent;
import net.bestia.zoneserver.entity.component.StatusComponent;

public class StatusServiceTest {
	
	private final static long PLAYER_BESTIA_ID = 123;

	private StatusService statusService;
	private EntityService entityService;
	private PlayerBestiaDAO playerBestiaDao;
	private StatusComponent statusComp;
	private PlayerComponent playerComp;
	private PlayerBestia playerBestia;
	private LevelComponent levelComp;
	private BaseValues baseValues;

	private Entity nonStatusEntity;
	private Entity statusEntity;

	@Before
	public void setup() {

		entityService = mock(EntityService.class);
		playerBestiaDao = mock(PlayerBestiaDAO.class);
		statusComp = new StatusComponent(10, 10);
		levelComp = mock(LevelComponent.class);

		playerComp = mock(PlayerComponent.class);
		nonStatusEntity = mock(Entity.class);
		statusEntity = mock(Entity.class);
		playerBestia = mock(PlayerBestia.class);
		baseValues = mock(BaseValues.class);
		
		when(levelComp.getLevel()).thenReturn(10);
		
		when(playerComp.getPlayerBestiaId()).thenReturn(PLAYER_BESTIA_ID);
		
		when(baseValues.getAgility()).thenReturn(10);
		when(baseValues.getAttack()).thenReturn(10);
		when(baseValues.getVitality()).thenReturn(10);
		when(baseValues.getIntelligence()).thenReturn(10);
		when(baseValues.getWillpower()).thenReturn(10);
		when(baseValues.getHp()).thenReturn(10);
		when(baseValues.getMana()).thenReturn(10);

		when(entityService.getComponent(nonStatusEntity, StatusComponent.class)).thenReturn(Optional.empty());
		when(entityService.getComponent(statusEntity, StatusComponent.class)).thenReturn(Optional.of(statusComp));
		when(entityService.getComponent(statusEntity, LevelComponent.class)).thenReturn(Optional.of(levelComp));
		when(entityService.getComponent(statusEntity, PlayerComponent.class)).thenReturn(Optional.of(playerComp));
		
		when(playerBestiaDao.findOne(PLAYER_BESTIA_ID)).thenReturn(playerBestia);
		
		when(playerBestia.getBaseValues()).thenReturn(baseValues);
		when(playerBestia.getEffortValues()).thenReturn(baseValues);
		when(playerBestia.getIndividualValue()).thenReturn(baseValues);
		when(playerBestia.getCurrentHp()).thenReturn(50);
		when(playerBestia.getCurrentMana()).thenReturn(50);

		statusService = new StatusService(entityService, playerBestiaDao);
	}

	@Test(expected = NullPointerException.class)
	public void getStatusBasedValues_null_throws() {
		statusService.getStatusBasedValues(null);
	}

	@Test
	public void getStatusBasedValues_nonStatusEntity_empty() {
		Optional<StatusBasedValues> sp = statusService.getStatusBasedValues(nonStatusEntity);
		Assert.assertFalse(sp.isPresent());
	}

	@Test
	public void getStatusBasedValues_statusEntity_returnsValues() {
		Optional<StatusBasedValues> statBased = statusService.getStatusBasedValues(statusEntity);
		Assert.assertTrue(statBased.isPresent());
	}
	
	@Test(expected = NullPointerException.class)
	public void getStatusPoints_null_throws() {
		statusService.getStatusPoints(null);
	}

	@Test
	public void getStatusPoints_nonStatusEntity_empty() {
		Optional<StatusPoints> sp = statusService.getStatusPoints(nonStatusEntity);
		Assert.assertFalse(sp.isPresent());
	}

	@Test
	public void getStatusPoints_statusEntity_returnsValues() {
		Optional<StatusPoints> sp = statusService.getStatusPoints(statusEntity);
		Assert.assertTrue(sp.isPresent());
	}
	
	@Test(expected = NullPointerException.class)
	public void getUnmodifiedStatusPoints_null_throws() {
		statusService.getUnmodifiedStatusPoints(null);
	}

	@Test
	public void getUnmodifiedStatusPoints_nonStatusEntity_empty() {
		Optional<StatusPoints> sp = statusService.getUnmodifiedStatusPoints(nonStatusEntity);
		Assert.assertFalse(sp.isPresent());
	}

	@Test
	public void getUnmodifiedStatusPoints_statusEntity_returnsValues() {
		Optional<StatusPoints> sp = statusService.getUnmodifiedStatusPoints(statusEntity);
		Assert.assertTrue(sp.isPresent());
	}
	
	@Test(expected=NullPointerException.class)
	public void calculateStatusPoints_nullEntity_throws() {
		statusService.calculateStatusPoints(null);
	}
	
	@Test
	public void calculateStatusPoints_validEntity_recalculatesStatus() {
		statusService.calculateStatusPoints(statusEntity);
		
		Optional<StatusPoints> sp = statusService.getStatusPoints(statusEntity);
		
		Assert.assertTrue(sp.isPresent());
		
		Assert.assertNotEquals(0, sp.get().getAgility());
		Assert.assertNotEquals(0, sp.get().getVitality());
		Assert.assertNotEquals(0, sp.get().getDexterity());
		Assert.assertNotEquals(0, sp.get().getStrength());
		Assert.assertNotEquals(0, sp.get().getIntelligence());
		Assert.assertNotEquals(0, sp.get().getWillpower());
	}
}
