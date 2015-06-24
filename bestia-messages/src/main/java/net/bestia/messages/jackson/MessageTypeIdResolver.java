package net.bestia.messages.jackson;

import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.Set;

import net.bestia.messages.Message;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.reflections.Reflections;

import com.fasterxml.jackson.annotation.JsonTypeInfo.Id;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.jsontype.impl.TypeIdResolverBase;
import com.fasterxml.jackson.databind.type.TypeFactory;

/**
 * Custom TypeId Resolver for message objects. Upon start it looks for all messages which inherit from Message and
 * determine their id. It stores it and uses this serialize the messages.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public class MessageTypeIdResolver extends TypeIdResolverBase {

	private final static Logger log = LogManager.getLogger(MessageTypeIdResolver.class);

	private final TypeFactory typeFactory = TypeFactory.defaultInstance();
	private final HashMap<String, Class<? extends Message>> idToClass = new HashMap<String, Class<? extends Message>>();
	private HashMap<Class<? extends Message>, String> classToId = new HashMap<Class<? extends Message>, String>();

	private JavaType baseType;

	/**
	 * Finds all IDs of the messages and registers them for later identification.
	 */
	@Override
	public void init(JavaType bt) {
		super.init(bt);
		baseType = bt;

		// Find all classes implementing the message interface.
		Reflections reflections = new Reflections("net.bestia.messages");
		Set<Class<? extends Message>> messages = reflections.getSubTypesOf(Message.class);
		// Instantiate the message classes to get their message id from the method and store
		// it for later serialization and deserialization.
		for (Class<? extends Message> msg : messages) {

			// Avoid abstract classes.
			if (Modifier.isAbstract(msg.getModifiers())) {
				continue;
			}

			try {
				Constructor<? extends Message> cons = msg.getConstructor();

				String key = cons.newInstance().getMessageId();

				idToClass.put(key, msg);
				classToId.put(msg, key);

				log.trace("Found Message.class: {} - {}", key, msg.toString());

			} catch (Exception e) {
				log.fatal("Could not initialize all message handler. Serialization and deserialization will fail.", e);
				System.exit(1);
			}
		}
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
	public JavaType typeFromId(String key) {
		Class<? extends Message> clazz = idToClass.get(key);
		return typeFactory.constructSpecializedType(baseType, clazz);
	}

}
