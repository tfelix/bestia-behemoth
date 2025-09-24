package net.bestia.login.ethereum

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Service

@ConditionalOnProperty(
  prefix = "ethereum",
  name = ["enable-nft-verification"],
  havingValue = "false"
)
@Service
class AlwaysSuccessEthereumService : EthereumService {
  override fun verifyNftOwnership(wallet: String, tokenId: Long): Boolean {
    return true
  }
}