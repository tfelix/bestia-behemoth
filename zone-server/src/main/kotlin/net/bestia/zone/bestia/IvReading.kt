package net.bestia.zone.bestia

import net.bestia.bnet.proto.BestiaGeneticsProto
import net.bestia.zone.battle.status.IndividualValues
import net.bestia.zone.battle.status.IvStat

/** Individual values as one master may read them: grades for everyone, exact numbers with Bloodline Study. */
class IvReading(
  private val ivs: IndividualValues,
  private val exact: Boolean,
) {

  fun toBnet(): BestiaGeneticsProto.IvReading {
    val reading = BestiaGeneticsProto.IvReading.newBuilder()
    for (stat in IvStat.entries) {
      reading.addGradesValue(IvGrade.of(ivs[stat]).ordinal)
      if (exact) reading.addExact(ivs[stat])
    }

    return reading.build()
  }
}
