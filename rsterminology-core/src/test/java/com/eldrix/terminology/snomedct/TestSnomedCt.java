package com.eldrix.terminology.snomedct;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

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
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import com.eldrix.terminology.medicine.ParsedMedication;
import com.eldrix.terminology.medicine.ParsedMedicationBuilder;
import com.eldrix.terminology.snomedct.Search.Request.Builder;
import com.eldrix.terminology.snomedct.Search.ResultItem;
import com.eldrix.terminology.snomedct.Semantic.Amp;
import com.eldrix.terminology.snomedct.Semantic.Ampp;
import com.eldrix.terminology.snomedct.Semantic.Dmd;
import com.eldrix.terminology.snomedct.Semantic.RelationType;
import com.eldrix.terminology.snomedct.Semantic.Tf;
import com.eldrix.terminology.snomedct.Semantic.Vmp;
import com.eldrix.terminology.snomedct.Semantic.Vmpp;
import com.eldrix.terminology.snomedct.Semantic.Vtm;

public class TestSnomedCt {
	static ServerRuntime _runtime;

	public ServerRuntime getRuntime() {
		return _runtime;
	}

	@BeforeClass
	public static void setUp() throws Exception {
		_runtime = new ServerRuntime("cayenne-project.xml");        
	}

	@AfterClass
	public static void tearDown() throws Exception {
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
			Search.Request.Builder builder = new Search.Request.Builder(search);
			List<Long> results = builder.searchFor("mult sclerosis").setMaxHits(200).withRecursiveParent(64572001L).build().searchForConcepts();
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
			
			assertTrue(builder.searchFor("parkin").build().search().size() > 0);
			assertNotEquals(0, builder.searchFor("mult scler").build().search().size());
			assertNotEquals(0, builder.searchFor("parkin").build().search().size());
			
			
		} catch (CorruptIndexException e) {
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
		Builder b = new Search.Request.Builder(search);
		List<ResultItem> sAmlodipine = b.searchByParsing("amlodip*").setMaxHits(1).withFilters(Search.Filter.DMD_VTM_OR_TF).build().search();
		assertEquals(1, sAmlodipine.size());
		Concept amlodipine = ObjectSelect.query(Concept.class, Concept.CONCEPT_ID.eq(sAmlodipine.get(0).getConceptId())).selectOne(context);
		assertNotNull(amlodipine);
		assertTrue(Semantic.Vtm.isA(amlodipine));		// this should be a VTM
		
		List<ResultItem> aMadopar = b.searchFor("madopar").setMaxHits(1).withFilters(Search.Filter.DMD_VTM_OR_TF).build().search();
		Concept madopar = ObjectSelect.query(Concept.class, Concept.CONCEPT_ID.eq(aMadopar.get(0).getConceptId())).selectOne(context);
		assertTrue(Semantic.Tf.isA(madopar));

		assertEquals(0, b.searchFor("madopar").clearFilters().withDirectParent(Dmd.Product.VIRTUAL_THERAPEUTIC_MOIETY.conceptId).build().search().size());
		assertEquals(0, b.searchFor("madopar").clearFilters().withDirectParent(Dmd.Product.VIRTUAL_MEDICINAL_PRODUCT.conceptId).build().search().size());
		assertEquals(0, b.searchFor("madopar").clearFilters().withDirectParent(Dmd.Product.VIRTUAL_MEDICINAL_PRODUCT_PACK.conceptId).build().search().size());
		assertNotEquals(0, b.searchFor("madopar").clearFilters().withDirectParent(Dmd.Product.TRADE_FAMILY.conceptId).build().search().size());
		assertNotEquals(0, b.searchFor("madopar").clearFilters().withDirectParent(Dmd.Product.ACTUAL_MEDICINAL_PRODUCT.conceptId).build().search().size());
		assertNotEquals(0, b.searchFor("madopar").clearFilters().withDirectParent(Dmd.Product.ACTUAL_MEDICINAL_PRODUCT_PACK.conceptId).build().search().size());
	
	}
	
