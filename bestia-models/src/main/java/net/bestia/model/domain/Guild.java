package net.bestia.model.domain;

import java.io.Serializable;
import java.util.Set;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.OneToMany;
import javax.persistence.OneToOne;
import javax.persistence.Table;
import javax.persistence.Transient;

/**
 * Representation of a guild for the bestia game.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
@Entity
@Table(name = "guilds")
public class Guild implements Serializable {

	private static final long serialVersionUID = 1L;

	@Transient
	public static final int MAX_LEVEL = 10;

	@Id
	@GeneratedValue(strategy = GenerationType.AUTO)
	private int id;

	@Column(nullable = false, unique = true, length = 40)
	private String name;

	@Column(nullable = true)
	private String emblem;

	private int level = 1;

	@OneToOne
	@JoinColumn(nullable = false, unique = true)
	private GuildMember leader;

	@OneToMany(mappedBy = "guild", fetch = FetchType.EAGER)
	private Set<GuildMember> members;

	/**
	 * Std. ctor. for Hibernate.
	 */
	public Guild() {

	}

	/**
	 * Ctor.
	 * 
	 * @param name
	 *            The name of the guild. Must be between 1 and 40 characters.
	 * @param leader
	 *            The leader of this guild.
	 */
	public Guild(String name, GuildMember leader) {
		if (name == null || name.isEmpty() || name.length() > 40) {
			throw new IllegalArgumentException("Guild name can not be null or empty or longer then 40 character.");
		}

		if (leader == null) {
			throw new IllegalArgumentException("Guildleader can not be null.");
		}

		this.name = name;
		this.leader = leader;
	}

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
		if (level > MAX_LEVEL || level < 0) {
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

	@Override
	public String toString() {
		return String.format("Guild[id: %d, name: %s, lv: %d, #member: %d]",
				id,
				name,
				level,
				members.size());
	}
}
