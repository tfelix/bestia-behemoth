package net.bestia.model.domain;

import java.io.Serializable;


public class GuildMemberId implements Serializable {
	
	private static final long serialVersionUID = 1L;
	private int accountId;
	private int guildId;
	
	public int getGuildId() {
		return guildId;
	}
	
	public void setGuildId(int guildId) {
		this.guildId = guildId;
	}
	
	public int getAccountId() {
		return accountId;
	}
	
	public void setAccountId(int accountId) {
		this.accountId = accountId;
	}
}
