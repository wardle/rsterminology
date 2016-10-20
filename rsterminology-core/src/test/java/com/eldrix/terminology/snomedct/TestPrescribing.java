package com.eldrix.terminology.snomedct;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.apache.cayenne.ObjectContext;
import org.apache.cayenne.ResultBatchIterator;
import org.apache.cayenne.configuration.server.ServerRuntime;
import org.apache.cayenne.exp.Expression;
import org.apache.cayenne.query.ObjectSelect;
import org.apache.cayenne.query.SelectQuery;
import org.apache.lucene.index.CorruptIndexException;
import org.apache.lucene.queryparser.classic.ParseException;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import com.eldrix.terminology.medicine.ParsedMedication;
import com.eldrix.terminology.medicine.ParsedMedicationBuilder;
import com.eldrix.terminology.snomedct.Search.Filter;
import com.eldrix.terminology.snomedct.Search.Request.Builder;
import com.eldrix.terminology.snomedct.Search.ResultItem;
import com.eldrix.terminology.snomedct.semantic.Amp;
import com.eldrix.terminology.snomedct.semantic.Ampp;
import com.eldrix.terminology.snomedct.semantic.Dmd;
import com.eldrix.terminology.snomedct.semantic.RelationType;
import com.eldrix.terminology.snomedct.semantic.Tf;
import com.eldrix.terminology.snomedct.semantic.Vmp;
import com.eldrix.terminology.snomedct.semantic.Vmpp;
import com.eldrix.terminology.snomedct.semantic.Vtm;

public class TestPrescribing {
	static ServerRuntime _runtime;

	public ServerRuntime getRuntime() {
		return _runtime;
	}

	@BeforeClass
	public static void setUpBeforeClass() throws Exception {
		_runtime = new ServerRuntime("cayenne-project.xml");        
	}

	@AfterClass
	public static void tearDownAfterClass() throws Exception {
		_runtime.shutdown();
	}

	@Before
	public void setUp() throws Exception {
	}

	@After
	public void tearDown() throws Exception {
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
		
		testTradeFamily(madoparTf, 2);
	}
	
	@Test
	public void testPrescribing() throws CorruptIndexException, IOException {
		ObjectContext context = getRuntime().newContext();
		Search search = Search.getInstance();
		Search.Request.Builder searchVmp = new Search.Request.Builder(search).setMaxHits(1).onlyActive().withDirectParent(Dmd.Product.VIRTUAL_MEDICINAL_PRODUCT.conceptId);

		// co-careldopa is a prescribable VTM -- see http://dmd.medicines.org.uk/DesktopDefault.aspx?VMP=377270003&toc=nofloat
		ParsedMedication pm1 = new ParsedMedicationBuilder().parseString("co-careldopa 25mg/250mg 1t tds").build();
		Concept cocareldopa = ObjectSelect.query(Concept.class, 
				Concept.CONCEPT_ID.eq(searchVmp.search("co-careldopa 25mg/250mg").build().searchForConcepts().get(0))).selectOne(context);
		assertEquals(Dmd.Product.VIRTUAL_MEDICINAL_PRODUCT, Dmd.Product.productForConcept(cocareldopa));
		Vmp cocareldopaVmp = new Vmp(cocareldopa);
		assertFalse(cocareldopaVmp.isInvalidToPrescribe());
		assertFalse(cocareldopaVmp.isNotRecommendedToPrescribe());
		assertEquals(2,cocareldopaVmp.getActiveIngredients().count());	// it has two active ingredients.
		assertFalse(cocareldopaVmp.isInvalidToPrescribe());
		assertFalse(cocareldopaVmp.isNotRecommendedToPrescribe());
		// check that this product contains levodopa.
		assertTrue(cocareldopaVmp.getActiveIngredients().map(c -> c.getPreferredDescription().getTerm()).anyMatch(s -> s.equalsIgnoreCase("levodopa")));
		
		// diltiazem m/r is a VTM not recommended for prescription -- see http://dmd.medicines.org.uk/DesktopDefault.aspx?VMP=319183002&toc=nofloat
		Concept diltiazem = ObjectSelect.query(Concept.class, 
				Concept.CONCEPT_ID.eq(searchVmp.search("diltiazem 120mg m/r").build().searchForConcepts().get(0))).selectOne(context);
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
		Vtm amlodipine3 = amlodipineVmp.getVtms().findAny().get();
		assertEquals(amlodipine1, amlodipine2);
		assertEquals(amlodipine2, amlodipine3);
		assertEquals(amlodipine1, amlodipine3);
		amlodipine1.getVmps().forEach(vmp -> assertFalse(vmp.equals(amlodipine1)));
	}
	
