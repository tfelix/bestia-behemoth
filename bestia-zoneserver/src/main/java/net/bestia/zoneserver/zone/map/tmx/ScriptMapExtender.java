package net.bestia.zoneserver.zone.map.tmx;

import java.awt.Rectangle;
import java.util.Iterator;
import java.util.Vector;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import net.bestia.zoneserver.zone.map.Map.MapBuilder;
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
	
	public ScriptMapExtender() {
		// no op.
	}

	@Override
	public void extendMap(Map tiledMap, MapBuilder builder) {

		log.trace("Extend map {} with scripts...", builder.mapDbName);

		final Vector<MapLayer> layers = tiledMap.getLayers();
		for (MapLayer layer : layers) {
			if (!(layer instanceof ObjectGroup)) {
				continue;
			}

			final ObjectGroup objLayer = (ObjectGroup) layer;

			// Basically we are looking for two layers: the portal layer and the
			// "normal" script layer.
			final String layerName = objLayer.getName().toLowerCase();
			if (!layerName.equals("scripts")) {
				continue;
			}

			// Create the portal scripts.
			final Iterator<MapObject> objIter = objLayer.getObjects();
			int createdScripts = 0;
			while (objIter.hasNext()) {
				final MapObject mapObj = objIter.next();
				final Rectangle bb = mapObj.getBounds();

				// Translate the bb to shape.
				final CollisionShape rect = new Rect(bb.x, bb.y, bb.width, bb.height);

				// TODO Hier noch die MapScripte hinzufügen.
				
				createdScripts++;
			}

			log.trace("Extended map {} with {} scripts(s).", builder.mapDbName, createdScripts);
		}
	}
}
