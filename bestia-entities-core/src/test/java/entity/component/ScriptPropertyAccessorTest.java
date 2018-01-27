package net.bestia.entity.component;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class ScriptPropertyAccessorTest {

	private static class TestOuter {
		@ScriptProperty("ageChanged")
		private int age;

		@ScriptProperty
		private TestInner inner;

		private String name = "Bruno";

		private boolean notAnnotated = false;

		public int getAge() {
			return age;
		}

		public void setAge(int age) {
			this.age = age;
		}

		@ScriptProperty("renamed")
		public String getName() {
			return name;
		}

		public void setNotAnnotated(boolean notAnnotated) {
			this.notAnnotated = notAnnotated;
		}

		public boolean getNotAnnotated() {
			return notAnnotated;
		}

		public TestInner getInner() {
			return inner;
		}

		public void setInner(TestInner inner) {
			this.inner = inner;
		}
	}

	private static class TestInner {

		@ScriptProperty
		private String name;

		public String getName() {
			return name;
		}

		public void setName(String name) {
			this.name = name;
		}
	}

	private TestOuter testAccess;
	private ScriptPropertyAccessor accessor = new ScriptPropertyAccessor(TestOuter.class);

	@Before
	public void setup() {
		testAccess = new TestOuter();
		testAccess.setAge(16);
		TestInner inner = new TestInner();
		testAccess.setInner(inner);
		inner.setName("Thomas");
		testAccess.setNotAnnotated(false);
	}

	@Test
	public void set_NonBaseProperty_false() {
		Assert.assertFalse(accessor.set("inner", testAccess, 123));
		final TestInner newInner = new TestInner();
		Assert.assertFalse(accessor.set("inner", testAccess, newInner));
	}

	@Test
	public void set_NonExistingKey_false() {
		Assert.assertFalse(accessor.set("notExisting", testAccess, 123));
	}

	@Test
	public void set_BaseProperty_true() {
		Assert.assertTrue(accessor.set("ageChanged", testAccess, 12));
		Assert.assertEquals(12, testAccess.getAge());
		Assert.assertFalse(accessor.set("age", testAccess, 10));
		Assert.assertEquals(12, testAccess.getAge());
		Assert.assertTrue(accessor.set("inner.name", testAccess, "test123"));
		Assert.assertEquals("test123", testAccess.getInner().getName());
		Assert.assertTrue(accessor.set("inner.name", testAccess, "#yolo"));
		Assert.assertEquals("#yolo", testAccess.getInner().getName());
	}

	@Test
	public void set_nonAnnotaed_false() {
		Assert.assertFalse(accessor.set("notAnnotated", testAccess, true));
		Assert.assertFalse(testAccess.getNotAnnotated());
	}

	@Test
	public void get_renamedGetter_returnsValue() {
	Assert.assertEquals("Bruno", accessor.get("renamed", testAccess));
	}

	@Test
	public void set_getterOnly_false() {
		Assert.assertFalse(accessor.set("renamed", testAccess, "Bla"));
		Assert.assertEquals("Bruno", testAccess.getName());
	}

	@Test
	public void get_NonBaseProperty_null() {
		Assert.assertNull(accessor.get("inner", testAccess));
	}

	@Test
	public void get_NonExistingProperty_null() {
		Assert.assertNull(accessor.get("age123", testAccess));
	}

	@Test
	public void get_baseProperty_notNull() {
		Assert.assertEquals("Thomas", accessor.get("inner.name", testAccess));
	}
}
