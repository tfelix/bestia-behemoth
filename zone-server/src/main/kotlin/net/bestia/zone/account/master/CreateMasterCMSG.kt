package net.bestia.zone.account.master

import net.bestia.bnet.proto.CreateMasterProto
import net.bestia.bnet.proto.MasterProto
import net.bestia.zone.account.master.status.StatusAttribute
import net.bestia.zone.message.CMSG
import net.bestia.zone.util.AccountId
import java.awt.Color

data class CreateMasterCMSG(
  override val playerId: Long,
  val name: String,
  val hairColor: Color,
  val skinColor: Color,
  val hair: Hairstyle,
  val face: Face,
  val body: BodyType,
  /**
   * Id of the chosen [net.bestia.zone.world.MasterSpawnPoint]. Required - a client that leaves the
   * presence-less `uint32` unset sends 0, which matches no spawn point and gets the request refused with
   * [MasterErrorSMSG.MasterErrorCode.INVALID_SPAWN_POINT] like any other unknown id.
   */
  val spawnPointId: Int,
  /**
   * The starting effort value per attribute as picked on the creation screen. Validated by
   * [MasterFactory] against the creation budget - a distribution that does not spend it exactly is
   * refused, including the all-zero one a client that leaves the message unset sends.
   */
  val effortValues: Map<StatusAttribute, Int>
) : CMSG {
  companion object {
    fun fromBnet(accountId: AccountId, msg: CreateMasterProto.CreateMasterCMSG): CreateMasterCMSG {
      return CreateMasterCMSG(
        playerId = accountId,
        name = msg.name,
        hairColor = mapColor(msg.hairColor),
        skinColor = mapColor(msg.skinColor),
        hair = mapHairstyle(msg.hair),
        face = mapFace(msg.face),
        body = mapBodyType(msg.body),
        spawnPointId = msg.spawnPointId,
        effortValues = mapEffortValues(msg.effortValues)
      )
    }

    private fun mapEffortValues(effortValues: MasterProto.EffortValues): Map<StatusAttribute, Int> {
      return mapOf(
        StatusAttribute.STRENGTH to effortValues.strength,
        StatusAttribute.AGILITY to effortValues.agility,
        StatusAttribute.VITALITY to effortValues.vitality,
        StatusAttribute.INTELLIGENCE to effortValues.intelligence,
        StatusAttribute.DEXTERITY to effortValues.dexterity,
        StatusAttribute.WILLPOWER to effortValues.willpower
      )
    }

    private fun mapColor(color: MasterProto.Color): Color {
      return Color(color.r, color.g, color.b)
    }

    private fun mapBodyType(body: MasterProto.BodyType): BodyType {
      return when (body) {
        MasterProto.BodyType.BODY_M_1 -> BodyType.BODY_M_1
        MasterProto.BodyType.UNRECOGNIZED -> throw GeneralMasterException("Unrecognized enum value in create master request")
      }
    }

    private fun mapFace(face: MasterProto.Face): Face {
      return when (face) {
        MasterProto.Face.FACE_1 -> Face.FACE_1
        MasterProto.Face.UNRECOGNIZED -> throw GeneralMasterException("Unrecognized enum value in create master request")
      }
    }

    private fun mapHairstyle(hair: MasterProto.Hairstyle): Hairstyle {
      return when (hair) {
        MasterProto.Hairstyle.HAIR_1 -> Hairstyle.HAIR_1
        MasterProto.Hairstyle.UNRECOGNIZED -> throw GeneralMasterException("Unrecognized enum value in create master request")
      }
    }
  }
}
