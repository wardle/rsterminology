package com.eldrix.terminology.snomedct;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.function.Consumer;

import org.apache.cayenne.ObjectContext;
import org.apache.cayenne.ResultBatchIterator;
import org.apache.cayenne.map.SQLResult;
import org.apache.cayenne.query.EJBQLQuery;
import org.apache.cayenne.query.SQLTemplate;
import org.apache.cayenne.query.SelectQuery;

public class ParentCache {
	private static final int BATCH_SIZE=500;
	
	/*
	 * Custom recursive SQL to derive all parents of a given concept.
	 * @see http://www.medicalnerds.com/recursive-sql-with-postgresql-84/
	 */
	private final static String RECURSIVE_PARENTS_SQL = "with recursive parent_concepts(concept_id) as (" +
			"select t0.concept_id from t_concept t0 inner join t_relationship t1 on t0.concept_id = t1.target_concept_id " +
			"where (t1.relationship_type_concept_id = 116680003 and t1.source_concept_id = $conceptId) " +
			"union  " +
			"select t0.concept_id from t_concept t0,t_relationship t1, parent_concepts pc " +
			"where t0.concept_id = t1.target_concept_id " +
			"and t1.source_concept_id = pc.concept_id and t1.relationship_type_concept_id = 116680003" +
			") " +	
			"select distinct(concept_id) from parent_concepts;";

	/*
	 * Build a SQL query according to the template.
	 * @param conceptId
	 * @return
	 */
	private static SQLTemplate _sqlTemplateForRecursiveParentsForConcept(long conceptId) {
		SQLTemplate query = new SQLTemplate(Concept.class, RECURSIVE_PARENTS_SQL);
		query.setParams(Collections.singletonMap("conceptId", String.valueOf(conceptId)));
		query.setFetchingDataRows(true);
		return query;
	}
	
	/**
	 * Return the recursive parents for the given concept.
	 * @param context
	 * @param conceptId
	 * @return
	 */
	public static List<Long> fetchRecursiveParentsForConcept(ObjectContext context, long conceptId) {
		SQLTemplate query = _sqlTemplateForRecursiveParentsForConcept(conceptId);
		SQLResult resultDescriptor = new SQLResult();
		resultDescriptor.addColumnResult("concept_id");
		query.setResult(resultDescriptor);
		@SuppressWarnings("unchecked") List<Long> result =  context.performQuery(query);
		return result;
	}
	
	/**
	 * Build the cached parent concept cache.
	 * This will naturally take a considerable amount of time to process and it is suggested that this be run
	 * within a background task.
	 */
	public static void buildParentCache(ObjectContext context) {
		EJBQLQuery countQuery = new EJBQLQuery("select COUNT(c) FROM Concept c");
		@SuppressWarnings("unchecked")
		long count = ((List<Long>)context.performQuery(countQuery)).get(0);
		SelectQuery<Concept> query = SelectQuery.query(Concept.class);
		timedBatchIterator(context, query, BATCH_SIZE, count, (c) -> {
			buildParentCacheForConcept(c);
		});
	}

	/**
	 * A very simple helper method to iterate through a select query showing progress.
	 * Most useful in a command line utility.
	 */
	public static <T> void timedBatchIterator(ObjectContext context, SelectQuery<T> query, int batchSize, long count, Consumer<T> forEach) {
		long i = 1;
		long batches = count / batchSize;
		long estimated = 0;
		System.out.println("Processing " + count + " in " + batches + " batches...");
		long start = System.currentTimeMillis();
		try (ResultBatchIterator<T> iterator = query.batchIterator(context, batchSize)) {
			for(List<T> batch : iterator) {
				System.out.print("\rProcessing batch " + i + "/" + batches + (estimated == 0 ? "" : " Remaining: ~" + estimated / 60000 + " min"));
				for (T c : batch) {
					forEach.accept(c);
				}
				i++;
				long elapsed = System.currentTimeMillis() - start;
				estimated = (batches - i) * elapsed / i;
			}
		}
		System.out.println("\nFinished processing : " + i);
	}
	
	
	/**
	 * Build the cached parent concept cache for the given concept.
	 * @param concept
	 */
	public static void buildParentCacheForConcept(Concept concept) {
		ObjectContext context = concept.getObjectContext();
		long conceptId = concept.getConceptId();
		List<Long> parents = ParentCache.fetchRecursiveParentsForConcept(context, conceptId);
		removeConceptCachedParents(context, conceptId);
		addConceptCachedParents(context, conceptId, parents);
	}
	
	
	/*
	 * Remove the parent concepts of the given concept from the database parent cache.
	 */
	protected static void removeConceptCachedParents(ObjectContext context, long conceptId) {
		SQLTemplate query = new SQLTemplate(Concept.class, "delete from t_cached_parent_concepts where child_concept_id=$childConceptId");
		query.setParams(Collections.singletonMap("childConceptId", String.valueOf(conceptId)));
		context.performGenericQuery(query);
	}

	/*
	 * Add the parent concepts for the given concept to the database parent cache.
	 */
	protected static void addConceptCachedParents(ObjectContext context, long conceptId, List<Long> parents) {
		for (long parentId: parents) {
			SQLTemplate query = new SQLTemplate(Concept.class, "insert into t_cached_parent_concepts (child_concept_id, parent_concept_id) values ($childConceptId, $parentConceptId)");
			HashMap<String, String> params = new HashMap<>();
			params.put("childConceptId", String.valueOf(conceptId));
			params.put("parentConceptId", String.valueOf(parentId));
			query.setParams(params);
			context.performGenericQuery(query);
		}
	}}
