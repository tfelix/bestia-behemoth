package net.bestia.zoneserver.ecs.manager;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.artemis.Entity;
import com.artemis.Manager;
import com.fasterxml.jackson.core.JsonGenerationException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;

public class WorldPersistenceManager extends Manager {

	private final static Logger log = LogManager
			.getLogger(WorldPersistenceManager.class);
	private final ObjectMapper mapper = new ObjectMapper();
	private File saveSubfolder;
	private final Map<Integer, Entity> trackedEntities = new HashMap<>();

	/**
	 * 
	 * @param saveFolder
	 * @param zoneName
	 *            Name of the zone server. This will be the name of the
	 *            subfolder which will be created for the entities to be
	 *            persisted.
	 */
	public WorldPersistenceManager(File saveFolder, String zoneName) {
		if (saveFolder == null) {
			log.warn("SaveFolder is null. Loading and persisting of entities disabled.");
			saveSubfolder = null;
		} else {
			if (!saveFolder.isDirectory() || !saveFolder.canRead()) {
				log.warn(
						"SaveFolder {} is not a directory or can not be read. Loading and persisting of entities disabled.",
						saveFolder.getAbsolutePath());
				saveSubfolder = null;
			} else {
				createSubfolder(saveFolder, zoneName);
			}
		}
	}

	private void createSubfolder(File saveFolder, String zoneName) {
		this.saveSubfolder = new File(saveFolder, zoneName);

		if (!this.saveSubfolder.exists()) {
			if (!this.saveSubfolder.mkdir()) {
				throw new IllegalArgumentException("Can not create subfolder: "
						+ saveSubfolder.getAbsolutePath());
			}
		}
	}

	public void save() throws IOException {

		if (saveSubfolder == null) {
			return;
		}

		for (Entity e : trackedEntities.values()) {
			// TODO Check if this entity should be persisted.
			// Dont persist player or script entities which can be regenerated
			// from database or during startup.

			final String filename = String.format("entity-%d", e.getId());
			final File saveFile = new File(saveSubfolder, filename);

			try {
				mapper.writeValue(saveFile, e);
			} catch (JsonGenerationException | JsonMappingException e1) {
				log.error("Can not write entity %s to file: %s", e.toString(),
						saveFile.getAbsolutePath());
			}
		}
	}

	public void load() {

		if (saveSubfolder == null) {
			return;
		}

		// TODO Schreiben.
	}

	@Override
	public void added(Entity e) {
		trackedEntities.put(e.getId(), e);
	}

	@Override
	public void deleted(Entity e) {
		trackedEntities.remove(e.getId());
	}
}
