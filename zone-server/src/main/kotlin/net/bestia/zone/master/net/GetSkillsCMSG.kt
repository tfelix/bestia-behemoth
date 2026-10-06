package net.bestia.zone.master.net

import net.bestia.zone.message.CMSG

data class GetSkillsCMSG(override val playerId: Long) : CMSG
