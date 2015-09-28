package net.bestia.zoneserver.ecs.manager;

import java.io.File;
import java.io.IOException;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.artemis.Entity;
import com.artemis.Manager;
import com.artemis.utils.Bag;
import com.fasterxml.jackson.core.JsonGenerationException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;

public class WorldPersistenceManager extends Manager {

	private final static Logger log = LogManager.getLogger(WorldPersistenceManager.class);
	private final ObjectMapper mapper = new ObjectMapper();
	private final File saveSubfolder;
	private final Bag<Entity> persistEntities = new Bag<>();

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
			throw new IllegalArgumentException("saveFilder can not be null.");
		}

		if (!saveFolder.isDirectory() || !saveFolder.canRead()) {

			throw new IllegalArgumentException(
					String.format("Path %s is not a directory or can not be read.", saveFolder.getAbsolutePath()));
		}

		this.saveSubfolder = new File(saveFolder, zoneName);

		if (!this.saveSubfolder.exists()) {
			if (!this.saveSubfolder.mkdir()) {
				throw new IllegalArgumentException("Can not create subfolder: " + saveSubfolder.getAbsolutePath());
			}
		}
	}

	public void save() throws IOException {

		for (Entity e : persistEntities) {
			// TODO Check if this entity should be persisted.
			// Dont persist player or script entities which can be regenerated
			// from database or during startup.

			final String filename = String.format("entity-%d", e.id);
			final File saveFile = new File(saveSubfolder, filename);

			try {
				mapper.writeValue(saveFile, e);
			} catch (JsonGenerationException | JsonMappingException e1) {
				log.error("Can not write entity %s to file: %s", e.toString(), saveFile.getAbsolutePath());
			}
		}
	}

	public void load() {
		// TODO Schreiben.
	}

	@Override
	public void added(int entityId) {
		persistEntities.add(world.getEntity(entityId));
	}

	@Override
	public void deleted(int entityId) {
		persistEntities.remove(world.getEntity(entityId));
	}
}
