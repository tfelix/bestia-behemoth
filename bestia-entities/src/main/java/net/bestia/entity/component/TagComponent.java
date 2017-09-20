package net.bestia.entity.component;

import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;

/**
 * The tag component allows attach simple data to an entity.
 * 
 * @author Thomas Felix
 *
 */
public class TagComponent extends Component {

	private static final long serialVersionUID = 1L;
	
	public enum Tag {
		PERSIST,
		MOB,
		NPC,
		ITEM
	}

	private EnumSet<Tag> tags = EnumSet.noneOf(Tag.class);

	public TagComponent(long id) {
		super(id);
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
	public String toString() {
		return String.format("TagComponent%s", tags.toString());
	}
}
