package net.bestia.memoryserver.persistance

import net.bestia.entity.component.Component
import net.bestia.model.dao.ComponentDataDAO
import net.bestia.model.dao.findOneOrThrow
import net.bestia.model.domain.ComponentData
import net.bestia.util.ObjectSerializer
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.util.*

@Service
class ComponentPersistService(
        private val componentDao: ComponentDataDAO
) {
  private val serializer = ObjectSerializer<Component>()

  /**
   * Permanently deletes the component with the given id.
   *
   */
  fun delete(id: Long) {
    componentDao.deleteById(id)
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

    val data = componentDao.findOneOrThrow(id)

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
