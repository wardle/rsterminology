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
import org.apache.lucene.queryparser.classic.ParseException;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.eldrix.terminology.snomedct.Concept;
import com.eldrix.terminology.snomedct.Description;
import com.eldrix.terminology.snomedct.ParentCache;
import com.eldrix.terminology.snomedct.Search;
import com.eldrix.terminology.snomedct.Search.ResultItem;
import com.eldrix.terminology.snomedct.Semantic;
import com.eldrix.terminology.snomedct.Semantic.Amp;
import com.eldrix.terminology.snomedct.Semantic.Ampp;
import com.eldrix.terminology.snomedct.Semantic.DmdProduct;
import com.eldrix.terminology.snomedct.Semantic.RelationType;
import com.eldrix.terminology.snomedct.Semantic.Tf;
import com.eldrix.terminology.snomedct.Semantic.Vmp;
import com.eldrix.terminology.snomedct.Semantic.Vmpp;
import com.eldrix.terminology.snomedct.Semantic.Vtm;

public class TestSnomedCt {
	ServerRuntime _runtime;

	public ServerRuntime getRuntime() {
		return _runtime;
	}

	@Before
	public void setUp() throws Exception {
		_runtime = new ServerRuntime("cayenne-project.xml");        
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
			List<Long> results = Search.getInstance().queryForConcepts(Search.fixSearchString("mult sclerosis"), 100, 64572001L);
			Expression qual = ExpressionFactory.inExp(Concept.CONCEPT_ID.getName(), results);
			List<Concept> concepts = ObjectSelect.query(Concept.class, qual).select(context);
			assertTrue(concepts.size() > 0);
			
			assertTrue(Search.getInstance().query("mult* sclerosis", 200, 64572001L).size() > 0);
			assertTrue(Search.getInstance().query("parkin*", 200, 64572001L).size() > 0);
			assertEquals(0, Search.getInstance().query("mult scler", 200, 64572001L).size());
			assertEquals(0, Search.getInstance().query("parkin", 200, 64572001L).size());
			
			
		} catch (CorruptIndexException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (ParseException e) {
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
	public void testSearchMedications() throws CorruptIndexException, ParseException, IOException {
		ObjectContext context = getRuntime().newContext();
		Search search = Search.getInstance();
		List<ResultItem> sAmlodipine = search.queryForVtmOrTf("amlodip*", 1);
		assertEquals(1, sAmlodipine.size());
		Concept amlodipine = ObjectSelect.query(Concept.class, Concept.CONCEPT_ID.eq(sAmlodipine.get(0).getConceptId())).selectOne(context);
		assertNotNull(amlodipine);
		assertTrue(Semantic.Vtm.isA(amlodipine));		// this should be a VTM
		
		List<ResultItem> aMadopar = search.queryForVtmOrTf("madopar", 1);
		Concept madopar = ObjectSelect.query(Concept.class, Concept.CONCEPT_ID.eq(aMadopar.get(0).getConceptId())).selectOne(context);
		assertTrue(Semantic.Tf.isA(madopar));
	}
	
	@Test
	public void testMedications() {
		ObjectContext context = getRuntime().newContext();
		// madopar CR is a trade family product
		Concept madoparTf = ObjectSelect.query(Concept.class, Concept.CONCEPT_ID.eq(9491001000001109L)).prefetch(Concept.PARENT_RELATIONSHIPS.joint()).selectOne(context);
		Concept madoparVmp = Amp.getVmp(Tf.getAmps(madoparTf).get(0));
		printConcept(madoparVmp);
		assertEquals(2, Vmp.activeIngredients(madoparVmp).size());
		
		testAMedication(madoparTf);
	
		Concept amlodipineTf = ObjectSelect.query(Concept.class, Concept.CONCEPT_ID.eq(108537001L)).selectOne(context);
		Tf.isA(amlodipineTf);
		Concept amlodipineAmp = Tf.getAmps(amlodipineTf).get(0);
		Concept amlodipineVmp = Amp.getVmp(amlodipineAmp);
		assertFalse(Vmp.hasMultipleActiveIngredientsInName(amlodipineVmp));
		assertEquals(1, Vmp.activeIngredients(amlodipineVmp).size());
		assertFalse(Vmp.isInvalidToPrescribe(amlodipineVmp));
		printConcept(amlodipineVmp);
		printConcept(amlodipineAmp);
		Amp.getDispensedDoseForms(amlodipineAmp).stream()
			.forEach(c -> System.out.println(c.getFullySpecifiedName()));
		Vmp.getDispensedDoseForms(amlodipineVmp).stream()
			.forEach(c -> System.out.println(c.getFullySpecifiedName()));
		printConcept(Vmp.getVtm(amlodipineVmp));
		Vtm.getDispensedDoseForms(Vmp.getVtm(amlodipineVmp)).stream()
			.forEach(c -> System.out.println(c.getFullySpecifiedName()));
		Concept amlodipineVtm = ObjectSelect.query(Concept.class, Concept.CONCEPT_ID.eq(108537001L)).selectOne(context);
		assertEquals(Vmp.getVtm(amlodipineVmp), amlodipineVtm);
		assertTrue(Vtm.getDispensedDoseForms(amlodipineVtm).size() > 0);
		
		Concept amlodipineSuspensionVmp = ObjectSelect.query(Concept.class, Concept.CONCEPT_ID.eq(8278311000001107L)).selectOne(context);
		assertTrue(Vtm.getVmps(amlodipineVtm).contains(amlodipineSuspensionVmp));
	}

	public void showDoseForms(Concept concept) {
		concept.getParentRelationships().stream()
		.filter(r ->
			r.getRelationshipTypeConcept().getConceptId() == RelationType.HAS_DOSE_FORM.conceptId ||
			r.getRelationshipTypeConcept().getConceptId() == RelationType.HAS_DISPENSED_DOSE_FORM.conceptId)
		.forEach(r ->
		System.out.println("Dose forms for :" + concept.getFullySpecifiedName() + " :" + r.getTargetConcept().getFullySpecifiedName() + " (" + r.getRelationshipTypeConcept().getFullySpecifiedName() + ")")
		);
	}
	
	public void testAMedication(Concept tradeFamily) {
		DmdProduct tf = DmdProduct.productForConcept(tradeFamily);
		assertEquals(tf, DmdProduct.TRADE_FAMILY);
		assertTrue(Tf.isA(tradeFamily));
		
		// now let's get its AMPs.
		Tf.getAmps(tradeFamily).forEach(c -> {
			assertTrue(Amp.isA(c));
		});
		
		// choose one AMP and interrogate it
		Concept madoparAmp = Tf.getAmps(tradeFamily).get(0);		// get the first AMP
		assertEquals(DmdProduct.ACTUAL_MEDICINAL_PRODUCT, DmdProduct.productForConcept(madoparAmp));
		assertTrue(Amp.isA(madoparAmp));
		assertEquals(tradeFamily, Amp.getTf(madoparAmp));
		
		// get its VMP
		Concept madoparVmp = Amp.getVmp(madoparAmp);
		assertNotNull(madoparVmp);
		assertEquals(DmdProduct.VIRTUAL_MEDICINAL_PRODUCT, DmdProduct.productForConcept(madoparVmp));
		assertTrue(Vmp.getAmps(madoparVmp).contains(madoparAmp));
		
		// get the VTM
		Concept madoparVtm = Vmp.getVtm(madoparVmp);
		assertEquals(DmdProduct.VIRTUAL_THERAPEUTIC_MOIETY, DmdProduct.productForConcept(madoparVtm));
		assertTrue(Vtm.getVmps(madoparVtm).contains(madoparVmp));
		
		// get an AMPP from our AMP
		List<Concept> ampps = Amp.getAmpps(madoparAmp);
		assertTrue(ampps.size() > 0);
		Concept madoparAmpp = ampps.get(0);
		assertEquals(DmdProduct.ACTUAL_MEDICINAL_PRODUCT_PACK, DmdProduct.productForConcept(madoparAmpp));
		
		// get AMP from AMPP
		assertEquals(madoparAmp, Ampp.getAmp(madoparAmpp));
		
		// get VMPP from AMPP
		Concept madoparVmpp = Ampp.getVmpp(madoparAmpp);
		assertEquals(DmdProduct.VIRTUAL_MEDICINAL_PRODUCT_PACK, DmdProduct.productForConcept(madoparVmpp));

		// and now get VMP from the VMPP and check it is what we think it should be
		printConcept(madoparVmpp);
		assertEquals(madoparVmp, Vmpp.getVmp(madoparVmpp));
		assertTrue(Vmpp.getAmpps(madoparVmpp).contains(madoparAmpp));
		assertTrue(Vmp.getVmpps(madoparVmp).contains(madoparVmpp));
		
		// walk the dm&d structure directly to get from TF to VTM
		Concept madoparVtm2 = Vmp.getVtm(Amp.getVmp(Tf.getAmps(tradeFamily).get(0)));
		assertEquals(madoparVtm2, madoparVtm);
		
		assertTrue(Vtm.getVmps(madoparVtm).contains(madoparVmp));
	}
	
	private static void printConcept(Concept c) {
		StringBuilder sb = new StringBuilder();
		sb.append("Concept : " + c.getConceptId() + " : " + c.getFullySpecifiedName() + " DM&D structure : " + DmdProduct.productForConcept(c));
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
