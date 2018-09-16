package bestia.memoryserver.persistance;

import net.bestia.entity.Entity;
import net.bestia.entity.EntityService;
import net.bestia.entity.component.TagComponent;
import net.bestia.memoryserver.persistance.EntityPersistService;
import net.bestia.model.dao.EntityDataDAO;
import net.bestia.model.domain.EntityData;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class EntityPersistServiceTest {

	private EntityPersistService service;

	@Mock
	private EntityDataDAO entityDao;

	@Mock
	private EntityService entityService;

	@Mock
	private Entity taggedEntity;
	
	@Mock
	private Entity untaggedEntity;

	@Mock
	private TagComponent tagComp;

	@Before
	public void setup() {

		when(entityService.getComponent(taggedEntity, TagComponent.class)).thenReturn(Optional.of(tagComp));
		when(entityService.getComponent(untaggedEntity, TagComponent.class)).thenReturn(Optional.empty());
		
		when(tagComp.has(TagComponent.Tag.PERSIST)).thenReturn(true);

		service = new EntityPersistService(entityDao, entityService);
	}

	@Test(expected = NullPointerException.class)
	public void store_null_throws() {
		service.store(null);
	}

	@Test
	public void store_taggedEntity_stores() {
		service.store(taggedEntity);
		verify(entityDao).save(any(EntityData.class));
	}
	
	@Test
	public void store_untaggedEntity_dontStore() {
		service.store(untaggedEntity);
		verify(entityDao, never()).save(any(EntityData.class));
	}
}
