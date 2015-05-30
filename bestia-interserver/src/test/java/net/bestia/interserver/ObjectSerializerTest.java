package net.bestia.interserver;

import java.io.IOException;
import java.io.Serializable;

import org.junit.Assert;
import org.junit.Test;

public class ObjectSerializerTest {
	
	public static class TestObject implements Serializable {
		private static final long serialVersionUID = 1L;
		public int age;
		public String name;
		
		@Override
		public boolean equals(Object obj) {
			if(obj == null) {
				return false;
			}
			if(!(obj instanceof TestObject)) {
				return false;
			}
			
			TestObject o = (TestObject) obj;
			
			return o.age == this.age && o.name.equals(this.name);
		}
		
		@Override
		public int hashCode() {
			return age + name.hashCode();
		}
	}
	
	private static TestObject myTest = new TestObject();
	static {
		myTest.age = 15;
		myTest.name = "bestia";
	}

	@Test
	public void serialize_test() throws IOException {
		ObjectSerializer.serializeObject(myTest);
	}
	
	@Test
	public void deserialize_test() throws IOException, ClassNotFoundException {	
		byte[] test = ObjectSerializer.serializeObject(myTest);
		TestObject obj2 = (TestObject)ObjectSerializer.deserializeObject(test);
		Assert.assertEquals(obj2, myTest);
	}
	
	@Test(expected = IOException.class)
	public void false_deserialize_test() throws ClassNotFoundException, IOException {
		byte[] test = new byte[]{ 0x01, 0x12, 0x7A, 0x0C, 0x15 };
		ObjectSerializer.deserializeObject(test);
	}
}
