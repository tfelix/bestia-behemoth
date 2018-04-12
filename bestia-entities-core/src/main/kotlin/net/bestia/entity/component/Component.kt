package net.bestia.entity.component

import com.fasterxml.jackson.annotation.JsonIgnore
import java.io.Serializable

/**
 * Each component needs a unique ID because this is required by the way
 * components are saved into the DHT of hazelcast. This is must be unique in the
 * whole system.
 *
 * Components must have a ctor which accepts only an long id value.
 *
 * @author Thomas Felix
 */
abstract class Component(
        /**
         * The unqiue id of the component.
         *
         * @return The unique component id.
         */
        val id: Long,

        /**
         * @return The entity id to which this component is attached.
         */
        @get:JsonIgnore
        var entityId: Long = 0
) : Serializable {

  /**
   * This method is called to restore the component state as if it had
   * been initialized. Not all components might to override this but
   * very complex ones which need to be cleared before reuse should do this
   * in this method.
   */
  open fun clear() {
    // no op.
  }
}
