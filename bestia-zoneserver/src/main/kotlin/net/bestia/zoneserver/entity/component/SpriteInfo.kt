package net.bestia.zoneserver.entity.component

import com.fasterxml.jackson.annotation.JsonProperty
import java.io.Serializable
import javax.persistence.EnumType
import javax.persistence.Enumerated

/**
 * This class is meant to be included in different messages. The engine must be
 * informed when getting the player bestia data how to display it and also when
 * an entity gets an update.
 *
 * @author Thomas Felix
 */
data class SpriteInfo(
    @JsonProperty("s")
    val sprite: String,

    @JsonProperty("t")
    @Enumerated(EnumType.STRING)
    val type: VisualType
) : Serializable {

  companion object {
    val EMPTY_SPRITE = SpriteInfo("", VisualType.SINGLE)

    /**
     * Creates an sprite info for a item sprite which is usually only a static
     * image.
     *
     * @param image
     * Name of the static item sprite image.
     * @return A [SpriteInfo] instance describing the item.
     */
    fun item(image: String): SpriteInfo {
      return SpriteInfo(image, VisualType.ITEM)
    }

    fun mob(sprite: String): SpriteInfo {
      return SpriteInfo(sprite, VisualType.DYNAMIC)
    }
  }
}
