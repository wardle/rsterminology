package com.eldrix.terminology.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.util.List;

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
}
