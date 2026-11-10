package net.bestia.zone.crafting.station

import net.bestia.bnet.proto.StationStateSmsgProto

/** Why a station's queue is not being worked. Each one clears on its own once its cause is gone. */
enum class StationPause(val wire: StationStateSmsgProto.StationPause) {
  NONE(StationStateSmsgProto.StationPause.STATION_PAUSE_NONE),
  WORKER_AWAY(StationStateSmsgProto.StationPause.STATION_PAUSE_WORKER_AWAY),
  WORKER_OFFLINE(StationStateSmsgProto.StationPause.STATION_PAUSE_WORKER_OFFLINE),
  BAG_FULL(StationStateSmsgProto.StationPause.STATION_PAUSE_BAG_FULL),
  STATION_OUT(StationStateSmsgProto.StationPause.STATION_PAUSE_STATION_OUT),
  CANNOT_CRAFT(StationStateSmsgProto.StationPause.STATION_PAUSE_CANNOT_CRAFT),
  NO_FREE_CRAFTER(StationStateSmsgProto.StationPause.STATION_PAUSE_NO_FREE_CRAFTER)
}
