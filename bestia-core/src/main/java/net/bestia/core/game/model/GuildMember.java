package net.bestia.core.game.model;

import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.IdClass;
import javax.persistence.ManyToOne;
import javax.persistence.PrimaryKeyJoinColumn;

/**
 * Describes a member of a guild. This needs to be an extra entity because we
 * save additional information about each guild member like tax values, given
 * exp and some guild specific stuff.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 * 
 */
@Entity
@IdClass(GuildMemberId.class)
public class GuildMember {

	@Id
	private int guildId;

	@Id
	private int accountId;
	@ManyToOne
	@PrimaryKeyJoinColumn(name = "ACCOUNTID", referencedColumnName = "ID")
	private Account account;
	@ManyToOne
	@PrimaryKeyJoinColumn(name = "GUILDID", referencedColumnName = "ID")
	private Guild guild;
	private int tax;
	private int expEarned;
	private int rank;

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
	 * @return the tax
	 */
	public int getTax() {
		return tax;
	}

	/**
	 * @param tax
	 *            the tax to set
	 */
	public void setTax(int tax) {
		this.tax = tax;
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

	/**
	 * @return the rank
	 */
	public int getRank() {
		return rank;
	}

	/**
	 * @param rank
	 *            the rank to set
	 */
	public void setRank(int rank) {
		this.rank = rank;
	}
}
