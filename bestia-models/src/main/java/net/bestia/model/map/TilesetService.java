package net.bestia.model.map;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;

import bestia.model.dao.TilesetDataDAO;
import bestia.model.domain.TilesetData;

@Service
public class TilesetService {

	private final static Logger LOG = LoggerFactory.getLogger(TilesetService.class);
	private final ObjectMapper mapper = new ObjectMapper();
	private final TilesetDataDAO tilesetDao;

	@Autowired
	public TilesetService(TilesetDataDAO tilesetDao) {

		this.tilesetDao = Objects.requireNonNull(tilesetDao);
	}

	/**
	 * 
	 * @param containedGid
	 * @return
	 */
	public Optional<Tileset> findTileset(int containedGid) {

		final TilesetData data = tilesetDao.findByGid(containedGid);

		if (data == null) {
			LOG.error("Tileset contaning gid {} not found.", containedGid);
			return Optional.empty();
		}

		try {

			final Tileset tileset = mapper.readValue(data.getData(), Tileset.class);
			return Optional.of(tileset);

		} catch (IOException e) {
			LOG.error("Could not deserialize the data {} for tileset {}.", data.getData(), data.getId(), e);
			return Optional.empty();
		}

	}

	public List<Tileset> findAllTilesets(Set<Integer> gids) {

		final List<Tileset> tilesets = new ArrayList<>();

		for (Integer gid : gids) {

			// Check if we are not already contained.
			if (tilesets.stream().anyMatch(ts -> ts.contains(gid))) {
				continue;
			}

			findTileset(gid).ifPresent(tilesets::add);
		}
		
		return tilesets;
	}

}
