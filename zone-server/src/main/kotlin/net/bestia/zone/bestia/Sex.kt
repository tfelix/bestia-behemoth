package net.bestia.zone.bestia

import net.bestia.bnet.proto.BestiaGeneticsProto
import kotlin.random.Random

/** Every bestia is one or the other, half and half; a breeding pair needs one of each. */
enum class Sex {
  MALE,
  FEMALE;

  fun toBnet(): BestiaGeneticsProto.BestiaSex {
    return when (this) {
      MALE -> BestiaGeneticsProto.BestiaSex.BESTIA_SEX_MALE
      FEMALE -> BestiaGeneticsProto.BestiaSex.BESTIA_SEX_FEMALE
    }
  }

  companion object {
    fun roll(random: Random): Sex {
      return if (random.nextBoolean()) MALE else FEMALE
    }
  }
}
