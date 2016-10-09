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

import org.apache.cayenne.ObjectContext;
import org.apache.cayenne.ResultIterator;
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

import com.eldrix.terminology.snomedct.Search.Request.Builder;
import com.eldrix.terminology.snomedct.Search.ResultItem;
import com.eldrix.terminology.snomedct.Semantic.Dmd;
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
	public void testCrossmaps() {
		final long icd10 = 91000000146L;	
		ObjectContext context = getRuntime().newContext();
		ObjectSelect<Concept> q = ObjectSelect.query(Concept.class, Concept.CONCEPT_ID.eq(24700007L));
		Concept ms = context.selectFirst(q);
		Optional<String> msIcd10 = ms.getCrossMaps().stream()
			.filter(cm -> cm.getMapSetId() == icd10)
			.map(cm -> cm.getTarget().getCodes())
			.findFirst();
		if (msIcd10.isPresent()) {
			assertEquals("G35X", msIcd10.get());
		}
	}
	
	@Test
	public void testSearch() {
		try {
			ObjectContext context = getRuntime().newContext();
			Search search = Search.getInstance();
			Search.Request.Builder builder = new Search.Request.Builder(search);
			List<Long> results = builder.search("mult sclerosis").setMaxHits(200).withRecursiveParent(64572001L).build().searchForConcepts();
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
			
			assertNotEquals(0, builder.search("mult scler").build().search().size());
			assertNotEquals(0, builder.search("parkin").build().search().size());
			
			
		} catch (CorruptIndexException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	@Test
	public void testFuzzySearch() throws CorruptIndexException, IOException {
		Search search = Search.getInstance();
		Search.Request.Builder builder = new Search.Request.Builder(search);
		List<ResultItem> noFuzzy = builder.search("bronchopnuemonia").build().search();
		assertEquals(0, noFuzzy.size());
		
		List<ResultItem> fuzzy = builder.search("bronchopnuemonai").useFuzzy(2).build().search();
		assertNotEquals(0, fuzzy.size());
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
		List<ResultItem> sAmlodipine = b.searchUsingQueryParser("amlodip*").setMaxHits(1).withFilters(Search.Filter.DMD_VTM_OR_TF).build().search();
		assertEquals(1, sAmlodipine.size());
		Concept amlodipine = ObjectSelect.query(Concept.class, Concept.CONCEPT_ID.eq(sAmlodipine.get(0).getConceptId())).selectOne(context);
		assertNotNull(amlodipine);
		assertTrue(Semantic.Vtm.isA(amlodipine));		// this should be a VTM
		List<ResultItem> aMadopar = b.search("madopar").setMaxHits(1).withFilters(Search.Filter.DMD_VTM_OR_TF).build().search();
		Concept madopar = ObjectSelect.query(Concept.class, Concept.CONCEPT_ID.eq(aMadopar.get(0).getConceptId())).selectOne(context);
		assertTrue(Semantic.Tf.isA(madopar));

		assertEquals(0, b.search("madopar").clearFilters().withDirectParent(Dmd.Product.VIRTUAL_THERAPEUTIC_MOIETY.conceptId).build().search().size());
		assertEquals(0, b.search("madopar").clearFilters().withDirectParent(Dmd.Product.VIRTUAL_MEDICINAL_PRODUCT.conceptId).build().search().size());
		assertEquals(0, b.search("madopar").clearFilters().withDirectParent(Dmd.Product.VIRTUAL_MEDICINAL_PRODUCT_PACK.conceptId).build().search().size());
		assertNotEquals(0, b.search("madopar").clearFilters().withDirectParent(Dmd.Product.TRADE_FAMILY.conceptId).build().search().size());
		assertNotEquals(0, b.search("madopar").clearFilters().withDirectParent(Dmd.Product.ACTUAL_MEDICINAL_PRODUCT.conceptId).build().search().size());
		assertNotEquals(0, b.search("madopar").clearFilters().withDirectParent(Dmd.Product.ACTUAL_MEDICINAL_PRODUCT_PACK.conceptId).build().search().size());
	
	}
	
	@Test
	public void testRequest() throws CorruptIndexException, IOException, ParseException {
		ObjectContext context = getRuntime().newContext();
		Search search = Search.getInstance();
		List<ResultItem> sAmlodipine = new Search.Request.Builder(search).searchUsingQueryParser("amlodip*").withFilters(Search.Filter.DMD_VTM_OR_TF, Search.Filter.CONCEPT_ACTIVE).setMaxHits(1).build().search();
		assertEquals(1, sAmlodipine.size());
		Concept amlodipine = ObjectSelect.query(Concept.class, Concept.CONCEPT_ID.eq(sAmlodipine.get(0).getConceptId())).selectOne(context);
		assertTrue(Vtm.isA(amlodipine));
		
		List<ResultItem> sMultipleSclerosisInDrugs = new Search.Request.Builder(search).searchUsingQueryParser("multiple sclerosis").withFilters(Search.Filter.DMD_VTM_OR_TF).build().search();
		assertEquals(0, sMultipleSclerosisInDrugs.size());
		
		List<ResultItem> sMultipleSclerosis = new Search.Request.Builder(search).search("multiple sclerosis").withRecursiveParent(Semantic.Category.DISEASE.conceptId).setMaxHits(1).build().search();
		assertEquals(1, sMultipleSclerosis.size());
		
		List<ResultItem> sMs = new Search.Request.Builder(search).search("ms").withRecursiveParent(Semantic.Category.DISEASE.conceptId).withFilters(Search.Filter.CONCEPT_ACTIVE).setMaxHits(200).build().search();
		//sMs.forEach(ri -> System.out.println(ri));
		assertTrue(sMs.stream().anyMatch(ri -> ri.getConceptId()==24700007L));	// multiple sclerosis
		assertTrue(sMs.stream().anyMatch(ri -> ri.getConceptId()==79619009L));		// mitral stenosis
	}
	
	@Test
	public void testLocale() {
		ObjectContext context = getRuntime().newContext();
		Concept haemophilia = ObjectSelect.query(Concept.class, Concept.CONCEPT_ID.eq(90935002L)).selectOne(context);
		assertNotNull(haemophilia);
		// haemophilia has two preferred descriptions one for US and one for GB
		assertTrue(haemophilia.getDescriptions().stream()
				.filter(d -> d.isPreferred())
				.count() > 1);
		Description preferredGB = haemophilia.getPreferredDescription("en-GB").get();
		assertNotNull(preferredGB);
		assertTrue(preferredGB.isPreferred());
		assertTrue(preferredGB.isActive());
		assertEquals("en-GB", preferredGB.getLanguageCode());
		Description preferredUS = haemophilia.getPreferredDescription("en-US").get();
		assertEquals("en-US", preferredUS.getLanguageCode());
		
		Concept beclametasone = ObjectSelect.query(Concept.class, Concept.CONCEPT_ID.eq(1389007L)).selectOne(context);
		assertNotNull(beclametasone);
		Description bGp = beclametasone.getPreferredDescription("en-GB").get();
		assertTrue(bGp.isActive());
	
	}
	
	public void testAllConcepts() {
		ObjectContext context = getRuntime().newContext();
		SelectQuery<Concept> query = SelectQuery.query(Concept.class);
		query.addPrefetch(Concept.DESCRIPTIONS.joint());
		try (ResultIterator<Concept> iterator = query.iterator(context)) {
			while (iterator.hasNextRow()) {
				Concept c = iterator.nextRow();
				Description d = c.getPreferredDescription();
				assertNotNull(d);
			}
		}
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
