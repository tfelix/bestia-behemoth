package bestia.util;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Helper object to serialize objects into byte arrays and vice versa. Some data
 * in the database is persisted as binary stream data. This class helps to
 * transform this data into binary streams.
 * 
 * @author Thomas Felix
 *
 * @param <T>
 *            The type on which to operate the serialization.
 */
public class ObjectSerializer<T> {

	private final static Logger LOG = LoggerFactory.getLogger(ObjectSerializer.class);

	/**
	 * Deserializes the entity from raw byte array.
	 * 
	 * @param data
	 *            Raw byte array.
	 * @return The entity.
	 */
	@SuppressWarnings("unchecked")
	public T deserialize(byte[] data) {

		Objects.requireNonNull(data);

		try (ByteArrayInputStream bis = new ByteArrayInputStream(data);
				ObjectInputStream ois = new ObjectInputStream(bis)) {

			return (T) ois.readObject();

		} catch (IOException | ClassNotFoundException | ClassCastException e) {
			LOG.warn("Could not deserialize entity.", e);
			return null;
		}

	}

	/**
	 * Serializes the object to a raw byte array.
	 * 
	 * @param obj
	 *            The object
	 * @return Raw byte array.
	 */
	public byte[] serialize(T obj) {

		Objects.requireNonNull(obj);

		try (ByteArrayOutputStream bos = new ByteArrayOutputStream();
				ObjectOutputStream ois = new ObjectOutputStream(bos)) {

			ois.writeObject(obj);
			ois.close();
			return bos.toByteArray();

		} catch (IOException e) {
			LOG.warn("Could not serialize entity.", e);
			return null;
		}

	}

}
