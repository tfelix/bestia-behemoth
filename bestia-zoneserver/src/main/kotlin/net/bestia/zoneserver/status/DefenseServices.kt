package net.bestia.zoneserver.status

import net.bestia.model.bestia.BasicDefense
import net.bestia.model.bestia.Defense
import org.springframework.stereotype.Service

@Service
class DefenseServices {
  fun getDefense(): Defense {
    return BasicDefense()
  }
}