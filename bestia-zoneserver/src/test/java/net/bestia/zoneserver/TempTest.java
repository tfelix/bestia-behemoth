package net.bestia.zoneserver;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import org.junit.Test;

public class TempTest {

	private class Bla {

		public Bla(Class<? extends Object> test[]) {

		}

		public Bla(List<Class<? extends Object>> test) {

		}
		
		/*
		public Test(Class<? extends Object>... test) {

		}*/
	}

	@Test
	public void genericTest() {		
		List<Class<? extends Object>> bla = new ArrayList<>();
		Collection<Class<? extends Object>> bla2 = Arrays.asList(String.class, Long.class);
		
		for(Class<? extends Object> clazz : bla2) {
			if(clazz.equals(String.class)) {
				System.out.println("Funktioniert.");
			} else {
				System.out.println("Nope");
			}
		}
	}

}
