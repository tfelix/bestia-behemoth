package net.bestia.zoneserver.script;

import org.junit.Assert;
import org.junit.Test;

import net.bestia.zoneserver.util.PackageLoader;

public class GeneralScriptTest {

	@Test
	public void stdctor_impl() {
		final PackageLoader<Script> pkg = new PackageLoader<>(Script.class, "net.bestia.zoneserver.script");
		Assert.assertTrue(pkg.haveAllStdCtor());
	}
}
