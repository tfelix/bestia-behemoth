package net.bestia.core.game.model;

import java.util.Set;

import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.OneToMany;
import javax.persistence.OneToOne;
import javax.persistence.Transient;

// Checken ob eine Gilde nur einen Member haben kann bzw. 
//@Entity
public class Guild {
	
	@Transient
	final public int MAX_LEVEL = 100;
	
	@Id
	@GeneratedValue(strategy=GenerationType.AUTO)
	private int id;
	private String name;
	private String emblem;
	private int level;
	@OneToOne
	private GuildMember leader;
	@OneToMany(mappedBy="guild")
	private Set<GuildMember> members;

	/**
	 * @return the name
	 */
	public String getName() {
		return name;
	}

	/**
	 * @param name
	 *            the name to set
	 */
	public void setName(String name) {
		this.name = name;
	}

	/**
	 * @return the emblem
	 */
	public String getEmblem() {
		return emblem;
	}

	/**
	 * @param emblem
	 *            the emblem to set
	 */
	public void setEmblem(String emblem) {
		this.emblem = emblem;
	}

	/**
	 * @return the level
	 */
	public int getLevel() {
		return level;
	}

	/**
	 * @param level
	 *            the level to set
	 */
	public void setLevel(int level) {
		if(level > MAX_LEVEL || level < 0) {
			throw new IllegalArgumentException("Guild level must be between 0 and " + MAX_LEVEL);
		}
		this.level = level;
	}

	/**
	 * @return the leader
	 */
	public GuildMember getLeader() {
		return leader;
	}

	/**
	 * @param leader
	 *            the leader to set
	 */
	public void setLeader(GuildMember leader) {
		this.leader = leader;
	}

	/**
	 * @return the members
	 */
	public Set<GuildMember> getMembers() {
		return members;
	}

	/**
	 * @param members
	 *            the members to set
	 */
	public void setMembers(Set<GuildMember> members) {
		this.members = members;
	}
}
