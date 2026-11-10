package net.bestia.zone.breeding

import net.bestia.bnet.proto.BestiaEggProto
import net.bestia.zone.battle.status.IndividualValues
import net.bestia.zone.bestia.IvReading
import net.bestia.zone.bestia.Sex
import net.bestia.zone.breeding.persistence.BestiaEgg

/** An egg as the tick keeps it, enough to show it without reading its row. */
data class EggSummary(
  val instanceId: Long,
  val speciesId: Long,
  val sex: Sex,
  val ivs: IndividualValues,
  val hatchMillis: Long,
  val hatchedMillis: Long,
) {

  fun toBnet(readsExactIvs: Boolean, hatchedMillisNow: Long = hatchedMillis): BestiaEggProto.BestiaEgg {
    return BestiaEggProto.BestiaEgg.newBuilder()
      .setUniqueId(instanceId)
      .setMobId(speciesId.toInt())
      .setSex(sex.toBnet())
      .setIvs(IvReading(ivs, readsExactIvs).toBnet())
      .setHatchTotalMs(hatchMillis)
      .setHatchDoneMs(hatchedMillisNow.coerceAtMost(hatchMillis))
      .build()
  }

  companion object {
    fun of(egg: BestiaEgg): EggSummary {
      return EggSummary(egg.itemInstanceId, egg.speciesId, egg.sex, egg.individualValues, egg.hatchMillis, egg.hatchedMillis)
    }
  }
}
