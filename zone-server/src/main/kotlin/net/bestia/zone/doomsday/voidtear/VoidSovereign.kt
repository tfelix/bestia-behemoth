package net.bestia.zone.doomsday.voidtear

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.zone.bestia.BestiaCatalogue
import net.bestia.zone.doomsday.DoomAvatarFall
import net.bestia.zone.doomsday.DoomFate
import net.bestia.zone.doomsday.persistence.StoredVoidTear
import net.bestia.zone.ecs.core.WorldView
import net.bestia.zone.entity.ecs.EventCreature
import net.bestia.zone.spawn.BestiaEntitySpawner
import net.bestia.zone.worldevent.EventGround
import net.bestia.zone.worldevent.persistence.StoredRift
import org.springframework.stereotype.Component

/**
 * The Void Sovereign: the anchor of the Void Tear, and its Avatar in the Last Stand. While it stands, the Tear cannot
 * be sealed. Each one that falls holds the end off a little, and it rises again soon after.
 */
@Component
class VoidSovereign(
  private val config: VoidTearConfig,
  private val world: WorldView,
  private val catalogue: BestiaCatalogue,
  private val spawner: BestiaEntitySpawner,
  private val eventGround: EventGround,
) {

  /** Makes [tear] call the Sovereign instead of a Rift Warden. */
  fun anchor(tear: StoredRift) {
    tear.anchorSpecies = SPECIES
  }

  /**
   * Calls a new Sovereign once the last one's seal window passed unused, rather than one stage later as a rift does:
   * the Tear stays at its last stage for long.
   */
  fun rise(tear: StoredRift) {
    if (!tear.anchorSlain && tear.anchorEntityId == null && tear.nextAnchorStage > tear.stage) {
      tear.nextAnchorStage = maxOf(tear.stage, FIRST_STAGE)
    }
  }

  /** How far a fallen Sovereign holds the end off: once for each one, while its seal window stays open. */
  fun fallen(row: StoredVoidTear, tear: StoredRift): Double {
    if (!tear.anchorSlain) {
      row.sovereignFallCounted = false
      return 0.0
    }
    if (row.sovereignFallCounted) {
      return 0.0
    }
    row.sovereignFallCounted = true
    LOG.info { "The Void Sovereign fell. The Tear can be sealed for a while" }
    return config.sovereignPush
  }

  /** Raises the Sovereign at full strength at the Tear, for the Last Stand. */
  fun raiseAvatar(fate: DoomFate) {
    val at = eventGround.grounded(fate.originX, fate.originY) ?: return
    val species = catalogue.byIdentifier(SPECIES)
    world.post {
      val avatar = spawner.spawnMob(this, species.id, at, persistent = false, homePosition = at, level = config.avatarLevel)
      add(avatar, EventCreature(DoomAvatarFall.EVENT, 0))
    }
    LOG.warn { "The Void Sovereign rose at full strength for the Last Stand" }
  }

  private companion object {
    private val LOG = KotlinLogging.logger { }

    const val SPECIES = "void_sovereign"

    /** A rift calls its first anchor at this stage. */
    const val FIRST_STAGE = 3
  }
}
