package net.bestia.zoneserver.service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import net.bestia.model.dao.MapDataDAO;
import net.bestia.model.dao.MapParameterDAO;
import net.bestia.model.domain.MapData;
import net.bestia.model.domain.MapParameter;
import net.bestia.model.geometry.Point;
import net.bestia.model.geometry.Rect;
import net.bestia.model.map.Map;
import net.bestia.model.map.MapChunk;
import net.bestia.model.map.MapDataDTO;

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

	private final static Logger LOG = LoggerFactory.getLogger(MapService.class);

	private final MapDataDAO mapDataDao;
	private final MapParameterDAO mapParamDao;

	@Autowired
	public MapService(MapDataDAO dataDao, MapParameterDAO paramDao) {

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
		if(x < 0 || y < 0 || width < 0 || height < 0) {
			throw new IllegalArgumentException("X, Y, width and height must be positive.");
		}

		return null;
	}

	/**
	 * Compresses the given byte stream.
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
	 * Saved the given {@link MapDataDTO} to the database for later retrieval.
	 * 
	 * @param dto
	 * @throws IOException
	 */
	public void saveMapData(MapDataDTO dto) throws IOException {
		
		Objects.requireNonNull(dto);

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
		return params == null ? "" : params.getName();
	}

	/**
	 * Returns the name of the area in which this point is lying.
	 * 
	 * @param p
	 *            The coordinates of which the local name should be found.
	 * @return The name of the local area.
	 */
	public String getAreaName(Point p) {
		Objects.requireNonNull(p);
		
		return "Kalarian (mot implemented)";
	}

	/*
	/**
	 * This returns a tileset via its name.
	 * 
	 * @param name
	 * @return
	 */
	/*
	public Tileset getTileset(String name) {
		final IMap<String, Tileset> tilesetData = hazelcastInstance.getMap(TILESET_KEY);
		return tilesetData.get(name);
	}*/

	/**
	 * Returns the tileset depending on the gid of a tile. The system will
	 * perform a lookup in order to find the correct tileset for the given guid.
	 * 
	 * @param gid
	 *            The id of the tile to which the tileset should be found.
	 * @return The found tileset containing the tile with the GID or null if not
	 *         tileset could been found.
	 */
	/*
	public Tileset getTileset(int gid) {
		final IMap<String, Tileset> tilesetData = hazelcastInstance.getMap(TILESET_KEY);

		final Optional<Tileset> ts = tilesetData.values()
				.stream()
				.filter(x -> x.contains(gid))
				.findAny();

		return ts.isPresent() ? ts.get() : null;
	}*/

	/**
	 * Returns a list with all DTOs which are covering the given range.
	 * 
	 * @param x
	 * @param y
	 * @param width
	 * @param height
	 * @return
	 */
	private List<MapDataDTO> getCoveredMapDataDTO(long x, long y, long width, long height) {
		final List<MapData> rawData = mapDataDao.findAllInRange(x, y, width, height);

		if (rawData == null) {
			return Collections.emptyList();
		}

		List<MapDataDTO> dtos = new ArrayList<>(rawData.size());

		for (MapData md : rawData) {
			try {
				byte[] data = uncompress(md.getData());

				try (ByteArrayInputStream bis = new ByteArrayInputStream(data);
						ObjectInputStream ois = new ObjectInputStream(bis)) {

					MapDataDTO dto = (MapDataDTO) ois.readObject();
					dtos.add(dto);
				}
			} catch (IOException | ClassNotFoundException e) {
				LOG.error("Could not deserialize map dto.", e);
			}
		}

		return dtos;
	}

	/**
	 * Returns the chunks of map data given in this list. If the chunks could
	 * not been found an empty list is returned.
	 * 
	 * @param chunkCords
	 * @return
	 */
	public List<MapChunk> getChunks(List<Point> chunkCords) {
		Objects.requireNonNull(chunkCords);

		final List<MapChunk> chunks = new ArrayList<>();

		for (Point point : chunkCords) {
			final Rect area = MapChunk.getWorldRect(point);

			// Prepare the list of ground tiles.
			List<Integer> groundTiles = new ArrayList<>((int) (area.getWidth() * area.getHeight()));

			final List<MapDataDTO> dtos = getCoveredMapDataDTO(area.getX(), area.getY(), area.getWidth(),
					area.getHeight());

			for (long y = area.getOrigin().getY(); y < area.getOrigin().getY() + area.getHeight(); y++) {
				for (long x = area.getOrigin().getX(); x < area.getOrigin().getX() + area.getWidth(); x++) {
					// Find the dto with the point inside.
					final Point curPos = new Point(x, y);

					final Optional<MapDataDTO> data = dtos.stream()
							.filter(dto -> dto.getRect().collide(curPos))
							.findFirst();

					Integer gid = data.map(d -> d.getGroundGid(curPos.getX(), curPos.getY())).orElse(0);
					groundTiles.add(gid);	
				}
			}
			
			// TODO Handle the different layers at this point.
			List<java.util.Map<Point, Integer>> sortedLayers = new ArrayList<>();
			chunks.add(new MapChunk(point, groundTiles, sortedLayers));
		}

		return chunks;
	}
}
