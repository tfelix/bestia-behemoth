package bestia.messages.entity

import java.io.Serializable

class ComponentInstallMessage(
        val entityId: Long,
        val componentId: Long
) : Serializable {
  override fun toString(): String {
    return "ComponentInstallMessage[eid: $entityId, cid: $componentId]"
  }
}