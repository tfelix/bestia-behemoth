package net.bestia.zoneserver.entity.component

import com.fasterxml.jackson.annotation.JsonProperty

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

    @JsonProperty("v")
    val visual: SpriteInfo,

    /**
     * Gives a flag if the entity is currently visible.
     *
     * @return TRUE if the entity is visible. FALSE otherwise.
     */
    @JsonProperty("vis")
    val isVisible: Boolean = true
) : Component
