package net.bestia.login.eip712

import org.springframework.stereotype.Component
import org.web3j.crypto.Sign
import org.web3j.crypto.Hash
import org.web3j.crypto.Keys
import org.web3j.utils.Numeric

@Component
class Eip712Verifier(
  config: Eip712Config
) {
  data class Eip712Domain(
    val name: String,
    val version: String,
    val chainId: Long,
    val verifyingContract: String
  )

  data class LoginPayload(
    val wallet: String,
    val tokenIndex: Long
  )

  sealed class VerificationResult {
    data class Success(
      val wallet: String,
      val tokenIndex: Long
    ) : VerificationResult()

    data class Failure(
      val error: String
    ) : VerificationResult()
  }

  private val domain = Eip712Domain(
    name = config.name,
    version = config.version,
    chainId = config.chainId,
    verifyingContract = "0x0"
  )

  /**
   * Verifies an EIP-712 signature for a given Login payload and address.
   */
  fun verifySignature(
    payload: LoginPayload,
    signature: String,
  ): VerificationResult {
    val messageHash = hashEip712Message(payload)
    val sigBytes = Numeric.hexStringToByteArray(signature)
    val pubKey = try {
      Sign.signedMessageToKey(
        messageHash, Sign.SignatureData(
          sigBytes[64], // v
          sigBytes.copyOfRange(0, 32), // r
          sigBytes.copyOfRange(32, 64) // s
        )
      )
    } catch (e: Exception) {
      return VerificationResult.Failure("Signature recovery failed: ${e.message}")
    }

    val recoveredAddress = "0x" + Keys.getAddress(pubKey)
    return if (recoveredAddress.equals(payload.wallet, ignoreCase = true)) {
      VerificationResult.Success(wallet = payload.wallet, tokenIndex = payload.tokenIndex)
    } else {
      VerificationResult.Failure("Recovered address does not match")
    }
  }

  /**
   * Hashes the EIP-712 domain separator.
   */
  private fun hashDomain(): ByteArray {
    val domainTypeHash = Hash.sha3(
      "EIP712Domain(string name,string version,uint256 chainId,address verifyingContract)".toByteArray()
    )
    val nameHash = Hash.sha3(domain.name.toByteArray())
    val versionHash = Hash.sha3(domain.version.toByteArray())
    val chainIdBytes = Numeric.toBytesPadded(domain.chainId.toBigInteger(), 32)
    val verifyingContractBytes = Numeric.hexStringToByteArray(domain.verifyingContract.padStart(42, '0'))

    return Hash.sha3(
      domainTypeHash + nameHash + versionHash + chainIdBytes + verifyingContractBytes
    )
  }

  /**
   * Hashes the Login payload struct.
   */
  private fun hashLoginPayload(payload: LoginPayload): ByteArray {
    val typeHash = Hash.sha3("Login(address wallet,uint256 tokenIndex)".toByteArray())
    val walletBytes = Numeric.hexStringToByteArray(payload.wallet.padStart(42, '0'))
    val tokenIndexBytes = Numeric.toBytesPadded(payload.tokenIndex.toBigInteger(), 32)

    return Hash.sha3(typeHash + walletBytes + tokenIndexBytes)
  }

  /**
   * Hashes the full EIP-712 message.
   */
  private fun hashEip712Message(payload: LoginPayload): ByteArray {
    val domainSeparator = hashDomain()
    val structHash = hashLoginPayload(payload)
    val prefix = byteArrayOf(0x19, 0x01)

    return Hash.sha3(prefix + domainSeparator + structHash)
  }
}
