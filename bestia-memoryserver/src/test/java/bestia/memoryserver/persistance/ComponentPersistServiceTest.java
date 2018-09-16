package bestia.memoryserver.persistance;

import net.bestia.entity.component.Component;
import net.bestia.memoryserver.persistance.ComponentPersistService;
import net.bestia.model.dao.ComponentDataDAO;
import net.bestia.model.domain.ComponentData;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;

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
		verify(compDao).deleteById(VALID_COMP_ID);
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
}
