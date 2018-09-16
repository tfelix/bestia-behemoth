package net.bestia.messages.entity

sealed class ComponentState

data class ComponentIntall(
        val componentId: Long
) : ComponentState()

data class ComponentRemove(
        val componentId: Long
) : ComponentState()