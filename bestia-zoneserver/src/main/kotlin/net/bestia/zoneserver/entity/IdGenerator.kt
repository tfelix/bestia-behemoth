package net.bestia.zoneserver.entity

import org.springframework.stereotype.Component
import java.util.*

@Component
class IdGenerator {

  /**
   * I am not sure if this id gen works out. Either we need to switch to maybe Snowflake lib or we
   * need to find another way of ID generation because unsigned is not available right now.
   */
  fun newId(): Long {
    val uuid = UUID.randomUUID()
    return uuid.leastSignificantBits xor uuid.mostSignificantBits
  }
}