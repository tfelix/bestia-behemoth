package net.bestia.zone.boot

import net.bestia.zone.stronghold.EmpyriteSeedRegistry
import net.bestia.zone.stronghold.EmpyriteSeedWaking
import org.springframework.boot.CommandLineRunner
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component

/** Restores the awake Empyrite Seeds and wakes more if guilds were approved. After the towns (`@Order(113)`), which skip a seed. */
@Component
@Order(114)
class StrongholdBootRunner(
  private val seeds: EmpyriteSeedRegistry,
  private val waking: EmpyriteSeedWaking,
) : CommandLineRunner {

  override fun run(vararg args: String?) {
    seeds.restoreAll()
    waking.wakeAtBoot()
  }
}
