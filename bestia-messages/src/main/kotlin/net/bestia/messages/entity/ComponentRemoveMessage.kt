package net.bestia.messages.entity

class ComponentRemoveMessage(
        val componentId: Long
) {
  override fun toString(): String {
    return "ComponentRemoveMessage[cid: $componentId]"
  }
}