package net.bestia.zoneserver.service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.core.IMap;

import net.bestia.model.dao.MapDataDAO;
import net.bestia.model.dao.MapParameterDAO;
import net.bestia.model.dao.TileDAO;
import net.bestia.model.domain.MapData;
import net.bestia.model.domain.MapParameter;
import net.bestia.model.domain.Tile;
import net.bestia.model.geometry.Point;
import net.bestia.model.geometry.Rect;
import net.bestia.model.map.Map;
import net.bestia.model.map.MapChunk;
import net.bestia.model.map.MapDataDTO;
import net.bestia.model.map.Tileset;

/**
 * The {@link MapService} is central instance for requesting and modifing map
 * data operation inside the bestia system. It is responsible for effectively
 * querying the cache in order to get map data from the in memory db since we
 * can not simply load the fully map into memory as a single map.
 * 
 * @author Thomas Felix
 *
 */
@Service
public class MapService {

	private final static String TILESET_KEY = "map.tilesets";

	private final TileDAO tileDao;
	private final MapDataDAO mapDataDao;
	private final MapParameterDAO mapParamDao;

	private final HazelcastInstance hazelcastInstance;

	@Autowired
	public MapService(HazelcastInstance hz, TileDAO tileDao, MapDataDAO dataDao, MapParameterDAO paramDao) {

		this.hazelcastInstance = Objects.requireNonNull(hz);
		this.tileDao = Objects.requireNonNull(tileDao);
		this.mapDataDao = Objects.requireNonNull(dataDao);
		this.mapParamDao = Objects.requireNonNull(paramDao);
	}

	/**
	 * Checks if there is a initialized map inside the permanent storage is
	 * active.
	 * 
	 * @return TRUE if the map is initialized. FALSE otherwise.
	 */
	public boolean isMapInitialized() {
		return mapDataDao.count() > 0;
	}

	/**
	 * Retrieves and generates the map. It has the dimensions of the given
	 * coordiantes.
	 * 
	 * @param x
	 * @param y
	 * @param width
	 * @param height
	 * @return
	 */
	public Map getMap(long x, long y, long width, long height) {

		return null;
	}

	/**
	 * Compresses the given bytestream.
	 * 
	 * @param input
	 *            The byte stream of the compression.
	 * @return The compressed byte stream.
	 * @throws IOException
	 *             If the object could not be compressed.
	 */
	private byte[] compress(byte[] input) throws IOException {

		try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream(input.length)) {

			final Deflater deflater = new Deflater(7);
			deflater.setInput(input);
			deflater.finish();

			byte[] buffer = new byte[1024];
			while (!deflater.finished()) {
				int count = deflater.deflate(buffer);
				outputStream.write(buffer, 0, count);
			}

			deflater.end();

			return outputStream.toByteArray();
		}
	}

	private byte[] uncompress(byte[] input) throws IOException {

		final Inflater inflater = new Inflater();
		// Decompress the bytes
		inflater.setInput(input, 0, input.length);

		try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream(input.length * 2)) {

			byte[] buffer = new byte[1024];
			while (!inflater.finished()) {
				try {
					int count = inflater.inflate(buffer);
					outputStream.write(buffer, 0, count);
				} catch (DataFormatException e) {
					// Rethrow as IO
					throw new IOException(e);
				}
			}

			inflater.end();

			return outputStream.toByteArray();
		}
	}

	/**
	 * This will fetch and decompress the requested map data from the database.
	 * 
	 * @return The decompressed {@link MapDataDTO}.
	 */
	private MapDataDTO getMapData(long x, long y, long width, long height) {

		return null;
	}

	public void saveMapData(MapDataDTO dto) throws IOException {

		try (ByteArrayOutputStream bos = new ByteArrayOutputStream();
				ObjectOutputStream oos = new ObjectOutputStream(bos)) {
			oos.writeObject(dto);

			byte[] output = compress(bos.toByteArray());

			// Now create the map data object and save it to the database.
			final MapData mapData = new MapData();
			mapData.setData(output);
			mapData.setHeight(dto.getRect().getHeight());
			mapData.setWidth(dto.getRect().getWidth());
			mapData.setX(dto.getRect().getX());
			mapData.setY(dto.getRect().getY());
			mapDataDao.save(mapData);
		}
	}

	/**
	 * Returns the name of the current map/world.
	 * 
	 * @return The name of the map.
	 */
	public String getMapName() {
		final MapParameter params = mapParamDao.getLatest();
		return params == null ? null : params.getName();
	}

	/**
	 * Returns the name of the area in which this point is lying.
	 * 
	 * @param p
	 *            The coordinates of which the local name should be found.
	 * @return The name of the local area.
	 */
	public String getAreaName(Point p) {
		return "Kalarian";
	}

	/**
	 * Retrieves all the needed map data from the database and build a map
	 * object around it in order to access it.
	 * 
	 * @param range
	 * @return
	 */
	public Map getMap(Rect range) {

		return null;
	}

	/**
	 * Returns a tileset via its name.
	 * 
	 * @param name
	 * @return
	 */
	public Tileset getTileset(String name) {
		final IMap<String, Tileset> tilesetData = hazelcastInstance.getMap(TILESET_KEY);
		return tilesetData.get(name);
	}

	/**
	 * Returns the tileset depending on the gid of a tile. The system will
	 * perform a lookup in order to find the correct tileset for the given guid.
	 * 
	 * @param gid
	 *            The id of the tile to which the tileset should be found.
	 * @return The found tileset containing the tile with the GID or null if not
	 *         tileset could been found.
	 */
	public Tileset getTileset(int gid) {
		final IMap<String, Tileset> tilesetData = hazelcastInstance.getMap(TILESET_KEY);

		final Optional<Tileset> ts = tilesetData.values()
				.stream()
				.filter(x -> x.contains(gid))
				.findAny();

		return ts.isPresent() ? ts.get() : null;
	}

	/**
	 * Returns the chunks of map data given in this list. If the chunks could
	 * not been found an empty list is returned.
	 * 
	 * @param chunkCords
	 * @return
	 */
	public List<MapChunk> getChunks(List<Point> chunkCords) {

		final List<MapChunk> chunks = new ArrayList<>();

		for (Point point : chunkCords) {
			final Rect area = MapChunk.getWorldRect(point);
			// -1 because from to coordinates are on less then height (start to
			// count at the start coordiante).
			List<Tile> tiles = tileDao.getTilesInRange(area.getX(), area.getY(),
					area.getHeight() - 1,
					area.getWidth() - 1);

			// Now filter the tiles for the ground layer.
			List<Integer> groundTiles = tiles.stream()
					.filter(t -> t.getLayer() == 0)
					.map(Tile::getGid)
					.collect(Collectors.toList());

			// Now filter and group the tiles by layers.
			java.util.Map<Short, List<Tile>> layers = tiles.stream()
					.filter(t -> t.getLayer() != 0)
					.collect(Collectors.groupingBy(Tile::getLayer));

			List<java.util.Map<Point, Integer>> sortedLayers = new ArrayList<>();

			for (Entry<Short, List<Tile>> entry : layers.entrySet()) {

				java.util.Map<Point, Integer> temp = new HashMap<>();

				for (Tile t : entry.getValue()) {
					temp.put(new Point(t.getX(), t.getY()), t.getGid());
				}

				sortedLayers.add(temp);
			}

			chunks.add(new MapChunk(point, groundTiles, sortedLayers));
		}

		return chunks;
	}
}
