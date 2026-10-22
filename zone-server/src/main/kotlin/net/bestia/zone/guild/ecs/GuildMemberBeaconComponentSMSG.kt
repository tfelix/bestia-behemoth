package net.bestia.zone.guild.ecs

import net.bestia.bnet.proto.GuildMemberBeaconSMSGProto
import net.bestia.bnet.proto.StateBatchSmsgProto
import net.bestia.bnet.proto.Vec3OuterClass
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.message.EntitySMSG

data class GuildMemberBeaconComponentSMSG(
  override val entityId: Long,
  val position: Vec3L,
  val areaName: String,
  val hpCurrent: Int,
  val hpMax: Int,
  val manaCurrent: Int,
  val manaMax: Int,
) : EntitySMSG {

  override fun writeTo(update: StateBatchSmsgProto.EntityUpdate.Builder) {
    val position = Vec3OuterClass.Vec3.newBuilder()
      .setX(position.x)
      .setY(position.y)
      .setZ(position.z)
      .build()

    val beacon = GuildMemberBeaconSMSGProto.GuildMemberBeaconSMSG.newBuilder()
      .setPosition(position)
      .setAreaName(areaName)
      .setHpCurrent(hpCurrent)
      .setHpMax(hpMax)
      .setManaCurrent(manaCurrent)
      .setManaMax(manaMax)
      .build()

    update.addComponents(StateBatchSmsgProto.ComponentDelta.newBuilder().setGuildMemberBeacon(beacon))
  }
}
