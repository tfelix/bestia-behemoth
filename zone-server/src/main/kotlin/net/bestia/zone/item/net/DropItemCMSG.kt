package net.bestia.zone.item.net

import net.bestia.zone.message.CMSG

data class DropItemCMSG(
    override val playerId: Long,
    val playerItemId: Long,
    val amount: Int,
) : CMSG {
    companion object {
        fun fromBnet(playerId: Long, bnet: net.bestia.bnet.proto.DropItemCMSGProto.DropItemCMSG): DropItemCMSG {
            return DropItemCMSG(playerId, bnet.playerItemId, bnet.amount)
        }
    }
}
