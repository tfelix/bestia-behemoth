package net.bestia.zone.spellbinding.net

import net.bestia.zone.message.CMSG

data class SpellBindingsCMSG(
  override val playerId: Long,
) : CMSG
