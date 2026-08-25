package net.bestia.zone.world.stream

import net.bestia.bnet.proto.SurfacePatchProto
import net.bestia.worldgen.lod.PatchGrid
import net.bestia.worldgen.lod.PatchPos

/**
 * The one place a [PatchPos] crosses the wire, mirroring what [ChunkCoords] does for a chunk address.
 *
 * [fromProto] is the untrusted direction and clamps the level rather than throwing: a client is free to send
 * a level this build has never heard of, and refusing the whole message over one bad field would turn a
 * version skew into a disconnect. An out-of-range level simply names no patch this server will announce, so
 * the authorisation gate drops it a moment later anyway.
 */
object SurfacePatchCoords {

  fun toProto(pos: PatchPos): SurfacePatchProto.PatchPos = SurfacePatchProto.PatchPos.newBuilder()
    .setLevel(pos.level)
    .setX(pos.x)
    .setY(pos.y)
    .build()

  fun fromProto(pos: SurfacePatchProto.PatchPos) = PatchPos(
    level = pos.level.coerceIn(0, PatchGrid.MAX_LEVEL),
    x = pos.x,
    y = pos.y
  )
}