	@Test
	public void testHashcodesDmd() throws CorruptIndexException, IOException {
		ObjectContext context = getRuntime().newContext();
		Builder b = new Search.Request.Builder(Search.getInstance());
		List<ResultItem> aMadopar = b.search("madopar").setMaxHits(1).withFilters(Search.Filter.DMD_VTM_OR_TF).build().search();
		Concept madopar = ObjectSelect.query(Concept.class, Concept.CONCEPT_ID.eq(aMadopar.get(0).getConceptId())).selectOne(context);
		Tf madoparTf = new Tf(madopar);
		List<Vtm> vtms = madoparTf.getVtms().collect(Collectors.toList());
		vtms.stream().forEach(vtm -> System.out.println(vtm.getConcept().getFullySpecifiedName()));
		assertEquals(2, vtms.size());
	}
	
	
	@Test
	public void testVirtualTherapeuticMoieties() throws CorruptIndexException, IOException, ParseException {
		ObjectContext context = getRuntime().newContext();
		
		Concept amlodipineVtm= ObjectSelect.query(Concept.class, Concept.CONCEPT_ID.eq(108537001L)).selectOne(context);
		assertDrugType(amlodipineVtm, Dmd.Product.VIRTUAL_THERAPEUTIC_MOIETY);
		
		List<Long> amlodipineTfIds = new Search.Request.Builder(Search.getInstance()).search("istin").withDirectParent(Dmd.Product.TRADE_FAMILY.conceptId).onlyActive().build().searchForConcepts();
		List<Concept> amlodipineTfs1 = SelectQuery.query(Concept.class, Concept.CONCEPT_ID.in(amlodipineTfIds)).select(context);

		Tf istin = new Tf(amlodipineTfs1.get(0));
		Optional<Vtm> istinVtm = istin.getVtms().findAny();
		assertTrue(istinVtm.isPresent());
		assertEquals(istinVtm.get().getConcept(), amlodipineVtm);
		
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
		
		Concept amlodipineSuspensionVmp = ObjectSelect.query(Concept.class, Concept.CONCEPT_ID.eq(8278311000001107L)).selectOne(context);
		assertTrue(Vtm.getVmps(amlodipineVtm).anyMatch(vmp -> vmp == amlodipineSuspensionVmp));
		
		/**
		 * new style DM&D processing using DM&D instances for convenience.
		 */
		Vtm amlodipine = new Vtm(amlodipineVtm);
		assertNotEquals(0, amlodipine.getTfs().count());
		
	}
	
	@Test
	public void testDoseParsing() throws CorruptIndexException, IOException {
		ObjectContext context = getRuntime().newContext();
		// find a VMP for amlodipine 5mg.
		ResultItem a1 = Search.getInstance().newBuilder().search("amlodipine 5mg").withFilters(Filter.DMD_VMP_OR_AMP).onlyActive().withoutFullySpecifiedNames().setMaxHits(1).build().search().get(0);
		Concept a2 = ObjectSelect.query(Concept.class, Concept.CONCEPT_ID.eq(a1.getConceptId())).selectOne(context);
		assertTrue(Vmp.isA(a2));
		Vmp amlodipineVmp = new Vmp(a2);
		assertEquals(1, amlodipineVmp.getActiveIngredients().count());	// this VMP has only a single active ingredient.
		// and now check that the dose is correctly parsed
		//assertEquals(new BigDecimal("5.0"), amlodipineVmp.getDose());
	}

