package net.bestia.entity.component;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import net.bestia.model.entity.InteractionType;

/**
 * Entities which implement this interface have some kind of intractable
 * behavior. This usually can be different types of interactions. A NPC might be
 * able to talk while other entities might be able to get clicked.
 * 
 * @author Thomas Felix
 *
 */
public class InteractionComponent extends Component {

	private static final long serialVersionUID = 1L;

	private Set<InteractionType> interactions = new HashSet<>();

	public InteractionComponent(long id, long entityId) {
		super(id, entityId);
		// no op.
	}

	/**
	 * Returns all possible interaction supported by this entity. The returned
	 * set can not be modified.
	 * 
	 * @return A set of all possible interactions.
	 */
	public Set<InteractionType> getInteractions() {
		return Collections.unmodifiableSet(interactions);
	}

	/**
	 * Sets the interaction which are supported by this kind of entity.
	 * 
	 * @param interactions
	 *            The set of interactions supported by this entity.
	 */
	public void setInteractions(Set<InteractionType> interactions) {
		this.interactions.clear();
		this.interactions.addAll(interactions);
	}
}
