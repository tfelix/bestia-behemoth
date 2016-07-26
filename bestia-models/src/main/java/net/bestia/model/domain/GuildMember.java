package net.bestia.model.domain;

import java.io.Serializable;

import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.Table;
import javax.persistence.UniqueConstraint;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Describes a member of a guild. This needs to be an extra entity because we
 * save additional information about each guild member like tax values, given
 * exp and some guild specific stuff.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 * 
 */
@Entity
@Table(name = "guild_member", uniqueConstraints = { @UniqueConstraint(columnNames = {
		"GUILD_ID", "ACCOUNT_ID" }) })
public class GuildMember implements Serializable {

	private static final long serialVersionUID = 1L;

	@Id
	@GeneratedValue
	private int id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "ACCOUNT_ID", nullable = false)
	@JsonProperty("a")
	private Account account;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "GUILD_ID", nullable = false)
	@JsonIgnore
	private Guild guild;

	private int expEarned;

	/**
	 * Std. ctor for Hibernate.
	 */
	public GuildMember() {
		// no op.
	}

	/**
	 * Ctor.
	 */
	public GuildMember(Guild guild, Account accont) {
		if (guild == null) {
			throw new IllegalArgumentException("Guild can not be null.");
		}
		if (account == null) {
			throw new IllegalArgumentException("Account can not be null.");
		}

		this.guild = guild;
		this.account = accont;
	}

	/**
	 * @return the guild
	 */
	public Guild getGuild() {
		return guild;
	}

	/**
	 * @param guild
	 *            the guild to set
	 */
	public void setGuild(Guild guild) {
		this.guild = guild;
	}

	/**
	 * @return the member
	 */
	public Account getMember() {
		return account;
	}

	/**
	 * @param member
	 *            the member to set
	 */
	public void setMember(Account member) {
		this.account = member;
	}

	/**
	 * @return the expEarned
	 */
	public int getExpEarned() {
		return expEarned;
	}

	/**
	 * @param expEarned
	 *            the expEarned to set
	 */
	public void setExpEarned(int expEarned) {
		this.expEarned = expEarned;
	}
}
