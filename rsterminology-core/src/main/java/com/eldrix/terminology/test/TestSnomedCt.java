package com.eldrix.terminology.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.cayenne.ObjectContext;
import org.apache.cayenne.configuration.server.ServerRuntime;
import org.apache.cayenne.exp.Expression;
import org.apache.cayenne.exp.ExpressionFactory;
import org.apache.cayenne.query.ObjectSelect;
import org.apache.cayenne.validation.ValidationException;
import org.apache.cayenne.validation.ValidationResult;
import org.apache.lucene.index.CorruptIndexException;
import org.apache.lucene.queryParser.ParseException;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.eldrix.terminology.snomedct.Concept;
import com.eldrix.terminology.snomedct.Description;
import com.eldrix.terminology.snomedct.ParentCache;
import com.eldrix.terminology.snomedct.Search;
import com.eldrix.terminology.snomedct.Semantic.MedicationProduct;

public class TestSnomedCt {
	ServerRuntime _runtime;

	public ServerRuntime getRuntime() {
		return _runtime;
	}

	@Before
	public void setUp() throws Exception {
		_runtime = new ServerRuntime("cayenne-rsterminology.xml");        
	}

	@After
	public void tearDown() throws Exception {
		_runtime.shutdown();
	}

	@Test
	public void testBasicConcepts() {
		ObjectContext context = getRuntime().newContext();
		ObjectSelect<Concept> q = ObjectSelect.query(Concept.class, Concept.CONCEPT_ID.eq(24700007L));
		Concept ms = context.selectFirst(q);
		assertEquals(24700007L, (long)ms.getConceptId());
		assertTrue(ms.isAConcept(6118003));	// is multiple sclerosis a type of demyelinating disease?
		assertFalse(ms.isAConcept(32798002));	// is MS a type of parkinsonism?
		Description d = ms.getPreferredDescription();
		assertTrue(d.getStatus().get() == Description.Status.CURRENT);
		assertTrue(d.getType().get() == Description.Type.PREFERRED);
		assertTrue(d.isPreferred());
		assertTrue(d.isActive());
	}

