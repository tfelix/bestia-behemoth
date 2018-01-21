package net.bestia.zoneserver.service;

import java.util.Collections;
import java.util.HashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import net.bestia.entity.Entity;
import net.bestia.entity.EntityService;
import net.bestia.entity.component.TagComponent;
import net.bestia.entity.component.TagComponent.Tag;
import net.bestia.model.entity.InteractionType;

/**
 * Service to control the interaction between entities. Usually in this
 * interaction some sort of scripting is involved to determine the art of
 * interactions which are possible.
 * 
 * @author Thomas Felix
 *
 */
@Service
public class InteractionService {

	private final EntityService entityService;

	/**
	 * Ctor.
	 */
	@Autowired
	public InteractionService(EntityService entityService) {

		this.entityService = Objects.requireNonNull(entityService);
	}

	/**
	 * Alias method of {@link #getPossibleInteractions(Entity, Entity)}. The
	 * entity ids are resolved beforehand.
	 * 
	 * @param sourceEntityId
	 *            The entity id of the source to start interaction.
	 * @param targetEntityId
	 *            The entity id of the target to receive interaction.
	 * @return A set of possible interactions at the current moment.
	 */
	public Set<InteractionType> getPossibleInteractions(long sourceEntityId, long targetEntityId) {
		final Entity source = entityService.getEntity(sourceEntityId);
		final Entity target = entityService.getEntity(targetEntityId);
		return getPossibleInteractions(source, target);
	}

	/**
	 * Asks the entity for all types of interactions which are possible with it
	 * by the certain entity. It might be possible that the interaction
	 * possibilities are dependent upon the invoker. Usually the questioning
	 * part is also an {@link InteractionComponent} unit.
	 * 
	 * The interaction is determined mostly by the tags of the entity on the
	 * first place. In case of living and thinking entities (like NPCs) the NPC
	 * itself is questioned via a script routine to refine the interaction
	 * possibilities on a per player basis.
	 * 
	 * But also items are able to control via a script if the given player
	 * should be able to interact with them. To determine this a script call is
	 * made. See the {@link InteractionScriptEnv} for further information.
	 * 
	 * @return A set of possible interactions.
	 */
	public Set<InteractionType> getPossibleInteractions(Entity source, Entity target) {
		Objects.requireNonNull(source);
		Objects.requireNonNull(target);

		final Optional<TagComponent> optTagComp = entityService.getComponent(target, TagComponent.class);

		// An untagged entity we are not able to interact with.
		if (!optTagComp.isPresent()) {
			return Collections.emptySet();
		}

		final TagComponent tagComp = optTagComp.get();
		final Set<InteractionType> interactTypes = new HashSet<>();

		// Check if the target is an item.
		if (tagComp.has(Tag.ITEM)) {
			interactTypes.add(InteractionType.PICKABLE);
			interactTypes.add(InteractionType.ATTACKABLE);
		}

		if (tagComp.has(Tag.MOB)) {
			interactTypes.add(InteractionType.ATTACKABLE);
		}

		if (tagComp.has(Tag.NPC)) {
			interactTypes.add(InteractionType.INTERACT);
		}
		
		// FIXME The distance between the interaction requester and the entity should also be considered. Via script?

		// NPC und NPC
		// PC und NPC
		// TODO Call the script calculating the correct interactions.

		return interactTypes;
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