	public static void assertDrugType(Concept concept, Dmd.Product product) {
		for (Dmd.Product dp : Dmd.Product.values()) {
			if (dp == product) {
				assertTrue(dp.isAProduct(concept));
			}
			else {
				assertFalse(dp.isAProduct(concept));
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
	
	public void testTradeFamily(Concept tradeFamily, int numberOfVtms ) {
		Dmd.Product tf = Dmd.Product.productForConcept(tradeFamily);
		assertEquals(tf, Dmd.Product.TRADE_FAMILY);
		
		// now let's get its AMPs.
		assertTrue(Tf.getAmps(tradeFamily).noneMatch(amp -> amp == null));
		Tf.getAmps(tradeFamily).forEach(c -> {
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
		Concept vtm = Vmp.getVtms(vmp).findAny().get();
		assertDrugType(vtm, Dmd.Product.VIRTUAL_THERAPEUTIC_MOIETY);
		assertEquals(Dmd.Product.VIRTUAL_THERAPEUTIC_MOIETY, Dmd.Product.productForConcept(vtm));
		assertTrue(Vtm.getVmps(vtm).anyMatch(v -> v == vmp));
		
		// get an AMPP from our AMP
		assertTrue(Amp.getAmpps(amp).noneMatch(ampp -> ampp == null));
		assertTrue(Amp.getAmpps(amp).count() > 0);
		Concept ampp = Amp.getAmpps(amp).findFirst().get();
		assertDrugType(ampp, Dmd.Product.ACTUAL_MEDICINAL_PRODUCT_PACK);
		assertEquals(Dmd.Product.ACTUAL_MEDICINAL_PRODUCT_PACK, Dmd.Product.productForConcept(ampp));
		
		// get AMP from AMPP
		assertEquals(amp, Ampp.getAmp(ampp).get());
		
		// get VMPP from AMPP
		Concept vmpp = Ampp.getVmpp(ampp).get();
		assertDrugType(vmpp, Dmd.Product.VIRTUAL_MEDICINAL_PRODUCT_PACK);
		assertEquals(Dmd.Product.VIRTUAL_MEDICINAL_PRODUCT_PACK, Dmd.Product.productForConcept(vmpp));

		// and now get VMP from the VMPP and check it is what we think it should be
		assertEquals(vmp, Vmpp.getVmp(vmpp).get());
		assertTrue(Vmpp.getAmpps(vmpp).anyMatch(a -> a == ampp));
		assertTrue(Vmp.getVmpps(vmp).anyMatch(v -> v == vmpp));
		
		// walk the dm&d structure directly to get from TF to VTM
		assertTrue(Tf.getVtms(tradeFamily).anyMatch(vtm2 -> vtm2.getConceptId() == vtm.getConceptId()));		
		assertTrue(Vtm.getVmps(vtm).anyMatch(v -> v == vmp));

		// use new streams to do the same but more safely...
		List<Concept> vtms = Tf.getVtms(tradeFamily).collect(Collectors.toList());
		assertEquals(numberOfVtms, vtms.size());
		assertEquals(vtm, vtms.get(0));
		
	}
	@Test
	public void testSomeVmps() {
		ObjectContext context = getRuntime().newContext();
		Expression exp = Concept.PARENT_RELATIONSHIPS.dot(Relationship.TARGET_CONCEPT.dot(Concept.CONCEPT_ID)).eq(Dmd.Product.VIRTUAL_MEDICINAL_PRODUCT.conceptId).andExp(Concept.PARENT_RELATIONSHIPS.dot(Relationship.RELATIONSHIP_TYPE_CONCEPT.dot(Concept.CONCEPT_ID)).eq(RelationType.IS_A.conceptId));
		SelectQuery<Concept> query = SelectQuery.query(Concept.class, exp);
		//query.setFetchLimit(500);
		try (ResultBatchIterator<Concept> iterator = query.batchIterator(context, 100)) {
			for(List<Concept> vmps : iterator) {
				for (Concept c : vmps) {
					assertTrue(Vmp.isA(c));
					Vmp vmp = new Vmp(c);
					vmp.getAmps().forEach(amp -> {
						long count = amp.getDispensedDoseForms().count();
						if (count == 0) {
						}
					});
				}
			}
		}
	}

}
