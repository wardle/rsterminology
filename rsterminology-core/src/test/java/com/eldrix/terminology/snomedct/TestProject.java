package com.eldrix.terminology.snomedct;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.cayenne.ObjectContext;
import org.apache.cayenne.configuration.server.ServerRuntime;
import org.apache.cayenne.query.ObjectSelect;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import com.eldrix.terminology.snomedct.Search.ResultItem;
import com.eldrix.terminology.snomedct.semantic.Category;

public class TestProject {
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
	public void testFetchProject() throws IOException {
		ObjectContext context = getRuntime().newContext();
		final String ACUTE_PAEDS_NAME="CAVACUTEPAEDS";
		Project p = ObjectSelect.query(Project.class, Project.NAME.eq(ACUTE_PAEDS_NAME)).selectOne(context);
		assertEquals(ACUTE_PAEDS_NAME, p.getName());

		// calculate a list of common concepts manually rather than in a single fetch
		HashSet<Concept> common = new HashSet<Concept>();
		p.getOrderedParents().forEach(proj -> common.addAll(proj.getCommonConcepts()));

		// and now perform a filtered search...
		Search search = Search.getInstance();
		List<ResultItem> result = new Search.Request.Builder(search)
				.search("bronchio").onlyActive().withoutFullySpecifiedNames()
				.withRecursiveParent(Category.DISEASE.conceptId).build()
				.search();
		List<ResultItem> filtered1 = SearchUtilities.filterSearchForProject(result, p, Collections.singletonList(Category.DISEASE.conceptId));
		
		// and now prove we have the correct result
		List<Long> filtered2 = filtered1.stream().map(ResultItem::getConceptId).collect(Collectors.toList());
		List<Concept> filtered3 = ObjectSelect.query(Concept.class, Concept.CONCEPT_ID.in(filtered2)).select(context);
		filtered3.stream().forEach(c -> {
			assertTrue(common.contains(c));
			System.out.println(c);
		});
	}
}
