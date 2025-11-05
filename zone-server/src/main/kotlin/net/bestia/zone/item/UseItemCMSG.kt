package net.bestia.zone.item

import net.bestia.zone.message.CMSG

data class UseItemCMSG(
    override val playerId: Long,
    val uniqueId: Long
) : CMSG {
    companion object {
        fun fromBnet(playerId: Long, bnet: net.bestia.bnet.proto.UseItemCMSGProto.UseItemCMSG): UseItemCMSG {
            return UseItemCMSG(playerId, bnet.uniqueId)
        }
    }
}
