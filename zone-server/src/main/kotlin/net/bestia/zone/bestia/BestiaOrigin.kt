package net.bestia.zone.bestia

import net.bestia.bnet.proto.BestiaOriginProto

/** How a player bestia came to be, read as "Caught by X", "Bred by X" or "Granted to X". */
enum class BestiaOrigin {
  CAUGHT,
  BRED,
  GRANTED;

  fun toBnet(): BestiaOriginProto.BestiaOrigin = when (this) {
    CAUGHT -> BestiaOriginProto.BestiaOrigin.BESTIA_ORIGIN_CAUGHT
    BRED -> BestiaOriginProto.BestiaOrigin.BESTIA_ORIGIN_BRED
    GRANTED -> BestiaOriginProto.BestiaOrigin.BESTIA_ORIGIN_GRANTED
  }
}
