package bestia.messages.entity

import java.io.Serializable

class ComponentRemoveMessage(
        val componentId: Long
) : Serializable {
  override fun toString(): String {
    return "ComponentRemoveMessage[cid: $componentId]"
  }
}