package net.bestia.zone.bestiastorage

import net.bestia.bnet.proto.OperationErrorProto.OpError

/** A storage request the master is told "no" to, with [args] for the client's wording. */
class BestiaStorageRefusal(
  val code: OpError,
  val args: List<String> = emptyList(),
) : RuntimeException(code.name)
