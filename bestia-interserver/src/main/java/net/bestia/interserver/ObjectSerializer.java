package net.bestia.interserver;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;

/**
 * Helper class which can be used to serialize objects.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
final public class ObjectSerializer {

	/**
	 * Should not be used. Use it in a static fashion.
	 */
	private ObjectSerializer() {
		// no op.
	}

	/**
	 * Serializes an object into a byte array.
	 * 
	 * @param obj
	 *            Object to be serialized. Must implement serializable.
	 * @return Byte array of this object.
	 * @throws IOException
	 *             If the object can not be written to a bytestream.
	 */
	public static byte[] serializeObject(Serializable obj) throws IOException {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		ObjectOutputStream os = new ObjectOutputStream(out);
		os.writeObject(obj);
		return out.toByteArray();
	}

	/**
	 * Deserializes an byte array back to an object.
	 * 
	 * @param data
	 *            Bytestream of the object to be deserialized.
	 * @return The object deserialized.
	 * @throws IOException
	 *             If something goes wrong during deserialization.
	 * @throws ClassNotFoundException
	 *             If the class of the object deserialized was not in the
	 *             classpath.
	 */
	public static Object deserializeObject(byte[] data) throws IOException, ClassNotFoundException {
		ByteArrayInputStream b = new ByteArrayInputStream(data);
		ObjectInputStream o = new ObjectInputStream(b);
		return o.readObject();
	}
}