	@Test
	public void testRequest() throws CorruptIndexException, IOException, ParseException {
		ObjectContext context = getRuntime().newContext();
		Search search = Search.getInstance();
		int[] a = Concept.Status.activeCodes();
		List<ResultItem> sAmlodipine = new Search.Request.Builder(search).searchByParsing("amlodip*").withFilters(Search.Filter.DMD_VTM_OR_TF, Search.Filter.CONCEPT_ACTIVE).setMaxHits(1).build().search();
		assertEquals(1, sAmlodipine.size());
		Concept amlodipine = ObjectSelect.query(Concept.class, Concept.CONCEPT_ID.eq(sAmlodipine.get(0).getConceptId())).selectOne(context);
		assertTrue(Vtm.isA(amlodipine));
		
		List<ResultItem> sMultipleSclerosisInDrugs = new Search.Request.Builder(search).searchByParsing("multiple sclerosis").withFilters(Search.Filter.DMD_VTM_OR_TF).build().search();
		assertEquals(0, sMultipleSclerosisInDrugs.size());
		
		List<ResultItem> sMultipleSclerosis = new Search.Request.Builder(search).searchByParsing("multiple sclerosis").withRecursiveParent(Semantic.Category.DISEASE.conceptId).setMaxHits(1).build().search();
		assertEquals(1, sMultipleSclerosis.size());
		
		List<ResultItem> sMs = new Search.Request.Builder(search).searchFor("ms").withRecursiveParent(Semantic.Category.DISEASE.conceptId).withFilters(Search.Filter.CONCEPT_ACTIVE).setMaxHits(200).build().search();
		//sMs.forEach(ri -> System.out.println(ri));
		assertTrue(sMs.stream().anyMatch(ri -> ri.getConceptId()==24700007L));	// multiple sclerosis
		assertTrue(sMs.stream().anyMatch(ri -> ri.getConceptId()==79619009L));		// mitral stenosis
	}
	
	@Test
	public void testTradeFamilies() {
		ObjectContext context = getRuntime().newContext();
		// madopar CR is a trade family product
		Concept madoparTf = ObjectSelect.query(Concept.class, Concept.CONCEPT_ID.eq(9491001000001109L)).prefetch(Concept.PARENT_RELATIONSHIPS.joint()).selectOne(context);
		assertDrugType(madoparTf, Dmd.Product.TRADE_FAMILY);
		Concept madoparVmp = Amp.getVmp(Tf.getAmps(madoparTf).findFirst().get()).get();
		assertDrugType(madoparVmp, Dmd.Product.VIRTUAL_MEDICINAL_PRODUCT);
		assertEquals(2, Vmp.getActiveIngredients(madoparVmp).count());
		
		testTradeFamily(madoparTf);
	}
	
	@Test
	public void testPrescribing() throws CorruptIndexException, IOException {
		ObjectContext context = getRuntime().newContext();
		Search search = Search.getInstance();
		Search.Request.Builder searchVmp = new Search.Request.Builder(search).setMaxHits(1).withActive().withDirectParent(Dmd.Product.VIRTUAL_MEDICINAL_PRODUCT.conceptId);

		// co-careldopa is a prescribable VTM -- see http://dmd.medicines.org.uk/DesktopDefault.aspx?VMP=377270003&toc=nofloat
		ParsedMedication pm1 = new ParsedMedicationBuilder().parseString("co-careldopa 25mg/250mg 1t tds").build();
		Concept cocareldopa = ObjectSelect.query(Concept.class, 
				Concept.CONCEPT_ID.eq(searchVmp.searchFor("co-careldopa 25mg/250mg").build().searchForConcepts().get(0))).selectOne(context);
		assertEquals(Dmd.Product.VIRTUAL_MEDICINAL_PRODUCT, Dmd.Product.productForConcept(cocareldopa));
		Vmp cocareldopaVmp = new Vmp(cocareldopa);
		assertFalse(cocareldopaVmp.isInvalidToPrescribe());
		assertFalse(cocareldopaVmp.isNotRecommendedToPrescribe());
		
		// diltiazem m/r is a VTM not recommended for prescription -- see http://dmd.medicines.org.uk/DesktopDefault.aspx?VMP=319183002&toc=nofloat
		Concept diltiazem = ObjectSelect.query(Concept.class, 
				Concept.CONCEPT_ID.eq(searchVmp.searchFor("diltiazem 120mg m/r").build().searchForConcepts().get(0))).selectOne(context);
		Vmp diltiazemVmp = new Vmp(diltiazem);
		assertFalse(diltiazemVmp.isInvalidToPrescribe());
		assertTrue(diltiazemVmp.isNotRecommendedToPrescribe());
		assertTrue(diltiazemVmp.getAmps().count() > 0);
	}
	
	@Test
	public void testEqualityDmd() {
		ObjectContext context = getRuntime().newContext();
		Concept amlodipineVtm= ObjectSelect.query(Concept.class, Concept.CONCEPT_ID.eq(108537001L)).selectOne(context);
		Vtm amlodipine1 = new Vtm(amlodipineVtm);
		Vtm amlodipine2 = new Vtm(amlodipineVtm);
		Vmp amlodipineVmp = amlodipine1.getVmps().findAny().get();
		Vtm amlodipine3 = amlodipineVmp.getVtm().get();
		assertEquals(amlodipine1, amlodipine2);
		assertEquals(amlodipine2, amlodipine3);
		assertEquals(amlodipine1, amlodipine3);
		amlodipine1.getVmps().forEach(vmp -> assertFalse(vmp.equals(amlodipine1)));
	}
	
