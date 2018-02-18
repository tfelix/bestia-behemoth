package net.bestia.messages.entity

class ComponentInstallMessage(
        val entityId: Long,
        val componentId: Long
) {
  override fun toString(): String {
    return "ComponentInstallMessage[eid: $entityId, cid: $componentId]"
  }
}