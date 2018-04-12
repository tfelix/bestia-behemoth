package net.bestia.messages

/**
 * Only an empty class to notify actor that their managed component
 * has changed.
 */
sealed class ComponentChangedMessage {
  abstract val componentId: Long
}

data class ComponentDeletedMessage(
        override val componentId: Long
) : ComponentChangedMessage()

data class ComponentUpdateMessage(
        override val componentId: Long
) : ComponentChangedMessage()

data class ComponentCreatedMessage(
        override val componentId: Long
) : ComponentChangedMessage()