	@Test
	public void testHashcodesDmd() throws CorruptIndexException, IOException {
		ObjectContext context = getRuntime().newContext();
		Builder b = new Search.Request.Builder(Search.getInstance());
		List<ResultItem> aMadopar = b.searchFor("madopar").setMaxHits(1).withFilters(Search.Filter.DMD_VTM_OR_TF).build().search();
		Concept madopar = ObjectSelect.query(Concept.class, Concept.CONCEPT_ID.eq(aMadopar.get(0).getConceptId())).selectOne(context);
		Tf madoparTf = new Tf(madopar);
		List<Vtm> vtms = madoparTf.getAmps()
			.map(Amp::getVmp).filter(Optional::isPresent).map(Optional::get)
			.map(Vmp::getVtm).filter(Optional::isPresent).map(Optional::get)
			.distinct().collect(Collectors.toList());
		vtms.stream().forEach(vtm -> System.out.println(vtm.concept().getFullySpecifiedName()));
		assertEquals(1, vtms.size());
	}
	
	
	@Test
	public void testVirtualTherapeuticMoieties() throws CorruptIndexException, IOException, ParseException {
		ObjectContext context = getRuntime().newContext();
		
		Concept amlodipineVtm= ObjectSelect.query(Concept.class, Concept.CONCEPT_ID.eq(108537001L)).selectOne(context);
		assertDrugType(amlodipineVtm, Dmd.Product.VIRTUAL_THERAPEUTIC_MOIETY);
		
		List<Long> amlodipineTfIds = new Search.Request.Builder(Search.getInstance()).searchFor("istin").withDirectParent(Dmd.Product.TRADE_FAMILY.conceptId).withActive().build().searchForConcepts();
		List<Concept> amlodipineTfs1 = SelectQuery.query(Concept.class, Concept.CONCEPT_ID.in(amlodipineTfIds)).select(context);
		List<Concept> amlodipineTfs2 = Vtm.getTfs(amlodipineVtm).collect(Collectors.toList());
		for (Concept aTf : amlodipineTfs2) {
			assertNotNull(aTf);
		}
		for (Concept aTf : amlodipineTfs1) {
			assertTrue(amlodipineTfs2.contains(aTf));
		}
		
		Concept amlodipineVmp = Vtm.getVmps(amlodipineVtm).findAny().get();
		List<Concept> amlodipineTfs3 = Vmp.getTfs(amlodipineVmp).collect(Collectors.toList());
		for (Concept aTf : amlodipineTfs3) {
			assertTrue(amlodipineTfs2.contains(aTf));
		}
		Concept amlodipineAmp = Vmp.getAmps(amlodipineVmp).findFirst().get();
		
		assertTrue(Vtm.getAmps(amlodipineVtm).anyMatch(amp -> amp == amlodipineAmp));
		assertDrugType(amlodipineAmp, Dmd.Product.ACTUAL_MEDICINAL_PRODUCT);
		assertFalse(Vmp.hasMultipleActiveIngredientsInName(amlodipineVmp));
		assertEquals(1, Vmp.getActiveIngredients(amlodipineVmp).count());
		assertFalse(Vmp.isInvalidToPrescribe(amlodipineVmp));
		Amp.getDispensedDoseForms(amlodipineAmp)
			.forEach(c -> System.out.println(c.getFullySpecifiedName()));
		Vmp.getDispensedDoseForms(amlodipineVmp)
			.forEach(c -> System.out.println(c.getFullySpecifiedName()));
		printConcept(Vmp.getVtm(amlodipineVmp).get());
		Vtm.getDispensedDoseForms(Vmp.getVtm(amlodipineVmp).get())
			.forEach(c -> System.out.println(c.getFullySpecifiedName()));
		
		Concept amlodipineSuspensionVmp = ObjectSelect.query(Concept.class, Concept.CONCEPT_ID.eq(8278311000001107L)).selectOne(context);
		assertTrue(Vtm.getVmps(amlodipineVtm).anyMatch(vmp -> vmp == amlodipineSuspensionVmp));
		
		/**
		 * new style DM&D processing using DM&D instances for convenience.
		 */
		Vtm amlodipine = new Vtm(amlodipineVtm);
		assertNotEquals(0, amlodipine.getTfs().count());
	}

