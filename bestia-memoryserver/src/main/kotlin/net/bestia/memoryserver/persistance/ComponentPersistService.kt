package net.bestia.memoryserver.persistance

import java.util.Objects

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

import bestia.model.dao.ComponentDataDAO
import bestia.model.domain.ComponentData
import bestia.util.ObjectSerializer
import net.bestia.entity.component.Component

@Service
class ComponentPersistService(
        private val componentDao: ComponentDataDAO
) {
  private val serializer = ObjectSerializer<Component>()

  /**
   * Permanently deletes the component with the given id.
   *
   * @param id
   * The ID of the persisted component to be deleted.
   */
  fun delete(id: Long) {
    componentDao.delete(id)
  }

  /**
   * Stores the given component permanently into the system.
   *
   * @param comp
   * The component to persist.
   */
  fun store(comp: Component) {

    LOG.trace("Storing component {}.", comp)

    Objects.requireNonNull(comp)

    val data = serializer.serialize(comp)

    val compData = ComponentData()
    compData.id = comp.id
    compData.data = data
    componentDao.save(compData)

  }

  /**
   * Loads the component with the given ID from the persisted storage. Returns
   * null if the component could not be found or if there was a problem while
   * deserializing it.
   *
   * @param id
   * The ID of the component to load.
   * @return The loaded component.
   */
  fun load(id: Long): Component? {

    val data = componentDao.findOne(id)

    if (data == null) {
      LOG.debug("Did not find component {} inside database. Returning null.", id)
      return null
    }
    return serializer.deserialize(data.data)
  }

  companion object {
    private val LOG = LoggerFactory.getLogger(ComponentPersistService::class.java)
  }
}
