package net.bestia.zone.world.fire

import net.bestia.zone.world.WorldService
import net.bestia.zone.world.ground.ColumnLevels
import net.bestia.zone.world.ground.GroundLayer
import net.bestia.zone.world.ground.GroundLayerSource
import org.springframework.stereotype.Service

/**
 * Presents burnt ground as a ground layer, so the client composites a scar the same way it composites wear.
 *
 * ### The scar keeps its own store and its own healing
 *
 * Only the *transport* is shared. `ScorchRegistry` still holds a `ColumnMask` and still heals by eroding it
 * inward from the edges, which is what makes a burn shrink to a shrinking core rather than fading evenly -
 * see `ColumnMask.eroded`. Expressing that as a level that ticks down would lose the shape, and the shape is
 * the thing a player reads as "this burnt a while ago".
 *
 * So a scar is a layer whose levels are only ever nothing or everything, and that costs nothing: the wire
 * form is nibbles either way, and half a kilobyte of mostly-zero deflates to little.
 */
@Service
class ScorchLayerSource(
  private val scorch: ScorchRegistry,
  private val worldService: WorldService,
) : GroundLayerSource {

  override val layer = GroundLayer.SCORCHED

  override fun nibblesAt(columnKey: Long): ByteArray? {
    // `visible`, not `mask`: the stored mask is the original burn and a healing scar is smaller than it.
    val visible = scorch.scarOf(columnKey)?.visible ?: return null
    if (visible.isEmpty) return null

    val levels = ColumnLevels(worldService.config.chunkSize)
    visible.forEachSet { x, y -> levels.add(x, y, FULLY_BURNT) }

    return levels.toNibbles()
  }

  private companion object {

    /** Saturated, so the nibble the client reads is 15: burnt ground is burnt, never a bit burnt. */
    const val FULLY_BURNT = 255
  }
}
