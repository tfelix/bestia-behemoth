package net.bestia.model.util;

import java.util.Arrays;

import org.junit.Assert;
import org.junit.Test;

public class ObjectSerializerTest {
	
	private ObjectSerializer<String> serializer = new ObjectSerializer<>();
	
	@Test(expected=NullPointerException.class)
	public void deserialize_null_throws() {
		serializer.deserialize(null);
	}
	
	@Test(expected=NullPointerException.class)
	public void serialize_null_throws() {
		serializer.serialize(null);
	}

	@Test
	public void deserialize_invalidData_null() {
		byte[] invalidData = new byte[10];
		Arrays.fill(invalidData, (byte) 0x12);
		
		String test = serializer.deserialize(invalidData);
		Assert.assertNull(test);
	}
	
	@Test
	public void serializeAndDeserialize_validObject_ok() {
		String test = "Hello World";
		byte[] data = serializer.serialize(test);
		
		Assert.assertNotNull(data);
		Assert.assertTrue(data.length > 0);
		
		String test2 = serializer.deserialize(data);
		
		Assert.assertEquals(test, test2);
	}
}
