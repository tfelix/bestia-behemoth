package net.bestia.zoneserver.entity;

import java.util.Objects;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import net.bestia.model.dao.ComponentDataDAO;
import net.bestia.model.domain.ComponentData;
import net.bestia.util.ObjectSerializer;
import net.bestia.zoneserver.entity.component.Component;

@Service
public class ComponentPersistService {

	private final ObjectSerializer<Component> serializer = new ObjectSerializer<>();
	private final ComponentDataDAO componentDao;

	@Autowired
	public ComponentPersistService(ComponentDataDAO componentDao) {

		this.componentDao = Objects.requireNonNull(componentDao);
	}

	/**
	 * Permanently deletes the component with the given id.
	 * 
	 * @param id
	 *            The ID of the persisted component to be deleted.
	 */
	public void delete(Long id) {

		componentDao.delete(id);
	}

	/**
	 * Stores the given component permanently into the system.
	 * 
	 * @param comp
	 *            The component to persist.
	 */
	public void store(Component comp) {

		Objects.requireNonNull(comp);

		final byte[] data = serializer.serialize(comp);

		final ComponentData compData = new ComponentData();
		compData.setId(comp.getId());
		compData.setData(data);
		componentDao.save(compData);

	}

	/**
	 * Loads the component with the given ID from the persisted storage. Returns
	 * null if the component could not be found or if there was a problem while
	 * deserializing it.
	 * 
	 * @param id
	 *            The ID of the component to load.
	 * @return The loaded component.
	 */
	public Component load(Long id) {

		final ComponentData data = componentDao.findOne(id);

		if (data == null) {
			return null;
		}

		final Component comp = serializer.deserialize(data.getData());
		return comp;
	}

}
