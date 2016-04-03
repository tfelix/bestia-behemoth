package net.bestia.zoneserver.zone.map.tmx;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Properties;

import net.bestia.zoneserver.zone.map.Map.MapBuilder;
import net.bestia.zoneserver.zone.map.Tile;
import net.bestia.zoneserver.zone.shape.Vector2;
import tiled.core.Map;
import tiled.core.MapLayer;
import tiled.core.TileLayer;

public class TileDataExtender implements TMXMapExtender {
	
	public TileDataExtender() {
		// no op.
	}

	@Override
	public void extendMap(Map tiledMap, MapBuilder builder) {
		int numLayer = tiledMap.getLayerCount();

		// We must sort the layer since order is not guranteed.
		List<TileLayer> layers = new ArrayList<>();

		// Extract all ground layers.
		for (int i = 0; i < numLayer; i++) {

			MapLayer layer = tiledMap.getLayer(i);

			TileLayer tLayer;
			if (layer instanceof TileLayer) {
				tLayer = (TileLayer) layer;
			} else {
				continue;
			}

			// Ignore non bottom/ground layers. (Like special sound layer, event
			// trigger etc.)
			final String layerName = layer.getName().toLowerCase();
			if (!layerName.matches("layer_\\d+")) {
				continue;
			}

			layers.add(tLayer);
		}

		// Sort the layer list. Ascending order.
		layers.sort(new Comparator<MapLayer>() {

			@Override
			public int compare(MapLayer o1, MapLayer o2) {
				// Snip "layer_" away.
				int o1i = Integer.parseInt(o1.getName().substring(6));
				int o2i = Integer.parseInt(o2.getName().substring(6));

				if (o1i == o2i) {
					return 0;
				}
				return (o1i < o2i) ? -1 : 1;
			}

		});

		// Save the size of the map.
		final int height = layers.get(0).getHeight();
		final int width = layers.get(0).getWidth();

		// Iterate bottom up through tiles and layers.
		for (int y = 0; y < height; y++) {
			for (int x = 0; x < width; x++) {

				boolean isWalkable = true;
				int walkspeed = 1000;

				for (int i = 0; i < layers.size(); i++) {

					final TileLayer layer = layers.get(i);
					final tiled.core.Tile tile = layer.getTileAt(x, y);

					if (tile == null) {
						continue;
					}

					Properties p = tile.getProperties();

					if (p == null) {
						continue;
					}

					final String pWalkable = p.getProperty("bWalkable");
					// final String pWalkspeed = p.getProperty("walkspeed");

					if (pWalkable != null && pWalkable.equals("false")) {
						isWalkable = false;
					} else {
						isWalkable = true;
					}
				}

				final Tile mapTile = new Tile(isWalkable, walkspeed);
				builder.tiles.put(new Vector2(x, y), mapTile);
			}
		}
	}

}
