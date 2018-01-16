package net.bestia.model.domain;

import java.io.Serializable;
import java.util.Collections;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

import javax.persistence.CascadeType;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.OneToMany;
import javax.persistence.Table;
import javax.persistence.Transient;

/**
 * Holds all the players which are temporarily can form parties in order to
 * share experience for example or to chat.
 * 
 * @author Thomas Felix
 *
 */
@Entity
@Table(name = "parties")
public class Party implements Serializable {

	private static final long serialVersionUID = 1L;

	@Transient
	public static final int MAX_PARTY_MEMBER = 12;

	@Id
	@GeneratedValue(strategy = GenerationType.AUTO)
	private long id;

	@Column(nullable = false, unique = true, length = 25)
	private String name;

	@OneToMany(cascade = CascadeType.ALL, mappedBy = "party")
	private Set<PlayerBestia> members = new HashSet<>();

	/**
	 * Adds a new member to the party.
	 * 
	 * @param member
	 *            The new member to be added to the party.
	 * @return TRUE if the member could be added. FALSE if it couldnt.
	 */
	public boolean addMember(PlayerBestia member) {
		if (members.size() < MAX_PARTY_MEMBER && !members.contains(member)) {
			members.add(member);
			return true;
		} else {
			return false;
		}
	}
	
	public void removeMember(PlayerBestia removeMember) {
		members.remove(removeMember);
	}
	
	public long getId() {
		return id;
	}

	/**
	 * Returns a READ-ONLY set of the members inside this party.
	 * 
	 * @return The current member of this party.
	 */
	public Set<PlayerBestia> getMembers() {
		return Collections.unmodifiableSet(members);
	}

	/**
	 * @return The name of the party.
	 */
	public String getName() {
		return name;
	}

	/**
	 * @param name
	 *            The name of the party.
	 */
	public void setName(String name) {
		this.name = Objects.requireNonNull(name);
	}
}
