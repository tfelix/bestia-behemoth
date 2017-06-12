package net.bestia.zoneserver.map;

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
import java.util.Set;
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
import net.bestia.model.map.Map.MapBuilder;
import net.bestia.zoneserver.entity.Entity;
import net.bestia.zoneserver.entity.EntityService;
import net.bestia.zoneserver.entity.component.TagComponent;

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

	public final static String AREA_TAG_NAME = "areaname";

	/**
	 * Max sight range for the client in tiles in every direction.
	 */
	public static int SIGHT_RANGE_TILES = 32;

	private final MapDataDAO mapDataDao;
	private final MapParameterDAO mapParamDao;
	private final EntityService entityService;

	@Autowired
	public MapService(MapDataDAO dataDao, MapParameterDAO paramDao, EntityService entityService) {

		this.mapDataDao = Objects.requireNonNull(dataDao);
		this.mapParamDao = Objects.requireNonNull(paramDao);
		this.entityService = Objects.requireNonNull(entityService);
	}

	/**
	 * Returns the rect which lies inside the sight range of the position.
	 * 
	 * @param pos
	 *            The position to generate the view area around.
	 * @return The viewable rect.
	 */
	public static Rect getViewRect(Point pos) {
		final Rect viewArea = new Rect(
				pos.getX() - SIGHT_RANGE_TILES,
				pos.getY() - SIGHT_RANGE_TILES,
				pos.getX() + SIGHT_RANGE_TILES,
				pos.getY() + SIGHT_RANGE_TILES);
		return viewArea;
	}

	/**
	 * Returns the rectangular which is used for updating the clients. It is
	 * usually larger than the {@link #getViewRect(Point)}.
	 * 
	 * @param pos
	 *            The position to generate the view area around.
	 * @return The viewable rect.
	 */
	public static Rect getUpdateRect(Point pos) {
		final Rect viewArea = new Rect(
				pos.getX() - SIGHT_RANGE_TILES * 2,
				pos.getY() - SIGHT_RANGE_TILES * 2,
				pos.getX() + SIGHT_RANGE_TILES * 2,
				pos.getY() + SIGHT_RANGE_TILES * 2);
		return viewArea;
	}

	/**
	 * Checks if the chunks coordinates lie within the range reachable from the
	 * given point. This is important so the client can not request chunk ids
	 * not visible by it.
	 * 
	 * @param pos
	 *            Current position of the player.
	 * @param chunks
	 *            A list of chunk coordinates.
	 * @return TRUE if all chunks are within reach. FALSE otherwise.
	 */
	public static boolean areChunksInClientRange(Point pos, List<Point> chunks) {
		Objects.requireNonNull(pos);
		Objects.requireNonNull(chunks);

		// Find min max dist.
		final double maxD = Math.ceil(Math.sqrt(2 * (SIGHT_RANGE_TILES * SIGHT_RANGE_TILES)));

		final boolean isTooFar = chunks.stream()
				.map(p -> MapChunk.getWorldCords(p))
				.filter(wp -> wp.getDistance(pos) > maxD)
				.findAny()
				.isPresent();

		return !isTooFar;
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
	 * coordinates and contains all layers and tilemap data.
	 * 
	 * @param x
	 * @param y
	 * @param width
	 * @param height
	 * @return A {@link Map} containig the enclosed data.
	 */
	public Map getMap(long x, long y, long width, long height) {
		if (x < 0 || y < 0 || width < 0 || height < 0) {
			throw new IllegalArgumentException("X, Y, width and height must be positive.");
		}

		final MapBuilder builder = new Map.MapBuilder();
		
		final List<MapDataDTO> data = getCoveredMapDataDTO(x, y, width, height);
		
		/*
		data.sort((MapDataDTO d1, MapDataDTO d2) -> {
			d1.getRect().getX() < d2.getRect().getX() 
		});*/

		throw new IllegalStateException("Not yet implemented.");
	}

	/**
	 * Alias for {@link #getMap(long, long, long, long)}.
	 * 
	 * @param bbox
	 *            The bounding box for retrieving the map data.
	 */
	public Map getMap(Rect bbox) {
		return getMap(bbox.getX(), bbox.getY(), bbox.getWidth(), bbox.getHeight());
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
	public void saveMapData(MapDataDTO dto) {

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
		} catch (IOException e) {
			LOG.error("Can not persist map data.", e);
		}
	}

	/**
	 * Returns the name of the current area of the map if it has a special
	 * naming component attached to it.
	 * 
	 * @param pos
	 *            The position to check for a naming component.
	 * @return The name of the area.
	 */
	public String getAreaName(Point pos) {
		final Set<Entity> entities = entityService.getCollidingEntities(pos, TagComponent.class);

		Optional<String> areaName = entities.stream()
				.map(entity -> entityService.getComponent(entity, TagComponent.class))
				.map(Optional::get)
				.filter(tagComp -> tagComp.has(AREA_TAG_NAME))
				.map(tagComp -> tagComp.get(AREA_TAG_NAME, String.class).get())
				.findFirst();

		return areaName.orElse("");
	}

	/**
	 * Returns the name of the current map/world.
	 * 
	 * @return The name of the map.
	 */
	public String getMapName() {
		final MapParameter params = mapParamDao.findLatest();
		return params == null ? "" : params.getName();
	}

	/**
	 * Returns a list with all DTOs which are covering the given range.
	 * 
	 * @param x
	 *            X start of the range.
	 * @param y
	 *            Y start of the range.
	 * @param width
	 *            Width of the area.
	 * @param height
	 *            Height of the area.
	 * @return A list with all {@link MapDataDTO} covering the given area.
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
	 * A list of chunk coordinates must be given and the method will return all
	 * chunks of map data for the given coordinates.
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
			final List<Integer> groundTiles = new ArrayList<>((int) (area.getWidth() * area.getHeight()));

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
