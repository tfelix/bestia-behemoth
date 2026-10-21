package net.bestia.zone.party

import net.bestia.bnet.proto.EnvelopeProto
import net.bestia.bnet.proto.PartyInfoSmsgProto
import net.bestia.bnet.proto.Vec3OuterClass
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.identity.ecs.PartyExpShare
import net.bestia.zone.message.SMSG
import net.bestia.zone.util.EntityId

/**
 * A party's roster as its members see it. [partyId] 0 with no members is "in no party", see [NONE].
 *
 * Plain values only: the message is serialised after the world lease it was read under has ended.
 */
data class PartyInfoSMSG(
  val partyId: Long,
  val partyName: String,
  val member: List<PartyMember>,
  val expShare: PartyExpShare,
  val ownerMasterId: Long,
) : SMSG {

  data class PartyMember(
    val accountId: Long,
    val masterId: Long,
    val masterName: String,
    val level: Int,
    val onlineData: OnlineData?
  ) {

    data class OnlineData(
      val entityId: EntityId,
      val areaName: String,
      val position: Vec3L,
      val vitals: Vitals,
      val activeBestia: ActiveBestia?,
    )
  }

  data class Vitals(
    val hpCurrent: Int,
    val hpMax: Int,
    val manaCurrent: Int,
    val manaMax: Int,
  )

  data class ActiveBestia(
    val entityId: EntityId,
    val speciesId: Long,
    val name: String,
    val level: Int,
    val vitals: Vitals,
  )

  override fun toBnetEnvelope(): EnvelopeProto.Envelope {
    val proto = PartyInfoSmsgProto.PartyInfoSMSG.newBuilder()
      .setPartyId(partyId)
      .setPartyName(partyName)
      .addAllMember(member.map { toProto(it) })
      .setExpShare(
        when (expShare) {
          PartyExpShare.EACH_TAKE -> PartyInfoSmsgProto.PartyExpShare.PARTY_EXP_SHARE_EACH_TAKE
          PartyExpShare.EVEN_SHARE -> PartyInfoSmsgProto.PartyExpShare.PARTY_EXP_SHARE_EVEN_SHARE
        }
      )
      .setOwnerMasterId(ownerMasterId)
      .build()

    return EnvelopeProto.Envelope.newBuilder()
      .setPartyInfo(proto)
      .build()
  }

  private fun toProto(partyMember: PartyMember): PartyInfoSmsgProto.PartyMember {
    val builder = PartyInfoSmsgProto.PartyMember.newBuilder()
      .setMasterName(partyMember.masterName)
      .setAccountId(partyMember.accountId)
      .setMasterId(partyMember.masterId)
      .setLevel(partyMember.level)

    val onlineData = partyMember.onlineData ?: return builder.build()

    val position = Vec3OuterClass.Vec3.newBuilder()
      .setX(onlineData.position.x)
      .setY(onlineData.position.y)
      .setZ(onlineData.position.z)
      .build()

    val online = PartyInfoSmsgProto.PartyMemberOnlineData.newBuilder()
      .setEntityId(onlineData.entityId)
      .setAreaName(onlineData.areaName)
      .setPosition(position)
      .setHpCurrent(onlineData.vitals.hpCurrent)
      .setHpMax(onlineData.vitals.hpMax)
      .setManaCurrent(onlineData.vitals.manaCurrent)
      .setManaMax(onlineData.vitals.manaMax)

    onlineData.activeBestia?.let { bestia ->
      online.setActiveBestia(
        PartyInfoSmsgProto.PartyMemberBestia.newBuilder()
          .setEntityId(bestia.entityId)
          .setSpeciesId(bestia.speciesId)
          .setName(bestia.name)
          .setLevel(bestia.level)
          .setHpCurrent(bestia.vitals.hpCurrent)
          .setHpMax(bestia.vitals.hpMax)
          .setManaCurrent(bestia.vitals.manaCurrent)
          .setManaMax(bestia.vitals.manaMax)
      )
    }

    return builder.setOnlineData(online).build()
  }

  companion object {
    /** The answer for an account in no party. */
    val NONE = PartyInfoSMSG(
      partyId = 0,
      partyName = "",
      member = emptyList(),
      expShare = PartyExpShare.EVEN_SHARE,
      ownerMasterId = 0
    )
  }
}
