package net.bestia.zoneserver.game.zone;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.File;

import net.bestia.util.BestiaConfiguration;
import net.bestia.zoneserver.game.zone.map.Map;

import org.junit.Assert;
import org.junit.Test;

public class ZoneTest {

	private final static String ZONE_NAME = "test123";

	/**
	 * Returns mocked map.
	 * 
	 * @return
	 */
	private Map getTestMap() {
		Map m = mock(Map.class);

		when(m.getDimension()).thenReturn(new Rect(100, 100));

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
		return new Zone(getTestProp(), getTestMap());
	}

	@Test
	public void nameTest() {
		Zone z = getZone();
		Assert.assertEquals(ZONE_NAME, z.getName());
	}


	@Test
	public void isWalkable_test() {
		Zone z = getZone();
		Assert.assertEquals(ZONE_NAME, z.getName());
	}
}
