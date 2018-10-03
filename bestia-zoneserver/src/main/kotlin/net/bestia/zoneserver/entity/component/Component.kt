package net.bestia.zoneserver.entity.component

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
interface Component : Serializable {
  /**
   * @return The entity id to which this component is attached.
   */
  @get:JsonIgnore
  val entityId: Long
}
