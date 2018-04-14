package net.bestia.messages;

import com.fasterxml.jackson.annotation.JsonTypeInfo.Id;
import com.fasterxml.jackson.databind.DatabindContext;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.jsontype.impl.TypeIdResolverBase;
import com.fasterxml.jackson.databind.type.TypeFactory;
import org.reflections.Reflections;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.Set;

/**
 * Custom TypeId Resolver for message objects. Upon start it looks for all
 * messages which inherit from Message and determine their id. It stores it and
 * uses this serialize the messages.
 * 
 * @author Thomas Felix
 *
 */
public class MessageTypeIdResolver extends TypeIdResolverBase {

	private final static Logger log = LoggerFactory.getLogger(MessageTypeIdResolver.class);

	private final TypeFactory typeFactory = TypeFactory.defaultInstance();
	private static final HashMap<String, Class<? extends MessageId>> idToClass = new HashMap<>();
	private static final HashMap<Class<? extends MessageId>, String> classToId = new HashMap<>();

	static {

		// Find all classes implementing the message interface.
		final Reflections reflections = new Reflections("net.bestia.messages");
		final Set<Class<? extends MessageId>> messages = reflections.getSubTypesOf(MessageId.class);

		// Instantiate the message classes to get their message id from the
		// method and store it for later serialization and deserialization.
		for (Class<? extends MessageId> msg : messages) {

			// Avoid abstract classes.
			if (Modifier.isAbstract(msg.getModifiers())) {
				continue;
			}

			try {
			  final Field[] fields = msg.getFields();
			  log.debug(fields.toString());
				final Field messageId = msg.getDeclaredField("MESSAGE_ID");
				final String key = (String) messageId.get(null);

				idToClass.put(key, msg);
				classToId.put(msg, key);

				log.trace("Found Message.class: {} - {}", key, msg.toString());

			} catch (Exception e) {
				log.error("Could not get static MESSAGE_ID field from Message class: "+ msg.getName() +". Serialization and deserialization will fail.", e);
				System.exit(1);
			}
		}
	}

	private JavaType baseType;

	/**
	 * Finds all IDs of the messages and registers them for later
	 * identification.
	 */
	@Override
	public void init(JavaType bt) {
		super.init(bt);
		baseType = bt;

	}

	@Override
	public Id getMechanism() {
		return Id.CUSTOM;
	}

	@Override
	public String idFromValue(Object value) {
		return idFromValueAndType(value, value.getClass());
	}

	@Override
	public String idFromValueAndType(Object value, Class<?> suggestedType) {
		return classToId.get(suggestedType);
	}

	@Override
	public JavaType typeFromId(DatabindContext context, String id) {
		final Class<? extends MessageId> clazz = idToClass.get(id);
		return typeFactory.constructSpecializedType(baseType, clazz);
	}
}
