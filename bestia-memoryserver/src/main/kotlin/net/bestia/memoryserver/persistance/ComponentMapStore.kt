package net.bestia.memoryserver.persistance

import java.util.HashMap

import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Lazy

import com.hazelcast.core.MapStore

import net.bestia.entity.component.Component

/**
 * Specialized class which enables the components to be persistedly stored into
 * a persistent database. This is needed if the bestia server goes down or is
 * shut down so the world keeps persisted and can be reloaded.
 *
 * @author Thomas Felix
 */
@org.springframework.stereotype.Component
class ComponentMapStore(
        @Lazy private val persistService: ComponentPersistService
) : MapStore<Long, Component> {

  @Synchronized
  override fun load(id: Long): Component? {
    LOG.trace("Loading component: {}.", id)
    return persistService.load(id)
  }

  @Synchronized
  override fun loadAll(ids: Collection<Long>): Map<Long, Component> {

    val components = HashMap<Long, Component>()
    ids.forEach { id -> persistService.load(id)?.let { components[id] = it } }
    return components
  }

  @Synchronized
  override fun loadAllKeys(): Iterable<Long>? {
    // All loading not supported at the moment since we can not easily
    // iterate over the SQL keys. Maybe implement later when there is more
    // time.
    return null
  }

  @Synchronized
  override fun delete(id: Long) {

    LOG.trace("Deleting component: {}.", id)

    persistService.delete(id)
  }

  @Synchronized
  override fun deleteAll(ids: Collection<Long>) {

    LOG.trace("Deleting all {} components.", ids.size)

    for (id in ids) {
      persistService.delete(id)
    }
  }

  @Synchronized
  override fun store(id: Long?, comp: Component) {

    LOG.trace("Store component: {}", comp)

    persistService.store(comp)
  }

  @Synchronized
  override fun storeAll(components: Map<Long, Component>) {

    LOG.trace("Store all {} components.", components.size)

    for (comp in components.values) {
      persistService.store(comp)
    }
  }

  companion object {
    private val LOG = LoggerFactory.getLogger(ComponentMapStore::class.java)
  }
}
