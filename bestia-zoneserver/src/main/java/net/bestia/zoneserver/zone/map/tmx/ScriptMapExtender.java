package net.bestia.zoneserver.zone.map.tmx;

import java.awt.Rectangle;
import java.util.Iterator;
import java.util.Vector;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import net.bestia.zoneserver.zone.map.Map.MapBuilder;
import net.bestia.zoneserver.zone.map.MapScriptTemplate;
import net.bestia.zoneserver.zone.shape.CollisionShape;
import net.bestia.zoneserver.zone.shape.Rect;
import tiled.core.Map;
import tiled.core.MapLayer;
import tiled.core.MapObject;
import tiled.core.ObjectGroup;

/**
 * Adds the scripts to the {@link MapBuilder}.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public class ScriptMapExtender implements TMXMapExtender {

	private final static Logger log = LogManager.getLogger(ScriptMapExtender.class);

	private final static String PROP_TICKRATE = "tickRate";

	public ScriptMapExtender() {
		// no op.
	}

	@Override
	public void extendMap(Map tiledMap, MapBuilder builder) {

		log.trace("Extend map {} with scripts...", builder.mapDbName);

		// Tiles must be quadratic.
		final int tileSize = tiledMap.getTileHeight();

		final Vector<MapLayer> layers = tiledMap.getLayers();
		for (MapLayer layer : layers) {
			if (!(layer instanceof ObjectGroup)) {
				continue;
			}

			final ObjectGroup objLayer = (ObjectGroup) layer;
			final String layerName = objLayer.getName().toLowerCase();
			if (!layerName.equals("scripts")) {
				continue;
			}

			// Iterate over all triggered scripts and create them.
			final Iterator<MapObject> objIter = objLayer.getObjects();
			int createdScripts = 0;

			while (objIter.hasNext()) {
				final MapObject scriptObj = objIter.next();
				final Rectangle bb = scriptObj.getBounds().getBounds();

				// Translate the bb to shape.
				final CollisionShape rect = new Rect(bb.x / tileSize,
						bb.y / tileSize,
						bb.width / tileSize,
						bb.height / tileSize);

				final String tickRateStr = scriptObj.getProperties().getProperty(PROP_TICKRATE);
				if (tickRateStr != null) {
					final int tickRate = Integer.parseInt(tickRateStr);
					final MapScriptTemplate mapScript = new MapScriptTemplate(scriptObj.getName(), rect, tickRate);
					builder.scripts.add(mapScript);
				} else {
					final MapScriptTemplate mapScript = new MapScriptTemplate(scriptObj.getName(), rect);
					builder.scripts.add(mapScript);
				}

				createdScripts++;
			}

			log.trace("Extended map {} with {} scripts(s).", builder.mapDbName, createdScripts);
		}
	}
}
