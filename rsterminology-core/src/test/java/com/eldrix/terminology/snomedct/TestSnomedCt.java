package com.eldrix.terminology.snomedct;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
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
import org.apache.cayenne.query.SelectQuery;
import org.apache.cayenne.validation.ValidationException;
import org.apache.cayenne.validation.ValidationResult;
import org.apache.lucene.index.CorruptIndexException;
import org.apache.lucene.queryparser.classic.ParseException;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.eldrix.terminology.snomedct.Search.ResultItem;
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
			Search search = Search.getInstance();
			Search.Request.Builder builder = new Search.Request.Builder();
			List<Long> results = builder.search("mult sclerosis").setMaxHits(200).withRecursiveParent(64572001L).build().searchForConcepts(search);
			System.out.println(builder._query);
			Expression qual = ExpressionFactory.inExp(Concept.CONCEPT_ID.getName(), results);
			List<Concept> concepts = ObjectSelect.query(Concept.class, qual).prefetch(Concept.DESCRIPTIONS.joint()).select(context);
			assertTrue(concepts.size() > 0);
			
			for (Concept c : concepts) {
				boolean matched = c.getDescriptions().stream().anyMatch(d -> {
					String t = d.getTerm().toLowerCase();
					return t.contains("mult") && t.contains("sclerosis");
				});
				if (matched == false) {
					System.out.println("Concept doesn't match appropriately: " + c.getFullySpecifiedName() + " " + c.getConceptId());
					assertTrue(false);
				}
			}
			
			assertTrue(builder.search("parkin").build().search(Search.getInstance()).size() > 0);
			assertNotEquals(0, builder.search("mult scler").build().search(search).size());
			assertNotEquals(0, builder.search("parkin").build().search(search).size());
			
			
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
		q.prefetch(Concept.RECURSIVE_PARENT_CONCEPTS.joint());
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
		List<ResultItem> sAmlodipine = new Search.Request.Builder().parseQuery("amlodip*").setMaxHits(1).withFilters(Search.Filter.DMD_VTM_OR_TF).build().search(search);
		assertEquals(1, sAmlodipine.size());
		Concept amlodipine = ObjectSelect.query(Concept.class, Concept.CONCEPT_ID.eq(sAmlodipine.get(0).getConceptId())).selectOne(context);
		assertNotNull(amlodipine);
		assertTrue(Semantic.Vtm.isA(amlodipine));		// this should be a VTM
		
		List<ResultItem> aMadopar = new Search.Request.Builder().search("madopar").setMaxHits(1).withFilters(Search.Filter.DMD_VTM_OR_TF).build().search(search);
		Concept madopar = ObjectSelect.query(Concept.class, Concept.CONCEPT_ID.eq(aMadopar.get(0).getConceptId())).selectOne(context);
		assertTrue(Semantic.Tf.isA(madopar));

		assertEquals(0, new Search.Request.Builder().search("madopar").withDirectParent(Semantic.DmdProduct.VIRTUAL_THERAPEUTIC_MOIETY.conceptId).build().search(search).size());
		assertEquals(0, new Search.Request.Builder().search("madopar").withDirectParent(Semantic.DmdProduct.VIRTUAL_MEDICINAL_PRODUCT.conceptId).build().search(search).size());
		assertEquals(0, new Search.Request.Builder().search("madopar").withDirectParent(Semantic.DmdProduct.VIRTUAL_MEDICINAL_PRODUCT_PACK.conceptId).build().search(search).size());
		assertNotEquals(0, new Search.Request.Builder().search("madopar").withDirectParent(Semantic.DmdProduct.TRADE_FAMILY.conceptId).build().search(search).size());
		assertNotEquals(0, new Search.Request.Builder().search("madopar").withDirectParent(Semantic.DmdProduct.ACTUAL_MEDICINAL_PRODUCT.conceptId).build().search(search).size());
		assertNotEquals(0, new Search.Request.Builder().search("madopar").withDirectParent(Semantic.DmdProduct.ACTUAL_MEDICINAL_PRODUCT_PACK.conceptId).build().search(search).size());
	}
	
	@Test
	public void testRequest() throws CorruptIndexException, IOException, ParseException {
		ObjectContext context = getRuntime().newContext();
		Search search = Search.getInstance();
		int[] a = Concept.Status.activeCodes();
		List<ResultItem> sAmlodipine = new Search.Request.Builder().parseQuery("amlodip*").withFilters(Search.Filter.DMD_VTM_OR_TF, Search.Filter.CONCEPT_ACTIVE).setMaxHits(1).build().search(search);
		assertEquals(1, sAmlodipine.size());
		Concept amlodipine = ObjectSelect.query(Concept.class, Concept.CONCEPT_ID.eq(sAmlodipine.get(0).getConceptId())).selectOne(context);
		assertTrue(Vtm.isA(amlodipine));
		
		List<ResultItem> sMultipleSclerosisInDrugs = new Search.Request.Builder().parseQuery("multiple sclerosis").withFilters(Search.Filter.DMD_VTM_OR_TF).build().search(search);
		assertEquals(0, sMultipleSclerosisInDrugs.size());
		
		List<ResultItem> sMultipleSclerosis = new Search.Request.Builder().parseQuery("multiple sclerosis").withRecursiveParent(Semantic.Category.DISEASE.conceptId).setMaxHits(1).build().search(search);
		assertEquals(1, sMultipleSclerosis.size());
		
		List<ResultItem> sMs = new Search.Request.Builder().search("ms").withRecursiveParent(Semantic.Category.DISEASE.conceptId).withFilters(Search.Filter.CONCEPT_ACTIVE).setMaxHits(200).build().search(search);
		//sMs.forEach(ri -> System.out.println(ri));
		assertTrue(sMs.stream().anyMatch(ri -> ri.getConceptId()==24700007L));	// multiple sclerosis
		assertTrue(sMs.stream().anyMatch(ri -> ri.getConceptId()==79619009L));		// mitral stenosis
	}
	
	@Test
	public void testTradeFamilies() {
		ObjectContext context = getRuntime().newContext();
		// madopar CR is a trade family product
		Concept madoparTf = ObjectSelect.query(Concept.class, Concept.CONCEPT_ID.eq(9491001000001109L)).prefetch(Concept.PARENT_RELATIONSHIPS.joint()).selectOne(context);
		assertDrugType(madoparTf, DmdProduct.TRADE_FAMILY);
		Concept madoparVmp = Amp.getVmp(Tf.getAmps(madoparTf).get(0));
		assertDrugType(madoparVmp, DmdProduct.VIRTUAL_MEDICINAL_PRODUCT);
		assertEquals(2, Vmp.activeIngredients(madoparVmp).size());
		
		testTradeFamily(madoparTf);
	}
	
	@Test
	public void testVirtualTherapeuticMoieties() throws CorruptIndexException, IOException, ParseException {
		ObjectContext context = getRuntime().newContext();
		
		Concept amlodipineVtm= ObjectSelect.query(Concept.class, Concept.CONCEPT_ID.eq(108537001L)).selectOne(context);
		assertDrugType(amlodipineVtm, DmdProduct.VIRTUAL_THERAPEUTIC_MOIETY);
		
		List<Long> amlodipineTfIds = new Search.Request.Builder().search("istin").withDirectParent(DmdProduct.TRADE_FAMILY.conceptId).withActive().build().searchForConcepts(Search.getInstance());
		List<Concept> amlodipineTfs1 = SelectQuery.query(Concept.class, Concept.CONCEPT_ID.in(amlodipineTfIds)).select(context);
		List<Concept> amlodipineTfs2 = Vtm.getTfs(amlodipineVtm);
		for (Concept aTf : amlodipineTfs1) {
			assertTrue(amlodipineTfs2.contains(aTf));
		}
		
		Concept amlodipineVmp = Vtm.getVmps(amlodipineVtm).get(0);
		List<Concept> amlodipineTfs3 = Vmp.getTfs(amlodipineVmp);
		for (Concept aTf : amlodipineTfs3) {
			assertTrue(amlodipineTfs2.contains(aTf));
		}
		Concept amlodipineAmp = Vmp.getAmps(amlodipineVmp).get(0);
		
		assertTrue(Vtm.getAmps(amlodipineVtm).contains(amlodipineAmp));
		assertDrugType(amlodipineAmp, DmdProduct.ACTUAL_MEDICINAL_PRODUCT);
		assertFalse(Vmp.hasMultipleActiveIngredientsInName(amlodipineVmp));
		assertEquals(1, Vmp.activeIngredients(amlodipineVmp).size());
		assertFalse(Vmp.isInvalidToPrescribe(amlodipineVmp));
		Amp.getDispensedDoseForms(amlodipineAmp).stream()
			.forEach(c -> System.out.println(c.getFullySpecifiedName()));
		Vmp.getDispensedDoseForms(amlodipineVmp).stream()
			.forEach(c -> System.out.println(c.getFullySpecifiedName()));
		printConcept(Vmp.getVtm(amlodipineVmp));
		Vtm.getDispensedDoseForms(Vmp.getVtm(amlodipineVmp)).stream()
			.forEach(c -> System.out.println(c.getFullySpecifiedName()));
		
		Concept amlodipineSuspensionVmp = ObjectSelect.query(Concept.class, Concept.CONCEPT_ID.eq(8278311000001107L)).selectOne(context);
		assertTrue(Vtm.getVmps(amlodipineVtm).contains(amlodipineSuspensionVmp));
	}
	
	
	public static void assertDrugType(Concept concept, DmdProduct product) {
		for (DmdProduct dp : DmdProduct.values()) {
			if (dp == product) {
				assertTrue(dp.isAConcept(concept));
			}
			else {
				assertFalse(dp.isAConcept(concept));
			}
		}
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
	
	public void testTradeFamily(Concept tradeFamily) {
		DmdProduct tf = DmdProduct.productForConcept(tradeFamily);
		assertEquals(tf, DmdProduct.TRADE_FAMILY);
		
		// now let's get its AMPs.
		Tf.getAmps(tradeFamily).forEach(c -> {
			assertDrugType(c, DmdProduct.ACTUAL_MEDICINAL_PRODUCT);
		});
		
		// choose one AMP and interrogate it
		Concept amp = Tf.getAmps(tradeFamily).get(0);		// get the first AMP
		assertEquals(DmdProduct.ACTUAL_MEDICINAL_PRODUCT, DmdProduct.productForConcept(amp));
		assertDrugType(amp, DmdProduct.ACTUAL_MEDICINAL_PRODUCT);
		assertEquals(tradeFamily, Amp.getTf(amp));
		
		// get its VMP
		Concept vmp = Amp.getVmp(amp);
		assertNotNull(vmp);
		assertDrugType(vmp, DmdProduct.VIRTUAL_MEDICINAL_PRODUCT);
		assertEquals(DmdProduct.VIRTUAL_MEDICINAL_PRODUCT, DmdProduct.productForConcept(vmp));
		assertTrue(Vmp.getAmps(vmp).contains(amp));
		
		// get the VTM
		Concept vtm = Vmp.getVtm(vmp);
		assertDrugType(vtm, DmdProduct.VIRTUAL_THERAPEUTIC_MOIETY);
		assertEquals(DmdProduct.VIRTUAL_THERAPEUTIC_MOIETY, DmdProduct.productForConcept(vtm));
		assertTrue(Vtm.getVmps(vtm).contains(vmp));
		
		// get an AMPP from our AMP
		List<Concept> ampps = Amp.getAmpps(amp);
		assertTrue(ampps.size() > 0);
		Concept ampp = ampps.get(0);
		assertDrugType(ampp, DmdProduct.ACTUAL_MEDICINAL_PRODUCT_PACK);
		assertEquals(DmdProduct.ACTUAL_MEDICINAL_PRODUCT_PACK, DmdProduct.productForConcept(ampp));
		
		// get AMP from AMPP
		assertEquals(amp, Ampp.getAmp(ampp));
		
		// get VMPP from AMPP
		Concept vmpp = Ampp.getVmpp(ampp);
		assertDrugType(vmpp, DmdProduct.VIRTUAL_MEDICINAL_PRODUCT_PACK);
		assertEquals(DmdProduct.VIRTUAL_MEDICINAL_PRODUCT_PACK, DmdProduct.productForConcept(vmpp));

		// and now get VMP from the VMPP and check it is what we think it should be
		printConcept(vmpp);
		assertEquals(vmp, Vmpp.getVmp(vmpp));
		assertTrue(Vmpp.getAmpps(vmpp).contains(ampp));
		assertTrue(Vmp.getVmpps(vmp).contains(vmpp));
		
		// walk the dm&d structure directly to get from TF to VTM
		Concept vtm2 = Vmp.getVtm(Amp.getVmp(Tf.getAmps(tradeFamily).get(0)));
		assertEquals(vtm2, vtm);
		
		assertTrue(Vtm.getVmps(vtm).contains(vmp));
	}
	
	private static void printConcept(Concept c) {
		StringBuilder sb = new StringBuilder();
		sb.append("Concept : " + c.getConceptId() + " : " + c.getFullySpecifiedName() + " DM&D structure : " + DmdProduct.productForConcept(c));
		c.getRecursiveChildConcepts().forEach(child -> sb.append("\n  childConcept: " + child.getFullySpecifiedName()));
		c.getRecursiveParentConcepts().forEach(parent -> sb.append("\n  parentConcept: " + parent.getFullySpecifiedName()));
		c.getChildRelationships().forEach(r -> {
			sb.append("\n  childRelation: " + r.getSourceConcept().getFullySpecifiedName() + " [" + r.getRelationshipTypeConcept().getFullySpecifiedName() + "] " + r.getTargetConcept().getFullySpecifiedName());
		});
		c.getParentRelationships().forEach(r -> {
			sb.append("\n  parentRelation: " + r.getSourceConcept().getFullySpecifiedName() + " [" + r.getRelationshipTypeConcept().getFullySpecifiedName() + "] " + r.getTargetConcept().getFullySpecifiedName());
		});
		System.out.println(sb.toString());
	}
}
