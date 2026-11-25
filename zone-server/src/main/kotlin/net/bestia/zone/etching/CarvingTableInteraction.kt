package net.bestia.zone.etching

import net.bestia.bnet.proto.OperationErrorProto.OpError
import net.bestia.zone.bestiastorage.BestiaStorageService
import net.bestia.zone.crafting.CraftingService
import net.bestia.zone.ecs.core.World
import net.bestia.zone.entity.StaticEntityKind
import net.bestia.zone.identity.ecs.Account
import net.bestia.zone.movement.ecs.Position
import net.bestia.zone.prop.PlayerStructureService
import net.bestia.zone.prop.PropInteraction
import net.bestia.zone.entity.ecs.PlayerStructureIdentity
import net.bestia.zone.script.ScriptArgKeys
import net.bestia.zone.script.ScriptArgs
import net.bestia.zone.session.ConnectionInfoService
import net.bestia.zone.util.EntityId
import org.springframework.stereotype.Component

/**
 * A click on a Carving Table opens its window. The window's buttons click it again with an [ScriptArgKeys.ACTION]:
 * lock one of your Bestias into it for Runic Etching, or take one back out.
 */
@Component
class CarvingTableInteraction(
  private val crafting: CraftingService,
  private val storage: BestiaStorageService,
  private val connections: ConnectionInfoService,
) : PropInteraction {

  override val kind = StaticEntityKind.CARVING_TABLE

  override fun interact(world: World, actorId: EntityId, propId: EntityId): OpError? {
    return interact(world, actorId, propId, ScriptArgs.of())
  }

  override fun interact(world: World, actorId: EntityId, propId: EntityId, args: ScriptArgs): OpError? {
    val structureId = world.get(propId, PlayerStructureIdentity::class)?.structureId ?: return null
    val accountId = world.get(actorId, Account::class)?.accountId ?: return null
    val action = args.text(ScriptArgKeys.ACTION)
    if (action != null && !isAtTable(world, actorId, propId)) return OpError.CRAFT_BESTIA_NOT_AT_TABLE

    val playerBestiaId = args.long(ScriptArgKeys.PLAYER_BESTIA_ID)
    when (action) {
      DEPOSIT -> storage.deposit(accountId, playerBestiaId ?: return null, structureId)
      TAKE_BACK -> storage.takeBack(accountId, playerBestiaId ?: return null, structureId)
    }

    // After the storage change, which runs on the same master's job key, so the window shows its result.
    crafting.openAtStructure(world, actorId, connections.getSelectedMasterEntityId(accountId), structureId)

    return null
  }

  private fun isAtTable(world: World, actorId: EntityId, tableId: EntityId): Boolean {
    val actorAt = world.get(actorId, Position::class)?.toVec3L() ?: return false
    val tableAt = world.get(tableId, Position::class)?.toVec3L() ?: return false

    return actorAt.distance(tableAt) <= PlayerStructureService.RANGE_TILES
  }

  companion object {
    const val DEPOSIT = "depositBestia"
    const val TAKE_BACK = "takeBackBestia"
  }
}
