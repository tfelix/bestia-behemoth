package net.bestia.zoneserver.entity.component;

import java.util.HashSet;
import java.util.Set;

/**
 * The tag component allows attach simple data to the entity.
 * 
 * @author Thomas Felix
 *
 */
public class TagComponent extends Component {

	private static final long serialVersionUID = 1L;
	
	public static final String TAG_PERSIST = "persist";
	
	private final Set<String> data = new HashSet<>();
	
	public TagComponent(long id, long entityId) {
		super(id, entityId);
		// no op.
	}

	public void clear() {
		data.clear();
	}
	
	public void add(String tag) {
		data.add(tag);
	}
	
	public boolean has(String tag) {
		return data.contains(tag);
	}
	
	@Override
	public String toString() {
		return String.format("TagComponent[%s]", data);
	}
}
