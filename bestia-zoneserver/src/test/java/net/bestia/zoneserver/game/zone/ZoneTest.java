package net.bestia.zoneserver.game.zone;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
//import java.time.Duration;
import java.util.Properties;

import org.junit.Assert;
import org.junit.Test;
import org.mockito.Matchers;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import static org.mockito.Mockito.*;
import net.bestia.util.BestiaConfiguration;
import net.bestia.zoneserver.game.zone.Zone.Event;
import net.bestia.zoneserver.game.zone.map.Map;

public class ZoneTest {

	private final static String ZONE_NAME = "test123";

	/**
	 * Returns mocked map.
	 * 
	 * @return
	 */
	private Map getTestMap() {
		Map m = mock(Map.class);

		when(m.getDimension()).thenReturn(new Dimension(100, 100));

		return m;
	}

	private BestiaConfiguration getTestProp() {

		File configFile;
		try {
			configFile = new File(ZoneTest.class.getClassLoader()
					.getResource("bestia.properties").toURI());
			BestiaConfiguration p = new BestiaConfiguration();
			p.load(configFile);
			return p;
		} catch (Exception e) {
			Assert.fail("Could not read test properties file: bestia.properties.");
		}
		
		return null;
	}

	private Zone getZone() {
		return new Zone(getTestProp(), ZONE_NAME, getTestMap());
	}

	@Test
	public void nameTest() {
		Zone z = getZone();
		Assert.assertEquals(ZONE_NAME, z.getName());
	}

	@Test
	public void addEntity_test() {
		Zone z = getZone();

		Entity e1 = mock(Entity.class);
		Entity e2 = mock(Entity.class);

		z.addEntity(e1);
		z.addEntity(e2);

		verify(e1).onEntitySpawn(Matchers.eq(e2));
	}

	@Test
	public void addObserver_test() {
		Zone z = getZone();

		Entity e1 = mock(Entity.class);
		Entity e2 = mock(Entity.class);

		z.addObserver(Event.ON_ENTITY_SPAWN, e1);
		z.addEntity(e2);

		verify(e1).onEntitySpawn(eq(e2));

	}

	@Test
	public void removeObserver_test() {

		Zone z = getZone();

		Entity e1 = mock(Entity.class);
		Entity e2 = mock(Entity.class);

		z.addObserver(Event.ON_ENTITY_SPAWN, e1);
		z.removeObserver(e1);
		z.addEntity(e2);
		verify(e1, times(0)).onEntitySpawn(eq(e2));

		z.removeObserver(e1);
	}

	@Test
	public void removeObserverWithoutAdding_test() {
		Zone z = getZone();
		Entity e1 = mock(Entity.class);
		z.removeObserver(e1);
	}

	@Test
	public void removeEntityWhileObserving_test() {
		Zone z = getZone();

		Entity e1 = mock(Entity.class);
		Entity e2 = mock(Entity.class);

		z.addObserver(Event.ON_ENTITY_SPAWN, e1);
		z.removeEntity(e1);
		z.addEntity(e2);

		//verify(e1, times(0)).onEntitySpawn(any());
	}

	@Test
	public void isWalkable_test() {
		Zone z = getZone();
		Assert.assertEquals(ZONE_NAME, z.getName());
	}

	@Test
	public void getWalkspeed_test() {

	}

	@Test
	public void hasEntity_test() {
		Zone z = getZone();

		Entity e1 = mock(Entity.class);
		when(e1.getId()).thenReturn(new Long(1));

		z.addEntity(e1);
		
		Assert.assertTrue(z.hasEntity(e1));
		Assert.assertTrue(z.hasEntity(e1.getId()));
	}

	@Test
	public void countEntities_test() {

	}

	@Test
	public void scheduleNotify_test() {

		Zone z = getZone();

		Entity e1 = mock(Entity.class);
		when(e1.getId()).thenReturn(new Long(1));

		//z.scheduleNotify(Duration.ofSeconds(1), e1, 1);
		
		try {
			Thread.sleep(600);
		} catch (InterruptedException e) {
			// no op.
		}
		
		//verify(e1, times(0)).onTick(any(), eq(1));
		
		try {
			Thread.sleep(600);
		} catch (InterruptedException e) {
			// no op.
		}
		
		//verify(e1).onTick(any(), eq(1));
	}
}
