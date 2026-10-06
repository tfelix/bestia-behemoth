package net.bestia.zone.economy

import net.bestia.zone.world.WorldScopedData
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import net.bestia.zone.economy.persistence.SettlementLedgerRepository
import net.bestia.zone.economy.persistence.WorldTreasuryRepository

@Component
@Order(3)
class SettlementEconomyWipe(
  private val settlementLedgerRepository: SettlementLedgerRepository,
  private val worldTreasuryRepository: WorldTreasuryRepository,
) : WorldScopedData {

  override fun wipe() {
    // Settlement indices are dense and re-used, so a surviving ledger would not be orphaned - it would
    // be applied to a different town. The version columns would refuse it, so this is the tidy half.
    settlementLedgerRepository.deleteAll()
    // The reserve counts against those treasuries, so a survivor would be a number about a world that no
    // longer exists rather than a slightly wrong one. The version columns would refuse it either way.
    worldTreasuryRepository.deleteAll()
  }
}
