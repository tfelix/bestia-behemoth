package net.bestia.zone.bestia

import net.bestia.zone.BestiaException

class BestiaNotFoundException(identifier: String) : BestiaException(
  code = "BESTIA_NOT_FOUND",
  message = "Bestia $identifier was not found"
) {

  constructor(id: Long) : this("ID: $id")
  constructor(ids: Collection<Long>) : this("with IDs: $ids")
}