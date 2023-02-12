package net.bestia.model.login

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import net.bestia.model.AbstractEntity
import net.bestia.model.account.Account

/**
 * Saves information for basic authentication via password and email.
 */
@Entity
@Table(name = "basic_logins")
class BasicLogin(
    @Column(length = 64, unique = true, nullable = false)
    var email: String,
    var password: String,
    @ManyToOne
    var account: Account
) : AbstractEntity()
