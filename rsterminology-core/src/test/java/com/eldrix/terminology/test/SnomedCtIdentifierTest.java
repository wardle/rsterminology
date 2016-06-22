package com.eldrix.terminology.test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import com.eldrix.terminology.snomedct.SnomedCtIdentifier;
import com.eldrix.terminology.snomedct.SnomedCtIdentifier.Type;
import com.eldrix.terminology.snomedct.VerhoeffDihedral;

public class SnomedCtIdentifierTest {

	private static long[] validConceptIdentifiers = new long[] {
		87628006,
		189914005,
		278662003,
		24700007,
		10394003,
		50270004,
		53322007,
		195030007,
		68116008
	};
	private static long[] validDescriptionIdentifiers = new long[] {
		1487430010,
		1219039017,
		1229023011,
		494580019,
		345033012
	};
	@Test
	public void testConcepts() {
		for (long num : validConceptIdentifiers) {
			assertTrue(VerhoeffDihedral.validateVerhoeff(num));
			assertFalse(VerhoeffDihedral.validateVerhoeff(num+1));
			assertFalse(VerhoeffDihedral.validateVerhoeff(num-1));
			assertEquals(0, new SnomedCtIdentifier(num).partitionIdentifier());
			assertEquals(Type.CONCEPT, new SnomedCtIdentifier(num).partitionIdentifierType());
			assertEquals(0, new SnomedCtIdentifier(num).releaseIdentifier());
		}
	}
	@Test
	public void testDescriptions() {
		
		for (long num : validDescriptionIdentifiers) {
			assertTrue(VerhoeffDihedral.validateVerhoeff(num));
			assertFalse(VerhoeffDihedral.validateVerhoeff(num+1));
			assertFalse(VerhoeffDihedral.validateVerhoeff(num-1));
			assertEquals(1, new SnomedCtIdentifier(num).partitionIdentifier());
			assertEquals(Type.DESCRIPTION, new SnomedCtIdentifier(num).partitionIdentifierType());
			assertEquals(0, new SnomedCtIdentifier(num).releaseIdentifier());
		}
	}
	@Test
	public void testMiscellaneous() {
		assertEquals(989121L, new SnomedCtIdentifier(999999990989121104L).namespaceIdentifier());
		assertEquals(1, new SnomedCtIdentifier(1290000001117L).namespaceIdentifier());
		assertEquals(1, new SnomedCtIdentifier(40000001132L).namespaceIdentifier());
	}
}
