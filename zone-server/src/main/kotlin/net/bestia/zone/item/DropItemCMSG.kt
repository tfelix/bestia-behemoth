package net.bestia.zone.item

import net.bestia.zone.message.CMSG

data class DropItemCMSG(
    override val playerId: Long,
    val itemId: Long,
    val amount: Int,
    /** Which copy to drop; 0 leaves the choice to the server. See `drop_item_cmsg.proto`. */
    val uniqueId: Long = 0L
) : CMSG {
    companion object {
        fun fromBnet(playerId: Long, bnet: net.bestia.bnet.proto.DropItemCMSGProto.DropItemCMSG): DropItemCMSG {
            return DropItemCMSG(playerId, bnet.itemId, bnet.amount, bnet.uniqueId)
        }
    }
}
