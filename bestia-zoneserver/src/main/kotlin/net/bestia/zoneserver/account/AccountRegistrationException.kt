package net.bestia.zoneserver.account

import net.bestia.messages.account.AccountRegistrationError

class AccountRegistrationException(
        val reason: AccountRegistrationError
) : RuntimeException()