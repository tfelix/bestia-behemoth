package net.bestia.zoneserver.zone;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.stub;
import static org.mockito.Mockito.when;

import java.io.File;

import net.bestia.model.ServiceLocator;
import net.bestia.util.BestiaConfiguration;
import net.bestia.zoneserver.Zoneserver;
import net.bestia.zoneserver.command.CommandContext;
import net.bestia.zoneserver.script.ScriptManager;
import net.bestia.zoneserver.zone.map.Map;
import net.bestia.zoneserver.zone.shape.Rect;

import org.junit.Assert;

// TODO Die Zone hier noch richtig testen.
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

	private CommandContext getTestContext() {

		BestiaConfiguration p = new BestiaConfiguration();
		
		File configFile;
		try {
			configFile = new File(ZoneTest.class.getClassLoader()
					.getResource("bestia.properties").toURI());
			p.load(configFile);
		} catch (Exception e) {
			Assert.fail("Could not read test properties file: bestia.properties.");
		}
		
		ScriptManager manager = mock(ScriptManager.class);
		ServiceLocator locator = mock(ServiceLocator.class);
		Zoneserver server = mock(Zoneserver.class);
		
		CommandContext ctx = mock(CommandContext.class);
		
		stub(ctx.getConfiguration()).toReturn(p);
		stub(ctx.getScriptManager()).toReturn(manager);
		stub(ctx.getServiceLocator()).toReturn(locator);
		stub(ctx.getServer()).toReturn(server);
		
		return ctx;
	}

	private Zone getZone() {
		return new Zone(getTestContext(), getTestMap());
	}

}
