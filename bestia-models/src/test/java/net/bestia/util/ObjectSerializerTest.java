package net.bestia.util;

import net.bestia.model.util.ObjectSerializer;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;


public class ObjectSerializerTest {
	
	private ObjectSerializer<String> ser;
	
	@Before
	public void setup() {
		
		ser = new ObjectSerializer<>();
	}

	@Test(expected = NullPointerException.class)
	public void deserialize_null_throws() {
		ser.deserialize(null);
	}


	@Test(expected = NullPointerException.class)
	public void serialize_null_throws() {
		ser.serialize(null);
	}

	@Test
	public void serializeAndDeserialize_validStream_object() {
		final String dataObj = "Hello World";
		byte[] data = ser.serialize(dataObj);
		Assert.assertNotNull(data);
		final String newDataObj = ser.deserialize(data);
		Assert.assertEquals(dataObj, newDataObj);
	}

}
