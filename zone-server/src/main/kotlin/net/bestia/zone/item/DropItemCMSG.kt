package net.bestia.zone.item

import net.bestia.zone.message.CMSG

data class DropItemCMSG(
    override val playerId: Long,
    val itemId: Long,
    val amount: Int
) : CMSG {
    companion object {
        fun fromBnet(playerId: Long, bnet: net.bestia.bnet.proto.DropItemCMSGProto.DropItemCMSG): DropItemCMSG {
            return DropItemCMSG(playerId, bnet.itemId, bnet.amount)
        }
    }
}
