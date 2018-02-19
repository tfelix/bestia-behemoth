package bestia.memoryserver.persistance;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import bestia.entity.Entity;
import bestia.entity.EntityService;
import bestia.entity.component.TagComponent;
import bestia.model.dao.EntityDataDAO;
import bestia.model.domain.EntityData;

@RunWith(MockitoJUnitRunner.class)
public class EntityPersistServiceTest {

	private final static long VALID_ID = 123;

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

	@Test
	public void delete_validId_deletes() {
		service.delete(VALID_ID);
		verify(entityDao).delete(VALID_ID);
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

	@Test
	public void load_validId_loads() {
		service.load(VALID_ID);
		verify(entityDao).findOne(VALID_ID);
	}
}
