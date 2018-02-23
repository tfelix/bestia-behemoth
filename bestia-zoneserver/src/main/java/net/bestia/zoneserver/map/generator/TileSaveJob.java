package net.bestia.zoneserver.map.generator;

import de.tfelix.bestia.worldgen.io.MapGenDAO;
import de.tfelix.bestia.worldgen.map.Map2DDiscreteCoordinate;
import de.tfelix.bestia.worldgen.map.Map2DDiscretePart;
import de.tfelix.bestia.worldgen.map.MapDataPart;
import de.tfelix.bestia.worldgen.random.NoiseVector;
import de.tfelix.bestia.worldgen.workload.Job;
import net.bestia.model.geometry.Rect;
import net.bestia.model.map.MapDataDTO;
import net.bestia.zoneserver.map.MapService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

/**
 * Generates some sample tiles for the bestia map.
 * 
 * @author Thomas Felix
 *
 */
public class TileSaveJob extends Job {

	private static final Logger LOG = LoggerFactory.getLogger(TileSaveJob.class);

	private final MapService mapService;
	private final static int DEFAULT_GID = 0;

	public TileSaveJob(MapService mapService) {

		this.mapService = Objects.requireNonNull(mapService);
	}

	@Override
	public void foreachNoiseVector(MapGenDAO dao, MapDataPart data, NoiseVector vec) {
		// no op.
	}

	@Override
	public void onFinish(MapGenDAO dao, MapDataPart data) {
		LOG.debug("Starting tile saving job.");

		final Map2DDiscretePart part = (Map2DDiscretePart) data.getMapPart();

		final Rect partRect = new Rect(part.getX(), part.getY(), part.getWidth(), part.getHeight());
		final MapDataDTO mapDataDto = new MapDataDTO(partRect);

		// Transfer the data into the DTO object.
		for (long y = part.getY(); y < part.getY() + part.getHeight(); y++) {
			for (long x = part.getX(); x < part.getX() + part.getWidth(); x++) {

				final NoiseVector noise = data.getCoordinateNoise(new Map2DDiscreteCoordinate(x, y));

				if (noise == null) {
					LOG.warn("No tile data for x: {}, y: {}. Setting default gid {}.", x, y, DEFAULT_GID);
					mapDataDto.putGroundLayer(x, y, DEFAULT_GID);
					continue;
				}

				final int gid = noise.getValueInt(MapGeneratorConstants.TILE_MAP);
				mapDataDto.putGroundLayer(x, y, gid);

			}
		}

		// Now the tiles must be saved.
		LOG.info("Mapdata {} saved to database.", data);
		mapService.saveMapData(mapDataDto);
	}

	@Override
	public void onStart() {
		LOG.debug("Starting tile saving job.");
	}

}
