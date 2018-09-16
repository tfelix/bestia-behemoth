package net.bestia.messages

import net.bestia.entity.component.Component

data class ComponentUpdate<T : Component>(
        val component: T
)