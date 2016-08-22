package com.eldrix.terminology.snomedct;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.cayenne.DataRow;
import org.apache.cayenne.exp.Expression;
import org.apache.cayenne.query.SelectQuery;

import com.eldrix.terminology.snomedct.Search.ResultItem;

/**
 * Helper methods for common search scenarios.
 * @author Mark Wardle
 *
 */
public class SearchUtilities {
	/**
	 * Filter the results of a free-text SNOMED-CT search to include 
	 * only the "common concepts" recorded for that project and its parent projects. 
	 * While specifying root concepts is optional, it is more efficient to specify the same root concepts
	 * that were used for the search to be filtered.
	 * @param p - the project.
	 * @param unfiltered - the unfiltered result of a search.
	 * @param rootConcepts - root concepts - can be null but more efficient if specified.
	 * @return
	 */
	public static List<ResultItem> filterSearchForProject(List<ResultItem> unfiltered, Project p, Collection<Long> rootConcepts) {
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
	
}
