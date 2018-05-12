package bestia.memoryserver.persistance;

import static org.mockito.Mockito.verify;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import net.bestia.memoryserver.persistance.ComponentMapStore;
import net.bestia.memoryserver.persistance.ComponentPersistService;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import net.bestia.entity.component.Component;


@RunWith(MockitoJUnitRunner.class)
public class ComponentMapStoreTest {

	private final static long VALID_COMP_ID = 123;
	
	private ComponentMapStore store;
	
	@Mock
	private Component component;
	
	@Mock
	private ComponentPersistService compPersistService;
	
	@Before
	public void setup() {
		
		store = new ComponentMapStore(compPersistService);
	}
	
	@Test
	public void load_validId_loads() {
		store.load(VALID_COMP_ID);
		verify(compPersistService).load(VALID_COMP_ID);
	}
	
	@Test
	public void loadAll_validIds_loads() {
		store.loadAll(Arrays.asList(VALID_COMP_ID));
		verify(compPersistService).load(VALID_COMP_ID);
	}
	
	@Test
	public void delete_validId_delete() {
		store.delete(VALID_COMP_ID);
		verify(compPersistService).delete(VALID_COMP_ID);
	}
	
	@Test
	public void deleteAll_validIds_delete() {
		store.deleteAll(Arrays.asList(VALID_COMP_ID));
		verify(compPersistService).delete(VALID_COMP_ID);
	}
	
	@Test
	public void store_validId_delete() {
		store.store(VALID_COMP_ID, component);
		verify(compPersistService).store(component);
	}
	
	@Test
	public void storeAllAll_validIds_delete() {
		Map<Long, Component> data = new HashMap<>();
		
		data.put(VALID_COMP_ID, component);
		store.storeAll(data);
		
		verify(compPersistService).store(component);
	}
}