package net.bestia.entity;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import net.bestia.entity.component.LevelComponent;

import static org.mockito.Mockito.*;

import java.util.Optional;

@RunWith(MockitoJUnitRunner.class)
public class LevelServiceTest {

	@Mock
	private EntityService entityService;

	@Mock
	private StatusService statusService;

	@Mock
	private Entity entity;

	@Mock
	private LevelComponent lvComp;

	private LevelService lvService;

	@Before
	public void setup() {

		when(entityService.getComponent(entity, LevelComponent.class)).thenReturn(Optional.of(lvComp));

		lvService = new LevelService(entityService, statusService);
	}

	@Test
	public void setLevel_validValues_levelSet() {

		lvService.setLevel(entity, 10);

		// Verify recalc status values.
		verify(lvComp).setLevel(10);
		verify(statusService).calculateStatusPoints(entity);
	}

	@Test(expected = IllegalArgumentException.class)
	public void setLevel_0Level_throws() {

		lvService.setLevel(entity, 0);
	}

	@Test(expected = IllegalArgumentException.class)
	public void setLevel_NegativeLevel_throws() {

		lvService.setLevel(entity, -1);
	}
}
