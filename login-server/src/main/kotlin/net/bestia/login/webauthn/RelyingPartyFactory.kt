package net.bestia.login.webauthn

import com.yubico.webauthn.RelyingParty
import com.yubico.webauthn.RelyingPartyV2
import com.yubico.webauthn.data.RelyingPartyIdentity
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class RelyingPartyFactory {

  /**
   * `RelyingPartyV2` rather than `RelyingParty`: the V1 interface assumes every account has a
   * username, which this one does not - a player never types anything to log in.
   *
   * Attestation is left untrusted on purpose. Requiring a verifiable attestation statement would
   * lock out most synced passkey providers, and for a game it buys nothing: we care that the player
   * holds the private key, not which brand of hardware is holding it.
   */
  @Bean
  fun relyingParty(
    credentialRepository: BestiaCredentialRepository,
    config: WebAuthnConfig
  ): RelyingPartyV2<StoredCredential> {
    val identity = RelyingPartyIdentity.builder()
      .id(config.rpId)
      .name(config.rpName)
      .build()

    return RelyingParty.builder()
      .identity(identity)
      .credentialRepositoryV2(credentialRepository)
      .origins(config.origins.toSet())
      .allowOriginPort(config.allowOriginPort)
      .allowUntrustedAttestation(true)
      .build()
  }
}
