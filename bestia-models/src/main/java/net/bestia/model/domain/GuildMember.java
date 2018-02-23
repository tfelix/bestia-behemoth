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

/**
 * Describes a member of a guild. This needs to be an extra entity because we
 * save additional information about each guild member like tax values, given
 * exp and some guild specific stuff.
 * 
 * @author Thomas Felix
 * 
 */
@Entity
@Table(name = "guild_member", uniqueConstraints = { @UniqueConstraint(columnNames = { "GUILD_ID", "PLAYER_BESTIA_ID" }) })
public class GuildMember implements Serializable {

	private static final long serialVersionUID = 1L;

	@Id
	@GeneratedValue
	private int id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "PLAYER_BESTIA_ID", nullable = false)
	private PlayerBestia playerBestia;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "GUILD_ID", nullable = false)
	private Guild guild;
	
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "RANK_ID", nullable = true)
	private GuildRank rank;

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
	public GuildMember(Guild guild, PlayerBestia playerBestia) {
		if (guild == null) {
			throw new IllegalArgumentException("Guild can not be null.");
		}
		if (playerBestia == null) {
			throw new IllegalArgumentException("Account can not be null.");
		}

		this.guild = guild;
		this.playerBestia = playerBestia;
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
	public PlayerBestia getMember() {
		return playerBestia;
	}

	/**
	 * @param member
	 *            the member to set
	 */
	public void setPlayerBestia(PlayerBestia playerBestia) {
		this.playerBestia = playerBestia;
	}

	/**
	 * @return the expEarned
	 */
	public int getExpEarned() {
		return expEarned;
	}

	/**
	 * @param expEarned
	 *            The expEarned to set
	 */
	public void setExpEarned(int expEarned) {
		this.expEarned = expEarned;
	}

	/**
	 * @param expEarned
	 *            Adds the amount to the earned exp.
	 */
	public void addExpEarned(int expEarned) {
		this.expEarned += expEarned;
	}

	public GuildRank getRank() {
		return rank;
	}
}
