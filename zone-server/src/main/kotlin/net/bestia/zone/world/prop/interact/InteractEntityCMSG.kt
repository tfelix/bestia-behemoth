package net.bestia.zone.world.prop.interact

import net.bestia.bnet.proto.InteractEntityCMSGProto
import net.bestia.zone.message.CMSG
import net.bestia.zone.script.ScriptArgs
import net.bestia.zone.util.EntityId

/**
 * The player clicked on something in the world. What that means is decided by what the target is - see
 * [InteractEntityHandler].
 */
data class InteractEntityCMSG(
  override val playerId: Long,
  val targetEntityId: EntityId,
  val args: ScriptArgs
) : CMSG {

  companion object {
    fun fromBnet(playerId: Long, bnet: InteractEntityCMSGProto.InteractEntityCMSG): InteractEntityCMSG {
      return InteractEntityCMSG(
        playerId = playerId,
        targetEntityId = bnet.entityId,
        args = ScriptArgs.fromBnet(bnet.args)
      )
    }
  }
}
