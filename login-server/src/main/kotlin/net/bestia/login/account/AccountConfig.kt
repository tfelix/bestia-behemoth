package net.bestia.login.account

import net.bestia.account.Role
import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * Registration is unauthenticated, so [signUpRole] is handed to whoever signs up next, not to a
 * chosen account. Raising it above [Role.USER] is only ever safe on a host nobody else can reach,
 * which is what the `dev` profile assumes.
 */
@ConfigurationProperties(prefix = "account")
data class AccountConfig(
  val signUpRole: Role
)
