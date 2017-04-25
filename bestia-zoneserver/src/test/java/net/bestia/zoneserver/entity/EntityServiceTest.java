package net.bestia.zoneserver.entity;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.junit4.SpringRunner;

import net.bestia.model.dao.PlayerBestiaDAO;
import net.bestia.zoneserver.actor.ZoneAkkaApi;
import net.bestia.zoneserver.entity.components.PositionComponent;
import net.bestia.zoneserver.entity.components.VisibleComponent;

@RunWith(SpringRunner.class)
@SpringBootTest
public class EntityServiceTest {

	@Autowired
	EntityService entityService;

	@MockBean
	private PlayerBestiaDAO playerBestiaDao;
	
	

	@MockBean
	private ZoneAkkaApi zoneAkkaApi;

	@Test
	public void newEntity_returnsNewEntity() {
		Entity e1 = entityService.newEntity();
		Entity e2 = entityService.newEntity();
		Assert.assertNotEquals(e1, e2);
		Assert.assertNotNull(e1);
	}

	@Test(expected = NullPointerException.class)
	public void addComponent_nullComp_throws() {
		Entity e1 = entityService.newEntity();
		entityService.addComponent(e1, null);
	}

	@Test
	public void addComponent_entityRefAndComponent_isAdded() {
		Entity e1 = entityService.newEntity();
		PositionComponent posComp = entityService.addComponent(e1, PositionComponent.class);
		Assert.assertNotNull(posComp);
		Assert.assertTrue(entityService.hasComponent(e1, PositionComponent.class));
	}

	@Test(expected = NullPointerException.class)
	public void delete_null_throws() {
		entityService.delete(null);
	}

	@Test
	public void delete_notSavedEntity_doesNothing() {
		Entity e = entityService.newEntity();
		entityService.delete(e);
	}

	@Test
	public void delete_savedEntity_deletesIt() {	
		Entity e = entityService.newEntity();
		entityService.delete(e);
		Entity e2 = entityService.getEntity(e.getId());
		Assert.assertNull(e2);
	}

	@Test
	public void save_entity_savedIt() {
		Entity e = entityService.newEntity();
		Entity e2 = entityService.getEntity(e.getId());
		Assert.assertEquals(e, e2);
	}

	@Test
	public void addComponent_entityIdAndComponent_isAdded() {
		Entity e1 = entityService.newEntity();
		PositionComponent posComp = entityService.addComponent(e1, PositionComponent.class);
		Assert.assertNotNull(posComp);
		Assert.assertTrue(entityService.hasComponent(e1, PositionComponent.class));
	}
	
	@Test
	public void hasComponent_notAddedComponent_false() {
		Entity e1 = entityService.newEntity();
		Assert.assertFalse(entityService.hasComponent(e1, VisibleComponent.class));
	}
	
	@Test
	public void hasComponent_addedComponent_true() {
		Entity e1 = entityService.newEntity();
		entityService.addComponent(e1, PositionComponent.class);
		Assert.assertTrue(entityService.hasComponent(e1, PositionComponent.class));
	}
}
