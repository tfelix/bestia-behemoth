package net.bestia.entity;

import java.util.Collections;
import java.util.Set;

import org.springframework.stereotype.Service;

import net.bestia.model.entity.InteractionType;
import net.bestia.entity.component.InteractionComponent;

/**
 * Service to control the interaction between entities. Usually in this
 * interaction some sorte of scripting is involved to determine the art of
 * interactions which are possible.
 * 
 * @author Thomas Felix
 *
 */
@Service
public class InteractionService {

	/**
	 * Asks the entity for all types of interactions which are possible with it
	 * by the certain entity. It might be possible that the interaction
	 * possibilities are dependent upon the invoker. Usually the questioning
	 * part is also an {@link InteractionComponent} unit.
	 * 
	 * @return A set of possible interactions.
	 */
	public Set<InteractionType> getPossibleInteractions(Entity source, Entity target) {

		// TODO implementieren.
		return Collections.emptySet();
	}

	/**
	 * Performs the requested interaction with the given interactor.
	 * 
	 * @param type
	 *            The type of requested interaction.
	 * @param interactor
	 *            The interactor which performs the interaction.
	 */
	public void triggerInteraction(InteractionType type, Entity source, Entity target) {
		// TODO implementieren.
	}
}
