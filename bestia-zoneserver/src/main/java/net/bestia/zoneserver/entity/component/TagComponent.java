package net.bestia.zoneserver.entity.component;

import java.util.HashMap;
import java.util.Map;

/**
 * The tag component allows attach simple data to the entity.
 * 
 * @author Thomas Felix
 *
 */
public class TagComponent extends Component {

	private static final long serialVersionUID = 1L;
	private final Map<String, Object> data = new HashMap<>();
	
	public TagComponent(long id, long entityId) {
		super(id, entityId);
		// no op.
	}

	public void clear() {
		data.clear();
	}
	
	public void set(String key, Object value) {
		data.put(key, value);
	}
	
	public Object get(String key) {
		return data.get(key);
	}
	
	@Override
	public String toString() {
		return String.format("TagComponent[%s]", data);
	}
}
