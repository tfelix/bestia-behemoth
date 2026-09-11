package net.bestia.zone.item.script

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.zone.ecs.account.Master
import net.bestia.zone.ecs.core.World
import net.bestia.zone.ecs.movement.Position
import net.bestia.zone.script.ScriptArgKeys
import net.bestia.zone.script.ScriptArgs
import net.bestia.zone.util.EntityId
import net.bestia.zone.world.prop.PlayerStructureService
import net.bestia.zone.world.prop.StaticEntityKind

/**
 * A box of parts that becomes a construction site where the player points it.
 *
 * The shape every buildable kit has, so a furnace kit is one subclass rather than one more copy of
 * [execute] - the same argument [net.bestia.zone.battle.skill.CraftingSkillStrategy] makes about the nine
 * crafting skills that differ only in which station they work at.
 *
 * ### Every refusal returns false, which is what keeps the kit in the bag
 *
 * `ItemScriptExecutionService` consumes one only on success, so a kit aimed at nothing, aimed too far away,
 * or aimed at ground that is already taken costs the player nothing. That matters more here than for a
 * potion: a kit is expensive, and the client gathers the position before sending, so the failures left are
 * the ones where the world changed in between.
 *
 * ### Only a master builds
 *
 * A structure is owned by the master who put it up - `PlayerStructure.ownerMasterId` - and there is nothing
 * to write there for a bestia, which is why one carrying a kit is refused rather than building anonymously.
 * The same rule `CraftingService` states for crafting.
 */
abstract class StructureKitScript(
  private val structures: PlayerStructureService,

  /** What this kit turns into once it is built. */
  private val kind: StaticEntityKind,

  /** How much work the finished thing takes, in seconds, before any skill or buff is applied. */
  private val buildSeconds: Float
) : ItemScript {

  override fun execute(world: World, userId: EntityId, args: ScriptArgs): Boolean {
    val at = args.vec(ScriptArgKeys.POSITION)
    if (at == null) {
      LOG.debug { "Entity $userId used a $kind kit without saying where" }
      return false
    }

    val masterId = world.get(userId, Master::class)?.masterId
    if (masterId == null) {
      LOG.debug { "Entity $userId is not a master and cannot put up a $kind" }
      return false
    }

    // Re-checked rather than trusted: the client only offers a spot within reach, so this is the case where
    // the player walked between aiming and the packet landing - and the case where they did not use a client.
    val userAt = world.get(userId, Position::class)?.toVec3L()
    if (userAt == null || userAt.distance(at) > PlayerStructureService.RANGE_TILES) {
      LOG.debug { "Entity $userId aimed a $kind kit at $at, out of reach from $userAt" }
      return false
    }

    val yaw = args.float(ScriptArgKeys.YAW) ?: 0f

    return structures.beginConstruction(world, kind, masterId, at, yaw, buildSeconds) != null
  }

  private companion object {
    val LOG = KotlinLogging.logger { }
  }
}