	public static void assertDrugType(Concept concept, Dmd.Product product) {
		for (Dmd.Product dp : Dmd.Product.values()) {
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
		Dmd.Product tf = Dmd.Product.productForConcept(tradeFamily);
		assertEquals(tf, Dmd.Product.TRADE_FAMILY);
		
		// now let's get its AMPs.
		List<Concept> amps = Tf.getAmps(tradeFamily).collect(Collectors.toList());
		assertNoNullsInList(amps);
		amps.forEach(c -> {
			assertDrugType(c, Dmd.Product.ACTUAL_MEDICINAL_PRODUCT);
		});
		
		// choose one AMP and interrogate it
		Concept amp = Tf.getAmps(tradeFamily).findFirst().get();		// get the first AMP
		assertEquals(Dmd.Product.ACTUAL_MEDICINAL_PRODUCT, Dmd.Product.productForConcept(amp));
		assertDrugType(amp, Dmd.Product.ACTUAL_MEDICINAL_PRODUCT);
		assertEquals(tradeFamily, Amp.getTf(amp).get());
		
		// get its VMP
		Concept vmp = Amp.getVmp(amp).get();
		assertNotNull(vmp);
		assertDrugType(vmp, Dmd.Product.VIRTUAL_MEDICINAL_PRODUCT);
		assertEquals(Dmd.Product.VIRTUAL_MEDICINAL_PRODUCT, Dmd.Product.productForConcept(vmp));
		assertTrue(Vmp.getAmps(vmp).anyMatch(a -> a == amp));
		
		// get the VTM
		Concept vtm = Vmp.getVtm(vmp).get();
		assertDrugType(vtm, Dmd.Product.VIRTUAL_THERAPEUTIC_MOIETY);
		assertEquals(Dmd.Product.VIRTUAL_THERAPEUTIC_MOIETY, Dmd.Product.productForConcept(vtm));
		assertTrue(Vtm.getVmps(vtm).anyMatch(v -> v == vmp));
		
		// get an AMPP from our AMP
		List<Concept> ampps = Amp.getAmpps(amp).collect(Collectors.toList());
		assertNoNullsInList(ampps);
		assertTrue(ampps.size() > 0);
		Concept ampp = ampps.get(0);
		assertDrugType(ampp, Dmd.Product.ACTUAL_MEDICINAL_PRODUCT_PACK);
		assertEquals(Dmd.Product.ACTUAL_MEDICINAL_PRODUCT_PACK, Dmd.Product.productForConcept(ampp));
		
		// get AMP from AMPP
		assertEquals(amp, Ampp.getAmp(ampp).get());
		
		// get VMPP from AMPP
		Concept vmpp = Ampp.getVmpp(ampp).get();
		assertDrugType(vmpp, Dmd.Product.VIRTUAL_MEDICINAL_PRODUCT_PACK);
		assertEquals(Dmd.Product.VIRTUAL_MEDICINAL_PRODUCT_PACK, Dmd.Product.productForConcept(vmpp));

		// and now get VMP from the VMPP and check it is what we think it should be
		printConcept(vmpp);
		assertEquals(vmp, Vmpp.getVmp(vmpp).get());
		assertTrue(Vmpp.getAmpps(vmpp).anyMatch(a -> a == ampp));
		assertTrue(Vmp.getVmpps(vmp).anyMatch(v -> v == vmpp));
		
		// walk the dm&d structure directly to get from TF to VTM
		Concept vtm2 = Vmp.getVtm(Amp.getVmp(Tf.getAmps(tradeFamily).findFirst().get()).get()).get();
		assertEquals(vtm2, vtm);
		
		assertTrue(Vtm.getVmps(vtm).anyMatch(v -> v == vmp));

		// use new streams to do the same but more safely...
		List<Concept> vtms = Tf.getAmps(tradeFamily)
				.map(Amp::getVmp).filter(Optional::isPresent).map(Optional::get)
				.map(Vmp::getVtm).filter(Optional::isPresent).map(Optional::get)
				.distinct()
				.collect(Collectors.toList());
		assertEquals(1, vtms.size());
		assertEquals(vtm, vtms.get(0));
		
	}
	
	public <T> void assertNoNullsInList(List<T> l) {
		for (T i : l) {
			assertNotNull(i);
		}
	}
	
	private static void printConcept(Concept c) {
		StringBuilder sb = new StringBuilder();
		sb.append("Concept : " + c.getConceptId() + " : " + c.getFullySpecifiedName() + " DM&D structure : " + Dmd.Product.productForConcept(c));
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
