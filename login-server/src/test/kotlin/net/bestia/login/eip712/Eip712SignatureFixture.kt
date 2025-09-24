package net.bestia.login.eip712

import org.web3j.crypto.Credentials
import org.web3j.crypto.Hash
import org.web3j.crypto.Sign
import org.web3j.utils.Numeric
import java.math.BigInteger

class Eip712SignatureFixture {

  // Generate a test private key
  private val privateKey = "0x4c0883a69102937d6231471b5dbb6204fe5129617082792ae468d01a3f362318"
  private val credentials = Credentials.create(privateKey)

  val address = credentials.address!!

  /**
   * Generates a valid EIP-712 signature for testing purposes.
   * Returns a pair of (signature, address)
   */
  fun generateValidSignature(
    payload: Eip712Verifier.LoginPayload
  ): String {
    // Hash domain
    val domainTypeHash =
      Hash.sha3("EIP712Domain(string name,string version,uint256 chainId,address verifyingContract)".toByteArray())
    val nameHash = Hash.sha3("Bestia Login".toByteArray())
    val versionHash = Hash.sha3("1".toByteArray())
    val chainIdBytes = Numeric.toBytesPadded(BigInteger.valueOf(1), 32)
    val verifyingContractBytes = Numeric.hexStringToByteArray("0x0".padStart(42, '0'))
    val domainSeparator = Hash.sha3(domainTypeHash + nameHash + versionHash + chainIdBytes + verifyingContractBytes)

    // Hash struct
    val typeHash = Hash.sha3("Login(address wallet,uint256 tokenIndex)".toByteArray())
    val walletBytes = Numeric.hexStringToByteArray(payload.wallet.padStart(42, '0'))
    val tokenIndexBytes = Numeric.toBytesPadded(BigInteger.valueOf(payload.tokenIndex), 32)
    val structHash = Hash.sha3(typeHash + walletBytes + tokenIndexBytes)

    // Create final EIP-712 hash
    val prefix = byteArrayOf(0x19, 0x01)
    val messageHash = Hash.sha3(prefix + domainSeparator + structHash)

    // Sign the hash
    val signatureData = Sign.signMessage(messageHash, credentials.ecKeyPair)

    // Convert to hex string (r + s + v format)
    val signature = Numeric.toHexString(signatureData.r) +
            Numeric.toHexString(signatureData.s).substring(2) +
            Numeric.toHexString(byteArrayOf(signatureData.v[0])).substring(2)

    return signature
  }
}