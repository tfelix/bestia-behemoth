package net.bestia.zoneserver.script

import net.bestia.model.geometry.Shape

sealed class ScriptMessage

sealed class EntityMessage : ScriptMessage() {
    abstract val entityId: Long
}

sealed class ScriptQuery : ScriptMessage()

class EntityByIdQuery(
    val entityId: Long
) : ScriptQuery()

class EntitiesByShapeQuery(
    val shape: Shape,
    val callbackContext: CallbackContext
) : ScriptQuery()