package com.eldrix.terminology.snomedct;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.cayenne.DataRow;
import org.apache.cayenne.ObjectContext;
import org.apache.cayenne.configuration.server.ServerRuntime;
import org.apache.cayenne.exp.Expression;
import org.apache.cayenne.query.ObjectSelect;
import org.apache.cayenne.query.SelectQuery;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import com.eldrix.terminology.snomedct.Search.ResultItem;

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

	/**
	 * Filter the results of a free-text SNOMED-CT search to include 
	 * only the "common concepts" recorded for that project and its parent projects. 
	 * @param p - the project
	 * @param unfiltered - the unfiltered result of a search
	 * @param rootConcepts - root concepts - can be null
	 * @return
	 */
	public List<ResultItem> filterSearchForProject(List<ResultItem> unfiltered, Project p, Collection<Long> rootConcepts) {
		final List<Project> parents = p.getOrderedParents().collect(Collectors.toList());
		Expression qual = ProjectConcept.PROJECT.in(parents);
		if (rootConcepts != null) {
			qual = qual.andExp(ProjectConcept.CONCEPT.dot(Concept.RECURSIVE_PARENT_CONCEPTS).dot(Concept.CONCEPT_ID).in(rootConcepts));
		}
		final List<DataRow> ids = SelectQuery.dataRowQuery(ProjectConcept.class, qual).select(p.getObjectContext());
		final Set<Long> commonConceptIds = ids.stream()
				.map(dr -> (Long) dr.get(ProjectConcept.CONCEPTCONCEPTID_PK_COLUMN))
				.distinct().collect(Collectors.toSet());
		return unfiltered.stream()
				.filter(ri -> commonConceptIds.contains(ri.getConceptId()))
				.collect(Collectors.toList());
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
				.searchFor("bronchio").withActive().withoutFullySpecifiedNames()
				.withRecursiveParent(Semantic.Category.DISEASE.conceptId).build()
				.search();
		List<ResultItem> filtered1 = filterSearchForProject(result, p, Collections.singletonList(Semantic.Category.DISEASE.conceptId));
		
		// and now prove we have the correct result
		List<Long> filtered2 = filtered1.stream().map(ResultItem::getConceptId).collect(Collectors.toList());
		List<Concept> filtered3 = ObjectSelect.query(Concept.class, Concept.CONCEPT_ID.in(filtered2)).select(context);
		filtered3.stream().forEach(c -> {
			assertTrue(common.contains(c));
			System.out.println(c);
		});
	}
}
