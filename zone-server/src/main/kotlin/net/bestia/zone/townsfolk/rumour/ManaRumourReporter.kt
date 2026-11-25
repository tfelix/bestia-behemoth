package net.bestia.zone.townsfolk.rumour

import net.bestia.zone.ecs.core.World
import net.bestia.zone.world.WorldService
import net.bestia.zone.world.mana.ManaField
import net.bestia.zone.world.mana.ManaTier
import net.bestia.zone.world.mana.ManaTierListener
import net.bestia.zone.world.mana.ManaTiers
import net.bestia.zone.world.time.BestiaClock
import org.springframework.stereotype.Service

/**
 * Tells the towns nearby when the mana rises a tier: first lights at night, then restless beasts, then risen
 * stones and storms. A whole Wound can rise in one minute, so each kind is posted once per 4 km tile until the
 * news has run its course.
 */
@Service
class ManaRumourReporter(
  private val rumours: RumourService,
  private val worldService: WorldService,
  private val clock: BestiaClock,
) : ManaTierListener {

  /** The day each kind was last posted for a tile. Only rises are news, so a restart forgets nothing it needs. */
  private val postedOn = HashMap<Pair<RumourKind, Long>, Double>()

  override fun onTiersChanged(world: World, changes: List<ManaTiers.Change>) {
    val today = clock.now().absoluteDay

    changes
      .filter { it.to > it.from }
      .mapNotNull { change -> kindOf(change.to)?.let { kind -> kind to change.cell } }
      .distinctBy { (kind, cell) -> kind to ManaField.tileKeyOf(cell.x, cell.y) }
      .forEach { (kind, cell) ->
        val key = kind to ManaField.tileKeyOf(cell.x, cell.y)
        val last = postedOn[key]
        if (last != null && today - last < kind.lifetimeDays) return@forEach

        postedOn[key] = today
        val voxel = worldService.generated.config.voxelSize
        rumours.post(kind, (cell.centreX / voxel).toLong(), (cell.centreY / voxel).toLong(), STRENGTH)
      }
  }

  private fun kindOf(tier: ManaTier): RumourKind? = when (tier) {
    ManaTier.UNEASY -> RumourKind.STRANGE_LIGHTS
    ManaTier.OMINOUS -> RumourKind.RESTLESS_BEASTS
    ManaTier.STORMS -> RumourKind.STONES_RISEN
    else -> null
  }

  private companion object {
    /** Heard about half as far as the news carries at most: a sign in the hills, not a battle. */
    const val STRENGTH = 0.5
  }
}
