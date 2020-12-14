package net.bestia.ai.planner.goap

interface ActionProvider {
  fun getAvailableActions(entityId: Long)
}