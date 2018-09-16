package net.bestia.zoneserver.entity

import org.springframework.stereotype.Service
import java.util.*

@Service
class IdGeneratorService {

  /**
   * I am not sure if this id gen works out. Either we need to switch to maybe Snowflake lib or we
   * need to find another way of ID generation because unsigned is not available right now.
   *
   * TODO Guard against 0
   */
  fun newId(): Long {
    val uuid = UUID.randomUUID()
    return uuid.leastSignificantBits xor uuid.mostSignificantBits
  }
}