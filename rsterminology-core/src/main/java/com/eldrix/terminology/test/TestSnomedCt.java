package com.eldrix.terminology.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
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
import org.apache.lucene.index.CorruptIndexException;
import org.apache.lucene.queryParser.ParseException;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.eldrix.terminology.snomedct.Concept;
import com.eldrix.terminology.snomedct.Description;
import com.eldrix.terminology.snomedct.ParentCache;
import com.eldrix.terminology.snomedct.Search;

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
			concepts.forEach(concept -> {
				System.out.println(concept.getFullySpecifiedName());
			});
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

		// are methods equivalent?
		assertTrue(parents2.equals(new HashSet<Long>(parents)));
	}
}
