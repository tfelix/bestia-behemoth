package net.bestia.zone.stronghold

import net.bestia.bnet.proto.EnvelopeProto
import net.bestia.bnet.proto.StrongholdSmsgProto
import net.bestia.zone.message.SMSG

/** Opens, or refreshes, the Stronghold window of a member at their guild's Empyrite. */
data class StrongholdSMSG(
  val empyriteEntityId: Long,
  val seedName: String,
  val guildName: String,
  val hp: Int,
  val maxHp: Int,
  val tracks: List<Track>,
  val hasPermanentDevelopment: Boolean,
  val hpPerDefense: Int,
  val defPerDefense: Int,
  val steward: Steward,
) : SMSG {

  data class Track(val track: StrongholdTrack, val progress: TrackProgress, val nextStepCoins: Long)

  data class Steward(val hasContract: Boolean, val isHired: Boolean, val coins: Long)

  override fun toBnetEnvelope(): EnvelopeProto.Envelope {
    val proto = StrongholdSmsgProto.StrongholdSMSG.newBuilder()
      .setEmpyriteEntityId(empyriteEntityId)
      .setSeedName(seedName)
      .setGuildName(guildName)
      .setHp(hp)
      .setMaxHp(maxHp)
      .setHasPermanentDevelopment(hasPermanentDevelopment)
      .setMaxLevel(TrackProgress.MAX_LEVEL)
      .setStepsPerLevel(TrackProgress.STEPS_PER_LEVEL)
      .setHpPerDefense(hpPerDefense)
      .setDefPerDefense(defPerDefense)
      .setHasStewardContract(steward.hasContract)
      .setHasSteward(steward.isHired)
      .setStewardCoins(steward.coins)

    for (track in tracks) {
      proto.addTracks(
        StrongholdSmsgProto.StrongholdTrackInfo.newBuilder()
          .setTrack(StrongholdSmsgProto.StrongholdTrack.forNumber(track.track.ordinal))
          .setLevel(track.progress.level)
          .setSteps(track.progress.steps)
          .setNextStepCoins(track.nextStepCoins)
      )
    }

    return EnvelopeProto.Envelope.newBuilder().setStronghold(proto).build()
  }
}
