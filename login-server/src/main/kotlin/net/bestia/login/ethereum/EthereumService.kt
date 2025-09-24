package net.bestia.login.ethereum

interface EthereumService {
  fun verifyNftOwnership(wallet: String, tokenId: Long): Boolean
}

