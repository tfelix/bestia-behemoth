package net.bestia.zoneserver.map.path;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import net.bestia.model.geometry.Point;
import net.bestia.model.map.Map;
import net.bestia.zoneserver.entity.EntityService;

@RunWith(MockitoJUnitRunner.class)
public class MapPathfinderFactoryTest {
	
	private MapPathfinderFactory factory;
	
	@Mock
	private EntityService entityService;
	
	@Mock
	private Map map;
	
	@Before
	public void setup() {
		factory = new MapPathfinderFactory(entityService);
	}
	
	@Test(expected = NullPointerException.class)
	public void ctor_nullService_throws() {
		new MapPathfinderFactory(null);
	}
	
	@Test(expected = NullPointerException.class)
	public void getPathfinder_nullMap_throws() {
		factory.getPathfinder(null);
	}
	
	@Test
	public void getPathfinder_validMap_validPathfinder() {
		Pathfinder<Point> finder = factory.getPathfinder(map);
		Assert.assertNotNull(finder);
	}
}
