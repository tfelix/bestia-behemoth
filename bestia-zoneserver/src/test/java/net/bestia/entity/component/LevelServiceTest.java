package net.bestia.entity.component;

import net.bestia.entity.Entity;
import net.bestia.entity.EntityService;
import net.bestia.zoneserver.battle.StatusService;
import net.bestia.zoneserver.entity.LevelService;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.Optional;

@RunWith(MockitoJUnitRunner.class)
public class LevelServiceTest {

	private final int LEVEL = 4;

	@Mock
	private EntityService entityService;

	@Mock
	private StatusService statusService;

	@Mock
	private Entity entity;

	@Mock
	private LevelComponent lvComp;

	@Mock
	private Entity nonComponentEntity;

	private LevelService lvService;

	@Before
	public void setup() {

		Mockito.when(lvComp.getLevel()).thenReturn(LEVEL);

		Mockito.when(entityService.getComponent(entity, LevelComponent.class)).thenReturn(Optional.of(lvComp));
		Mockito.when(entityService.getComponent(nonComponentEntity, LevelComponent.class)).thenReturn(Optional.empty());

		lvService = new LevelService(entityService, statusService);
	}

	@Test
	public void setLevel_validValues_levelSet() {

		lvService.setLevel(entity, 10);

		// Verify recalc status values.
		Mockito.verify(lvComp).setLevel(10);
		Mockito.verify(statusService).calculateStatusPoints(entity);
	}

	@Test(expected = IllegalArgumentException.class)
	public void setLevel_0Level_throws() {

		lvService.setLevel(entity, 0);
	}

	@Test(expected = IllegalArgumentException.class)
	public void setLevel_NegativeLevel_throws() {

		lvService.setLevel(entity, -1);
	}

	@Test(expected = IllegalArgumentException.class)
	public void addExp_negExp_throws() {

		lvService.addExp(entity, -10);
	}

	@Test
	public void addExp_validExp_checksLevelUp() {

		lvService.addExp(entity, 100);

		Mockito.verify(statusService).calculateStatusPoints(entity);
		Mockito.verify(entityService).updateComponent(lvComp);
	}

	@Test(expected = IllegalArgumentException.class)
	public void addExp_nonCompEntity_nothing() {

		lvService.addExp(nonComponentEntity, 100);
	}

	@Test
	public void getLevel_nonLevelComponentEntity_returnsMinLevel1() {

		Assert.assertEquals(1, lvService.getLevel(nonComponentEntity));
	}

	@Test
	public void getLevel_levelComponentEntity_returnsLevel() {

		Assert.assertEquals(LEVEL, lvService.getLevel(entity));
	}

	@Test
	public void getExp_nonLevelComponentEntity_returns0() {

		Assert.assertEquals(0, lvService.getExp(nonComponentEntity));
	}
}
