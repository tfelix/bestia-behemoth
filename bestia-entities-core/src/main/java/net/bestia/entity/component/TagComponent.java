package net.bestia.entity.component;

import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Objects;

/**
 * The tag component allows attach simple data to an entity.
 * 
 * @author Thomas Felix
 *
 */
public class TagComponent extends Component {

	private static final long serialVersionUID = 1L;

	public enum Tag {

		/**
		 * Entity should be persisted by the system in case of a server
		 * crash/stop.
		 */
		PERSIST,

		/**
		 * This tagged entity is a usual bestia mob.
		 */
		MOB,

		/**
		 * This tagged entity is a usual bestia NPC.
		 */
		NPC,

		/**
		 * Items lying on the ground which can be picked up by the player.
		 */
		ITEM,

		/**
		 * A natural resource which can be harvested if the needed skills are
		 * learned by the player.
		 */
		RESOURCE,

		/**
		 * Entity is under the control of a player.
		 */
		PLAYER
	}

	private EnumSet<Tag> tags = EnumSet.noneOf(Tag.class);

	public TagComponent(long id) {
		super(id, 0);
		// no op.
	}

	public void clear() {
		tags.clear();
	}

	public void add(Tag t) {
		tags.add(t);
	}

	public boolean has(Tag t) {
		return tags.contains(t);
	}

	public Collection<Tag> getAllTags() {
		return Collections.unmodifiableSet(tags);
	}

	@Override
	public int hashCode() {
		return Objects.hash(tags);
	}

	@Override
	public boolean equals(Object obj) {
		if (obj == null) {
			return false;
		}
		if (getClass() != obj.getClass()) {
			return false;
		}

		final TagComponent other = (TagComponent) obj;
		return Objects.equals(this.tags, other.tags);
	}

	@Override
	public String toString() {
		return String.format("TagComponent[%s]", tags.toString());
	}
}
