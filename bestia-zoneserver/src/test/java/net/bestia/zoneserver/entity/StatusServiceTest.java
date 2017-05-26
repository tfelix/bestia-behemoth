package net.bestia.zoneserver.entity;

import org.junit.Before;
import org.junit.Test;

import net.bestia.model.dao.PlayerBestiaDAO;
import net.bestia.zoneserver.entity.component.StatusComponent;

import static org.mockito.Mockito.*;

import java.util.Optional;

public class StatusServiceTest {

	private StatusService statusService;
	private EntityService entityService;
	private PlayerBestiaDAO playerBestiaDao;

	private Entity nonStatusEntity;
	private Entity statusEntity;

	@Before
	public void setup() {

		entityService = mock(EntityService.class);
		playerBestiaDao = mock(PlayerBestiaDAO.class);

		nonStatusEntity = mock(Entity.class);
		statusEntity = mock(Entity.class);

		when(entityService.getComponent(nonStatusEntity, StatusComponent.class)).thenReturn(Optional.empty());

		statusService = new StatusService(entityService, playerBestiaDao);
	}

	@Test(expected = NullPointerException.class)
	public void getStatusBasedValues_null_throws() {
		statusService.getStatusBasedValues(null);
	}

	@Test(expected = IllegalArgumentException.class)
	public void getStatusBasedValues_nonStatusEntity_throws() {
		statusService.getStatusBasedValues(nonStatusEntity);
	}

	@Test(expected = IllegalArgumentException.class)
	public void getStatusBasedValues_statusEntity_returnsValues() {
		statusService.getStatusBasedValues(statusEntity);
	}
	
	
}
