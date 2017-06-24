package net.bestia.zoneserver.entity.component;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * The tag component allows attach simple data to the entity.
 * 
 * @author Thomas Felix
 *
 */
public class TagComponent extends Component {

	private static final long serialVersionUID = 1L;

	public static final String TAG_PERSIST = "persist";

	private final Map<String, Object> data = new HashMap<>();

	public TagComponent(long id, long entityId) {
		super(id, entityId);
		// no op.
	}

	public void clear() {
		data.clear();
	}

	public void add(String key, Object value) {
		
		// Only allow serializable values.
		if(!(value instanceof Serializable)) {
			new IllegalArgumentException("Value object must be serializable.");
		}
		
		data.put(key, value);
	}

	public boolean has(String tag) {
		return data.containsKey(tag);
	}

	@Override
	public String toString() {
		return String.format("TagComponent[%s]", data);
	}

	public <T> Optional<T> get(String key, Class<T> type) {

		if (data.containsKey(key)) {
			final Object obj = data.get(key);

			if (type.isAssignableFrom(type)) {
				return Optional.of(type.cast(obj));
			} else {
				return Optional.empty();
			}

		} else {
			return Optional.empty();
		}
	}
}