	@Test
	public void testSearch() {
		try {
			ObjectContext context = getRuntime().newContext();
			List<Long> results = Search.queryForConcepts(Search.fixSearchString("mult sclerosis"), 100, 64572001L);
			Expression qual = ExpressionFactory.inExp(Concept.CONCEPT_ID.getName(), results);
			List<Concept> concepts = ObjectSelect.query(Concept.class, qual).select(context);
			/*
			concepts.forEach(concept -> {
				System.out.println(concept.getFullySpecifiedName());
			});
			*/
			assertTrue(concepts.size() > 0);
		} catch (CorruptIndexException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (ParseException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	@Test
	public void testRecursiveParents() {
		ObjectContext context = getRuntime().newContext();
		timingForRecursiveParents(context, 26078007L);
		timingForRecursiveParents(context, 24700007L);

	}
	
	
	@Test 
	public void testRecursiveParentBuilding() {
		ObjectContext context = getRuntime().newContext();
		Concept ms = ObjectSelect.query(Concept.class, Concept.CONCEPT_ID.eq(24700007L)).selectOne(context);
		Set<Long> recursiveParents = ms.getCachedRecursiveParents();
		ParentCache.buildParentCacheForConcept(ms);
		ms.clearCachedRecursiveParents();
		Set<Long> parents = new HashSet<Long>(ParentCache.fetchRecursiveParentsForConcept(context, 24700007L));
		Set<Long> recursiveParents2 = ms.getCachedRecursiveParents();
		assertTrue(recursiveParents.equals(parents));
		assertTrue(recursiveParents2.equals(parents));
	}
	
	private void timingForRecursiveParents(ObjectContext context, long conceptId) {
		// try method 1, fetch concept and then fetch recursive parents
		long startTime = System.nanoTime();
		Concept ms1 = ObjectSelect.query(Concept.class, Concept.CONCEPT_ID.eq(26078007L)).selectOne(context);
		List<Long> parents = ParentCache.fetchRecursiveParentsForConcept(context, 26078007L);
		long duration = (System.nanoTime() - startTime) / 1000000;
		System.out.println("Time taken for fetch and then fetch: " + duration + " ms");		

		startTime = System.nanoTime();
		// try method 2, fetch concept and prefetch parents from cache in database
		ObjectSelect<Concept> q = ObjectSelect.query(Concept.class, Concept.CONCEPT_ID.eq(26078007L));
		q.prefetch(Concept.PARENT_CONCEPTS.joint());
		Concept ms = context.selectFirst(q);
		Set<Long> parents2 = ms.getCachedRecursiveParents();
		duration = (System.nanoTime() - startTime) / 1000000;
		System.out.println("Time taken for fetch with prefetch " + duration + " ms");		

		// are methods equivalent?  this may fail if database is inconsistent
		// assertTrue(parents2.equals(new HashSet<Long>(parents)));
	}
	
	@Test
	public void testConceptCreation() {
		ObjectContext context = getRuntime().newContext();
		Concept c = context.newObject(Concept.class);
		c.setConceptId(123456L);		// an invalid concept identifier
		c.setFullySpecifiedName("A test concept");
		c.setCtvId("ctvId");
		c.setIsPrimitive(0);
		c.setSnomedId("snomedid");
		c.setConceptStatusCode(0);
		try {
			context.commitChanges();
			assertFalse("Didn't catch validation errors", true);
		}
		catch (ValidationException e) {
			ValidationResult r = e.getValidationResult();
			assertTrue(r.hasFailures(c));
			assertTrue("Invalid concept identifier".equals(r.getFailures().get(0).getError()));
		}
		
	}

	@Test
	public void testMedications() {
		ObjectContext context = getRuntime().newContext();

		// madopar CR is a trade family product
		Concept madoparCr = ObjectSelect.query(Concept.class, Concept.CONCEPT_ID.eq(9491001000001109L)).selectOne(context);
		MedicationProduct tf = MedicationProduct.productForConcept(madoparCr);
		assertEquals(tf, MedicationProduct.TRADE_FAMILY);
		
		// now let's get its AMPs.
		MedicationProduct.ampsForTf(madoparCr).forEach(c -> {
			System.out.println("AMP for " + madoparCr.getFullySpecifiedName() + ": " + c.getFullySpecifiedName());
		});
		
		// choose one AMP and interrogate it
		Concept madoparAmp = MedicationProduct.ampsForTf(madoparCr).get(0);		// get the first AMP
		assertEquals(MedicationProduct.ACTUAL_MEDICINAL_PRODUCT, MedicationProduct.productForConcept(madoparAmp));
		assertEquals(madoparCr, MedicationProduct.tfForAmp(madoparAmp));
		
		// get its VMP
		Concept madoparVmp = MedicationProduct.vmpForAmp(madoparAmp);
		assertNotNull(madoparVmp);
		assertEquals(MedicationProduct.VIRTUAL_MEDICINAL_PRODUCT, MedicationProduct.productForConcept(madoparVmp));
		assertTrue(MedicationProduct.ampsForVmp(madoparVmp).contains(madoparAmp));
		printConcept(madoparAmp);
		printConcept(madoparVmp);
		
		// get the VTM
		Concept madoparVtm = MedicationProduct.vtmForVmp(madoparVmp);
		assertEquals(MedicationProduct.VIRTUAL_THERAPEUTIC_MOIETY, MedicationProduct.productForConcept(madoparVtm));
		assertTrue(MedicationProduct.vmpsForVtm(madoparVtm).contains(madoparVmp));
		
		// get an AMPP from our AMP
		List<Concept> ampps = MedicationProduct.amppsForAmp(madoparAmp);
		assertTrue(ampps.size() > 0);
		Concept madoparAmpp = ampps.get(0);
		assertEquals(MedicationProduct.ACTUAL_MEDICINAL_PRODUCT_PACK, MedicationProduct.productForConcept(madoparAmpp));
		
		// get AMP from AMPP
		assertEquals(madoparAmp, MedicationProduct.ampForAmpp(madoparAmpp));
		printConcept(madoparAmpp);
		
		// get VMPP from AMPP
		Concept madoparVmpp = MedicationProduct.vmppForAmpp(madoparAmpp);
		assertEquals(MedicationProduct.VIRTUAL_MEDICINAL_PRODUCT_PACK, MedicationProduct.productForConcept(madoparVmpp));

		// and now get VMP from the VMPP and check it is what we think it should be
		printConcept(madoparVmpp);
		assertEquals(madoparVmp, MedicationProduct.vmpForVmpp(madoparVmpp));
		assertTrue(MedicationProduct.amppsForVmpp(madoparVmpp).contains(madoparAmpp));
		assertTrue(MedicationProduct.vmppsForVmp(madoparVmp).contains(madoparVmpp));
		
		// walk the dm&d structure directly to get from TF to VTM
		Concept madoparVtm2 = MedicationProduct.vtmForVmp(MedicationProduct.vmpForAmp(MedicationProduct.ampsForTf(madoparCr).get(0)));
		assertEquals(madoparVtm2, madoparVtm);
		
		//assertEquals(MedicationProduct.TRADE_FAMILY.vtm(madoparCr), madoparVtm);
	}
	
	private static void printConcept(Concept c) {
		StringBuilder sb = new StringBuilder();
		sb.append("Concept : " + c.getConceptId() + " : " + c.getFullySpecifiedName() + " DM&D structure : " + MedicationProduct.productForConcept(c));
		c.getChildConcepts().forEach(child -> sb.append("\n  childConcept: " + child.getFullySpecifiedName()));
		c.getParentConcepts().forEach(parent -> sb.append("\n  parentConcept: " + parent.getFullySpecifiedName()));
		c.getChildRelationships().forEach(r -> {
			sb.append("\n  childRelation: " + r.getSourceConcept().getFullySpecifiedName() + " [" + r.getRelationshipTypeConcept().getFullySpecifiedName() + "] " + r.getTargetConcept().getFullySpecifiedName());
		});
		c.getParentRelationships().forEach(r -> {
			sb.append("\n  parentRelation: " + r.getSourceConcept().getFullySpecifiedName() + " [" + r.getRelationshipTypeConcept().getFullySpecifiedName() + "] " + r.getTargetConcept().getFullySpecifiedName());
		});
		System.out.println(sb.toString());
	}
}
