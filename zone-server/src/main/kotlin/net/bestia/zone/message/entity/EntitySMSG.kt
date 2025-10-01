package net.bestia.zone.message.entity

import net.bestia.zone.message.SMSG

interface EntitySMSG: SMSG {
  val entityId: Long
}