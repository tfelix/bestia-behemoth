package net.bestia.memoryserver.persistance;

import static org.mockito.Mockito.verify;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import net.bestia.entity.Entity;


@RunWith(MockitoJUnitRunner.class)
public class EntityMapStoreTest {

	private final static long VALID_ENTITY_ID = 123;
	
	private EntityMapStore store;
	
	@Mock
	private Entity entity;
	
	@Mock
	private EntityPersistService persistService;
	
	public void setup() {
		
		store = new EntityMapStore(persistService);
	}
	
	@Test
	public void load_validId_loads() {
		store.load(VALID_ENTITY_ID);
		verify(store).load(VALID_ENTITY_ID);
	}
	
	@Test
	public void loadAll_validIds_loads() {
		store.loadAll(Arrays.asList(VALID_ENTITY_ID));
		verify(store).load(VALID_ENTITY_ID);
	}
	
	@Test
	public void delete_validId_delete() {
		store.delete(VALID_ENTITY_ID);
		verify(store).delete(VALID_ENTITY_ID);
	}
	
	@Test
	public void deleteAll_validIds_delete() {
		store.deleteAll(Arrays.asList(VALID_ENTITY_ID));
		verify(store).delete(VALID_ENTITY_ID);
	}
	
	@Test
	public void store_validId_delete() {
		store.store(VALID_ENTITY_ID, entity);
		verify(store).store(VALID_ENTITY_ID, entity);
	}
	
	@Test
	public void storeAllAll_validIds_delete() {
		Map<Long, Entity> data = new HashMap<>();
		
		data.put(VALID_ENTITY_ID, entity);
		store.storeAll(data);
		
		verify(store).storeAll(data);
	}
}
