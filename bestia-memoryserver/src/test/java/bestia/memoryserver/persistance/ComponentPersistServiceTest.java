package bestia.memoryserver.persistance;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;

import net.bestia.memoryserver.persistance.ComponentPersistService;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import net.bestia.entity.component.Component;
import bestia.model.dao.ComponentDataDAO;
import bestia.model.domain.ComponentData;

@RunWith(MockitoJUnitRunner.class)
public class ComponentPersistServiceTest {

	private final static long VALID_COMP_ID = 123;

	private ComponentPersistService service;

	@Mock
	private ComponentDataDAO compDao;
	
	@Mock
	private Component comp;

	@Before
	public void setup() {

		service = new ComponentPersistService(compDao);
	}

	@Test
	public void delete_validId_deletes() {
		service.delete(VALID_COMP_ID);
		verify(compDao).delete(VALID_COMP_ID);
	}

	@Test(expected = NullPointerException.class)
	public void store_null_throws() {
		service.store(null);
	}

	@Test
	public void store_validComponent_stores() {
		service.store(comp);
		verify(compDao).save(any(ComponentData.class));
	}

	@Test
	public void load_validId_loads() {
		service.load(VALID_COMP_ID);
		verify(compDao).findOne(VALID_COMP_ID);
	}
}
