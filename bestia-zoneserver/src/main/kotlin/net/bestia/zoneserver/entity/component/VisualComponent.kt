package net.bestia.zoneserver.entity.component

/**
 * The entity can be visualized by the engine. In order to do this some means of
 * information about the display visual art must be provided. Usually this is an
 * information about the sprite sheet to be used. Also different animations can
 * be used.
 *
 * @author Thomas Felix
 */
data class VisualComponent(
    override val entityId: Long,

    /**
     * Name of the Mesh to be used in the client.
     */
    val mesh: String,

    /**
     * Gives a flag if the entity is currently visible.
     *
     * @return TRUE if the entity is visible. FALSE otherwise.
     */
    val isVisible: Boolean = true
) : Component
