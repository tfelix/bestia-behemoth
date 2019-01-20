package net.bestia.model.map

import java.io.Serializable
import com.fasterxml.jackson.annotation.JsonProperty

data class TileProperties(
    @JsonProperty("w")
    val isWalkable: Boolean,
    @JsonProperty("s")
    val walkspeed: WalkspeedInt,
    val blocksSight: Boolean
) : Serializable {

  init {
    if (walkspeed < 0) {
      throw IllegalArgumentException("Walkspeed must be 0 or positive.")
    }
  }
}
