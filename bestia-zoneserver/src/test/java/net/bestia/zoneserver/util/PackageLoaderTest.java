package net.bestia.zoneserver.util;

import java.util.Set;

import org.junit.Assert;
import org.junit.Test;

import net.bestia.zoneserver.testutil.packageload.A;
import net.bestia.zoneserver.testutil.packageload.A1;
import net.bestia.zoneserver.testutil.packageload.A2;
import net.bestia.zoneserver.testutil.packageload.B;
import net.bestia.zoneserver.testutil.packageload.B1;

public class PackageLoaderTest {
	
	private final static String PKG = "net.bestia.zoneserver.testutil.packageload";

	@Test
	public void subclasses_stdctor_all() {
		PackageLoader<A> pkg = new PackageLoader<>(A.class, PKG);
		Set<Class<? extends A>> clazzes = pkg.getSubclasses();
		Assert.assertTrue(clazzes.contains(A1.class));
		Assert.assertTrue(clazzes.contains(A2.class));
	}
	
	@Test
	public void subclasses_nonstdctor_all() {
		PackageLoader<B> pkg = new PackageLoader<>(B.class, PKG);
		Set<Class<? extends B>> clazzes = pkg.getSubclasses();
		Assert.assertTrue(clazzes.contains(B1.class));
	}

	@Test
	public void subobjects_stdctor_nonabstract() {
		PackageLoader<A> pkg = new PackageLoader<>(A.class, PKG);
		Set<A> objs = pkg.getSubObjects();
		Assert.assertTrue(objs.size() == 1);
		Assert.assertTrue(objs.toArray()[0] instanceof A2);
	}

	@Test
	public void subobjects_nonstdctor_missing() {
		PackageLoader<B> pkg = new PackageLoader<>(B.class, PKG);
		Set<B> objs = pkg.getSubObjects();
		Assert.assertTrue(objs.size() == 0);
	}

	@Test
	public void hasstdctor_stdctorclasses_true() {
		PackageLoader<A> pkg = new PackageLoader<>(A.class, PKG);
		Assert.assertTrue(pkg.haveAllStdCtor());
	}

	@Test
	public void hasstdctor_nonstdctorclasses_false() {
		PackageLoader<B> pkg = new PackageLoader<>(B.class, PKG);
		Assert.assertFalse(pkg.haveAllStdCtor());
	}
}
