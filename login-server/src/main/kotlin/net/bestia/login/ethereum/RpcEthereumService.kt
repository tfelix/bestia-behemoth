package net.bestia.login.ethereum

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Service
import org.web3j.abi.FunctionEncoder
import org.web3j.abi.FunctionReturnDecoder
import org.web3j.abi.TypeReference
import org.web3j.abi.datatypes.Address
import org.web3j.abi.datatypes.Function
import org.web3j.abi.datatypes.generated.Uint256
import org.web3j.protocol.Web3j
import org.web3j.protocol.core.DefaultBlockParameterName
import org.web3j.protocol.core.methods.request.Transaction
import org.web3j.protocol.http.HttpService
import java.math.BigInteger

@ConditionalOnProperty(
  prefix = "ethereum",
  name = ["enable-nft-verification"],
  havingValue = "true"
)
@Service
class RpcEthereumService(
  private val ethereumConfig: EthereumConfig
) : EthereumService {

  private val web3j = Web3j.build(HttpService(ethereumConfig.rpcUrl))

  /**
   * Verifies if the given wallet owns the specified NFT token.
   * Uses the ERC-721 ownerOf function to check current ownership.
   */
  override fun verifyNftOwnership(wallet: String, tokenId: Long): Boolean {
    return try {
      val owner = getTokenOwner(tokenId)
      owner?.equals(wallet, ignoreCase = true) ?: false
    } catch (e: Exception) {
      // Log the error in a real application
      println("Error verifying NFT ownership: ${e.message}")
      false
    }
  }

  private fun getTokenOwner(tokenId: Long): String? {
    // Create the ownerOf function call
    val function = Function(
      "ownerOf",
      listOf(Uint256(BigInteger.valueOf(tokenId))),
      listOf(object : TypeReference<Address>() {})
    )

    val encodedFunction = FunctionEncoder.encode(function)

    val transaction = Transaction.createEthCallTransaction(
      null,
      ethereumConfig.nftContractAddress,
      encodedFunction
    )

    val response = web3j.ethCall(transaction, DefaultBlockParameterName.LATEST).send()

    if (response.hasError()) {
      throw RuntimeException("Ethereum call failed: ${response.error.message}")
    }

    val result = response.value
    if (result == "0x") {
      return null // Token doesn't exist or no owner
    }

    val decoded = FunctionReturnDecoder.decode(result, function.outputParameters)

    return if (decoded.isNotEmpty()) {
      val address = decoded[0] as Address
      address.value
    } else {
      null
    }
  }
}