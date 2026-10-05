package net.bestia.login.zone

import org.springframework.stereotype.Component

/**
 * Every configured zone is a candidate for every account. Good enough while there are few zones; once zones
 * report who plays on them, a directory can name the one zone instead.
 */
@Component
class ConfiguredZoneDirectory(
  private val config: ZoneDirectoryConfig
) : ZoneDirectory {

  override fun candidatesFor(accountId: Long): List<ZoneEndpoint> {
    return config.zones
  }
}
