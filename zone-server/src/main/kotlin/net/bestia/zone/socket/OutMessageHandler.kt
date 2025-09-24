package net.bestia.zone.socket

import net.bestia.zone.message.SMSG

interface OutMessageHandler {
  fun sendMessage(playerId: Long, outMessage: SMSG)
}