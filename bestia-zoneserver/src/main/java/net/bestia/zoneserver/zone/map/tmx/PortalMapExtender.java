package net.bestia.zoneserver.zone.map.tmx;

import java.awt.Rectangle;
import java.util.Iterator;
import java.util.Vector;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import net.bestia.model.domain.LocationDomain;
import net.bestia.model.domain.Location;
import net.bestia.zoneserver.zone.map.Map.MapBuilder;
import net.bestia.zoneserver.zone.map.MapPortalScript;
import net.bestia.zoneserver.zone.shape.CollisionShape;
import net.bestia.zoneserver.zone.shape.Rect;
import tiled.core.Map;
import tiled.core.MapLayer;
import tiled.core.MapObject;
import tiled.core.ObjectGroup;

/**
 * Adds the map portals to the {@link MapBuilder}.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public class PortalMapExtender implements TMXMapExtender {

	private final static Logger LOG = LogManager.getLogger(PortalMapExtender.class);

	@Override
	public void extendMap(Map tiledMap, MapBuilder builder) {

		LOG.trace("Extend map {} with portals...", builder.mapDbName);
		
		final int tileHeight = tiledMap.getTileHeight();
		final int tileWidth = tiledMap.getTileWidth();

		final Vector<MapLayer> layers = tiledMap.getLayers();
		for (MapLayer layer : layers) {
			if (!(layer instanceof ObjectGroup)) {
				continue;
			}

			final ObjectGroup objLayer = (ObjectGroup) layer;

			// Basically we are looking for two layers: the portal layer and the
			// "normal" script layer.
			final String layerName = objLayer.getName().toLowerCase();
			if (!layerName.equals("portals")) {
				continue;
			}

			// Create the portal scripts.
			final Iterator<MapObject> objIter = objLayer.getObjects();
			int createdPortals = 0;
			
			while (objIter.hasNext()) {
				final MapObject mapObj = objIter.next();
				final Rectangle bb = mapObj.getBounds();

				// Translate the bb to shape.
				final CollisionShape rect = new Rect(bb.x / tileWidth, bb.y / tileHeight, bb.width / tileWidth, bb.height / tileHeight);
				final Location dest = parseDestination(mapObj.getName());

				if (dest == null) {
					LOG.warn("Malformed portal name: {}. Should be: MAP_DB_NAME,X,Y", mapObj.getName());
					continue;
				}
				
				final MapPortalScript portalScript = new MapPortalScript(dest, rect);
				builder.portals.add(portalScript);
				
				createdPortals++;
			}

			LOG.trace("Extended map {} with {} portal(s).", builder.mapDbName, createdPortals);
		}
	}

	private Location parseDestination(String name) {
		final String[] tokens = name.split(",");

		// Sanity checks.
		if (tokens.length != 3) {
			return null;
		}
		try {
			final int x = Integer.parseInt(tokens[1]);
			final int y = Integer.parseInt(tokens[2]);
			return new LocationDomain(tokens[0], x, y);
		} catch (NumberFormatException ex) {
			return null;
		}
	}

}
