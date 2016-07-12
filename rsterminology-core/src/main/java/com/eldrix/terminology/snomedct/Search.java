package com.eldrix.terminology.snomedct;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.cayenne.ObjectContext;
import org.apache.cayenne.ResultBatchIterator;
import org.apache.cayenne.query.SelectQuery;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.index.CorruptIndexException;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.Term;
import org.apache.lucene.queryParser.ParseException;
import org.apache.lucene.queryParser.QueryParser;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.ConstantScoreQuery;
import org.apache.lucene.search.Explanation;
import org.apache.lucene.search.Filter;
import org.apache.lucene.search.FilterManager;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.PrefixQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.QueryWrapperFilter;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.store.LockObtainFailedException;
import org.apache.lucene.util.Version;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.eldrix.terminology.snomedct.Description.Status;


/*
 * Provides full-text indexing and search facilities for SNOMED CT concepts (descriptions really) using Apache Lucene.
 * This provides a thin-wrapper around Apache Lucene's full-text search facilities.
 * 
 * This is essentially immutable and thread-safe.
 *
 */
public class Search {
	final static Logger log = LoggerFactory.getLogger(Search.class);
	final static ConcurrentHashMap<String, Search> factory = new ConcurrentHashMap<>();

	private static final String INDEX_LOCATION_PROPERTY_KEY="com.eldrix.snomedct.search.lucene.IndexLocation";
	private static final String DEFAULT_INDEX_LOCATION="/var/rsdb/sct_lucene/";
	private static final Version LUCENE_VERSION=Version.LUCENE_30;
	private static final String FIELD_TERM="term";
	private static final String FIELD_PREFERRED_TERM="preferredTerm";
	private static final String FIELD_CONCEPT_ID="conceptId";
	private static final String FIELD_PARENT_CONCEPT_ID="parentConceptId";
	private static final String FIELD_ISA_PARENT_CONCEPT_ID="directParentConceptId";
	private static final String FIELD_ISA_CHILD_CONCEPT_ID="directChildConceptId";
	private static final String FIELD_LANGUAGE="language";
	private static final String FIELD_STATUS="status";
	private static final String FIELD_DESCRIPTION_ID="descriptionId";
	
	private StandardAnalyzer _analyzer = new StandardAnalyzer(LUCENE_VERSION);
	private IndexSearcher _searcher;
	private String _indexLocation; 
	
	
	public static Search getInstance() throws CorruptIndexException, IOException {
		String indexLocation = System.getProperty(INDEX_LOCATION_PROPERTY_KEY, DEFAULT_INDEX_LOCATION);
		return getInstance(indexLocation);
	}
	
	public static Search getInstance(String indexLocation) throws CorruptIndexException, IOException {
		Search search = factory.get(indexLocation);
		if (search == null) {
			factory.putIfAbsent(indexLocation, new Search(indexLocation));
			search = factory.get(indexLocation);
		}
		return search;
	}
	
	private Search(String indexLocation) throws CorruptIndexException, IOException {
		_indexLocation = indexLocation;
		_searcher = createSearcher();
	}

	private IndexSearcher createSearcher() throws CorruptIndexException, IOException {
		return new IndexSearcher(createOrLoadIndexReader(indexFile(), analyser()));
	}
	
	/**
	 * Create a new index based on all known SNOMED CT descriptions.
	 * This may take a *long* time....
	 * @throws IOException
	 * @throws LockObtainFailedException
	 * @throws CorruptIndexException
	 *
	 */
	public void processAllDescriptions(ObjectContext context) throws CorruptIndexException, LockObtainFailedException, IOException {
		IndexWriter writer = createOrLoadIndexWriter(indexFile(), analyser());
		SelectQuery<Description> query = SelectQuery.query(Description.class);
		long i = 0;
		try (ResultBatchIterator<Description> iterator = query.batchIterator(context, 500)) {
			for(List<Description> batch : iterator) {
				System.out.println("Processing batch:" + (++i));
				for (Description d : batch) {
					processDescription(writer, d);
				}
			}
		}
		writer.optimize();
		writer.close();
		IndexSearcher oldSearcher = _searcher;
		_searcher = createSearcher();		// create a new searcher now the index has changed.
		oldSearcher.close();
	}

	/**
	 * Process a single description.
	 *
	 * This is fairly inefficient - looking for the recursive parents of each concept without caching the
	 * result. However, it is logically correct and simpler than any clever caching mechanism.
	 *
	 * @param writer
	 * @param d
	 * @throws CorruptIndexException
	 * @throws IOException
	 */
	protected void processDescription(IndexWriter writer, Description d) throws CorruptIndexException, IOException {
		writer.deleteDocuments(new Term("descriptionId", d.getDescriptionId().toString()));
		Document doc = new Document();
		doc.add(new Field(FIELD_TERM, d.getTerm(), Field.Store.YES, Field.Index.ANALYZED));
		doc.add(new Field(FIELD_PREFERRED_TERM, d.getConcept().getPreferredDescription().getTerm(), Field.Store.YES, Field.Index.NOT_ANALYZED));
		doc.add(new Field(FIELD_LANGUAGE, d.getLanguageCode(), Field.Store.YES, Field.Index.NOT_ANALYZED));
		doc.add(new Field(FIELD_STATUS, d.getStatus().orElse(Status.CURRENT).getTitle(), Field.Store.YES, Field.Index.NOT_ANALYZED));
		doc.add(new Field(FIELD_DESCRIPTION_ID, d.getDescriptionId().toString(), Field.Store.YES, Field.Index.NOT_ANALYZED));
		doc.add(new Field(FIELD_CONCEPT_ID, d.getConcept().getConceptId().toString(), Field.Store.YES, Field.Index.NOT_ANALYZED));
		for (long parent : d.getConcept().getCachedRecursiveParents()) {
			doc.add(new Field(FIELD_PARENT_CONCEPT_ID, String.valueOf(parent), Field.Store.YES, Field.Index.NOT_ANALYZED));
		}
		writer.addDocument(doc);
	}

	/**
	 * Return the top 'n' hits for a given search term.
	 *
	 * @param searchText
	 * @param n
	 * @return
	 * @throws CorruptIndexException
	 * @throws IOException
	 * @throws ParseException
	 */
	@Deprecated public TopDocs queryForTopHits(String searchText, int n) throws CorruptIndexException, IOException, ParseException {
		Query q1 = queryParser().parse(searchText);
		// Query query = new TermQuery(new Term("term", searchText));
		Explanation explain = searcher().explain(q1, 200);
		TopDocs rs = searcher().search(q1, null, n);
		// printDocuments(rs);
		return rs;
	}


	public List<ResultItem> query(String searchText, int n, long parentConceptId) throws CorruptIndexException, ParseException, IOException {
		TopDocs docs = queryForTopHitsWithFilter(searchText, n, parentConceptId);
		return resultsFromTopDocs(docs);
	}
	public List<ResultItem> query(String searchText, int n, long[] parentConceptIds) throws CorruptIndexException, ParseException, IOException {
		TopDocs docs = queryForTopHitsWithFilter(searchText, n, parentConceptIds);
		return resultsFromTopDocs(docs);
	}
	public List<ResultItem> query(long parentConceptId, int n) throws CorruptIndexException, IOException {
		TopDocs docs = queryForTopHitsForParent(parentConceptId, n);
		return resultsFromTopDocs(docs);
	}
	public List<ResultItem> query(long[] parentConceptIds, int n) throws CorruptIndexException, IOException {
		TopDocs docs = queryForTopHitsForParents(parentConceptIds, n);
		return resultsFromTopDocs(docs);
	}

	/**
	 * Performs the search limiting results to those descendants of the given parent concept.
	 * Results are from the Description's "term" field.
	 */
	public List<String> queryForDescriptions(String searchText, int n, long parentConceptId) throws CorruptIndexException, ParseException, IOException {
		TopDocs docs = queryForTopHitsWithFilter(searchText, n, parentConceptId);
		return descriptionsFromTopDocs(docs);
	}

	public List<String> queryForDescriptions(String searchText, int n, long[] parentConceptIds) throws CorruptIndexException, ParseException, IOException {
		TopDocs docs = queryForTopHitsWithFilter(searchText, n, parentConceptIds);
		return descriptionsFromTopDocs(docs);
	}

	/**
	 * Returns the single shortest named concept matching the searchtext.
	 * We first run a full term search.
	 *
	 * If there is no exact match, then we need a prefix search.
	 * Unfortunately, the prefix search does not give the shortest first
	 * so we need to iterate through the results to find the shortest term that matches our prefix.
	 * @param searchText
	 * @param parentConceptIds
	 * @return
	 * @throws CorruptIndexException
	 * @throws ParseException
	 * @throws IOException
	 */
	public ResultItem queryForSingle(String searchText, long[] parentConceptIds) throws CorruptIndexException, ParseException, IOException {
		String search = QueryParser.escape(searchText.trim());
		Query q1 = queryParser().parse("\"" + search + "\"");
		TopDocs docs = searcher().search(q1, Search.filterForParentConcepts(parentConceptIds), 500);
		//printDocuments(docs);
		ScoreDoc[] sds = docs.scoreDocs;
		Document top = null;
		if (sds.length > 0) {
			top = searcher().doc(sds[0].doc);
		}
		else {
			Query q2 = new PrefixQuery(new Term(FIELD_TERM, search));
			docs = searcher().search(q2,  Search.filterForParentConcepts(parentConceptIds), 500);
			sds = docs.scoreDocs;
			int topLength = 0;
			for (ScoreDoc sd : sds) {
				Document doc = searcher().doc(sd.doc);
				Field termField = doc.getField(FIELD_TERM);
				String term = termField.stringValue();
				int termLength = term.length();
				if (top == null || topLength > termLength) {
					top = doc;
					topLength = termLength;
				}
			}
		}
		if (top != null) {
			return new _ResultItem(top);
		}
		return null;
	}

	public List<Long> queryForConcepts(String searchText, int n, long parentConceptId) throws CorruptIndexException, IOException, ParseException {
		TopDocs docs = queryForTopHitsWithFilter(searchText, n, parentConceptId);
		return conceptsFromTopDocs(docs);
	}

	public List<Long> queryForConcepts(String searchText, int n, long[] parentConceptIds) throws CorruptIndexException, ParseException, IOException {
		TopDocs docs = queryForTopHitsWithFilter(searchText, n, parentConceptIds);
		return conceptsFromTopDocs(docs);
	}

	protected List<String> descriptionsFromTopDocs(TopDocs docs) throws CorruptIndexException, IOException {
		ArrayList<String> descs = new ArrayList<String>(docs.totalHits);
		ScoreDoc[] sds = docs.scoreDocs;
		for (ScoreDoc sd : sds) {
			Document doc = searcher().doc(sd.doc);
			Field term = doc.getField(FIELD_TERM);
			descs.add(term.stringValue());
		}
		return Collections.unmodifiableList(descs);
	}

	protected List<Long> conceptsFromTopDocs(TopDocs docs) throws CorruptIndexException, IOException {
		ArrayList<Long> concepts = new ArrayList<Long>(docs.totalHits);
		ScoreDoc[] sds = docs.scoreDocs;
		for (ScoreDoc sd : sds) {
			Document doc = searcher().doc(sd.doc);
			Field conceptField = doc.getField(FIELD_CONCEPT_ID);
			Long conceptId = Long.parseLong(conceptField.stringValue());
			concepts.add(conceptId);
		}
		return Collections.unmodifiableList(concepts);
	}

	protected TopDocs queryForTopHitsForParent(long parentConceptId, int n) throws CorruptIndexException, IOException {
		Query q = queryForChildConcepts(parentConceptId);
		return searcher().search(q, n);
	}
	protected TopDocs queryForTopHitsForParents(long[] parentConceptIds, int n) throws CorruptIndexException, IOException {
		Query q = queryForChildConcepts(parentConceptIds);
		return searcher().search(q, n);
	}

	/**
	 * Perform specified query limiting results to those that are a descendent of the
	 * given concept.
	 *
	 * @param searchText
	 * @param n
	 * @param parentConceptId
	 * @return
	 * @throws ParseException
	 * @throws CorruptIndexException
	 * @throws IOException
	 */
	public TopDocs queryForTopHitsWithFilter(String searchText, int n, long parentConceptId) throws ParseException, CorruptIndexException, IOException {
		Query q1 = queryParser().parse(searchText);
		return searcher().search(q1, Search.filterForParentConcept(parentConceptId), n);
	}

	/**
	 * Perform specified query limiting results to those that are descendents of the one of given concepts.
	 * @param searchText
	 * @param n
	 * @param parentConceptIds
	 * @return
	 * @throws ParseException
	 * @throws CorruptIndexException
	 * @throws IOException
	 */
	public TopDocs queryForTopHitsWithFilter(String searchText, int n, long[] parentConceptIds) throws ParseException, CorruptIndexException, IOException {
		Query q1 = queryParser().parse(searchText);
		return searcher().search(q1, Search.filterForParentConcepts(parentConceptIds), n);
	}


	public static String fixSearchString(String s) {
		String[] tokens = stripNonAlphanumeric(s).split(" ");
		StringBuilder sb = new StringBuilder();
		for(String token: tokens) {
			sb.append(token);
			sb.append("* ");
		}
		return (sb.toString());
	}

	protected static String stripNonAlphanumeric(String s) { 
		return s.replaceAll("[^a-zA-Z0-9]", " "); 
	} 

	/**
	 * Returns a cached filter for descriptions with the given parent concept.
	 *
	 * @param parentConceptId
	 * @return
	 */
	protected static Filter filterForParentConcept(long parentConceptId) {
		Query q = new TermQuery(new Term(FIELD_PARENT_CONCEPT_ID, Long.toString(parentConceptId)));
		QueryWrapperFilter qwf = new QueryWrapperFilter(q);
		Filter f = FilterManager.getInstance().getFilter(qwf);
		return f;
	}


	/**
	 * Returns a cached filter for descriptions with one of the given parent concepts.
	 *
	 * @param parentConceptIds
	 * @return
	 */
	protected static Filter filterForParentConcepts(long[] parentConceptIds) {
		BooleanQuery bq = new BooleanQuery();
		for (long conceptId: parentConceptIds) {
			Query q = new TermQuery(new Term(FIELD_PARENT_CONCEPT_ID, Long.toString(conceptId)));
			bq.add(q, BooleanClause.Occur.SHOULD);
		}
		QueryWrapperFilter qwf = new QueryWrapperFilter(bq);
		Filter f = FilterManager.getInstance().getFilter(qwf);
		return f;
	}

	protected static Query queryForChildConcepts(long parentConceptId) {
		return new ConstantScoreQuery(filterForParentConcept(parentConceptId));
	}

	protected static Query queryForChildConcepts(long[] parentConceptIds) {
		return new ConstantScoreQuery(filterForParentConcepts(parentConceptIds));
	}

	/**
	 * This is for debugging.
	 * @param rs
	 * @throws CorruptIndexException
	 * @throws IOException
	 */
	protected TopDocs printDocuments(TopDocs rs) throws CorruptIndexException, IOException {
		ScoreDoc[] docs = rs.scoreDocs;
		System.out.println("Found " + rs.totalHits + " hits");
		for (ScoreDoc sd: docs) {
			Document doc = searcher().doc(sd.doc);
			System.out.println("Term: " + doc.getField(FIELD_TERM) + " Concept: " + doc.getField(FIELD_CONCEPT_ID) + " Score: " + sd.score);
		}
		return rs;
	}

	/*
	 * Returns the IndexSearcher used to search against the Lucene index.
	 */
	protected IndexSearcher searcher() throws CorruptIndexException, IOException {
		return _searcher;
	}

	/**
	 * This loads an IndexReader in read-only mode.
	 * @return
	 * @throws CorruptIndexException
	 * @throws IOException
	 */
	protected static IndexReader createOrLoadIndexReader(File index, StandardAnalyzer analyser) throws CorruptIndexException, IOException {
		Directory directory = FSDirectory.open(index);
		if (IndexReader.indexExists(directory)==false) {
			IndexWriter writer = createOrLoadIndexWriter(index, analyser);
			writer.close();
		}
		IndexReader reader = IndexReader.open(directory, true);
		return reader;
	}

	protected static IndexWriter createOrLoadIndexWriter(File index, StandardAnalyzer analyser) throws CorruptIndexException, LockObtainFailedException, IOException  {
		Directory directory = FSDirectory.open(index);
		IndexWriter writer = new IndexWriter(directory,
				analyser,
				IndexReader.indexExists(directory)==false,
				IndexWriter.MaxFieldLength.LIMITED);
		return writer;
	}

	protected File indexFile() {
		return new File(_indexLocation);
	}

	protected StandardAnalyzer analyser() {
		return _analyzer;
	}

	/**
	 * A queryparser is not thread safe so we create a new instance when required.
	 * @return
	 */
	protected QueryParser queryParser() {
		QueryParser qp = new QueryParser(LUCENE_VERSION, FIELD_TERM, analyser());
		qp.setDefaultOperator(QueryParser.Operator.AND);
		return qp;
	}

	protected List<ResultItem> resultsFromTopDocs(TopDocs docs) throws CorruptIndexException, IOException {
		ArrayList<ResultItem> results = new ArrayList<ResultItem>(docs.totalHits);
		ScoreDoc[] sds = docs.scoreDocs;
		for (ScoreDoc sd : sds) {
			Document doc = searcher().doc(sd.doc);
			results.add(new _ResultItem(doc));
		}
		//NSArray<EOSortOrdering> sortOrderings = new NSArray<EOSortOrdering>(EOSortOrdering.sortOrderingWithKey("term",  EOSortOrdering.CompareAscending));
		//EOSortOrdering.sortArrayUsingKeyOrderArray(results, sortOrderings);
		return results;
	}

	/**
	 * Generates a fake Lucene full-text search result containing a single result of the concept specified.
	 * This is useful in faking the initial results of a search before any real search has taken place for example.
	 * @param concept
	 * @return
	 */
	public static ResultItem resultForConcept(Concept concept) {
		if (concept == null) {
			return null;
		}
		return new _ResultItem(concept);
	}


	public interface ResultItem {
		public String getTerm();
		public long getConceptId();
		public String getPreferredTerm();
	}

	protected static class _ResultItem implements ResultItem {
		private final String _term;
		private final long _conceptId;
		private final String _preferredTerm;

		protected _ResultItem(String term, long conceptId, String preferredTerm) {
			_term = term;
			_conceptId = conceptId;
			_preferredTerm = preferredTerm;
		}

		protected _ResultItem(Document doc) {
			_term = doc.getField(FIELD_TERM).stringValue();
			_conceptId = Long.parseLong(doc.getField(FIELD_CONCEPT_ID).stringValue());
			Field preferredTerm = doc.getField(FIELD_PREFERRED_TERM);
			_preferredTerm = preferredTerm != null ? preferredTerm.stringValue() : _term;
		}
		protected _ResultItem(Concept concept) {
			_term = concept.getPreferredDescription().getTerm();
			_conceptId = concept.getConceptId();
			_preferredTerm = _term;
		}

		@Override
		public String getTerm() {
			return _term;
		}
		@Override
		public long getConceptId() {
			return _conceptId;
		}
		@Override
		public String getPreferredTerm() {
			return _preferredTerm;
		}
		@Override
		public String toString() {
			return super.toString() + ": " + getPreferredTerm() + " (" + getConceptId() + ")";
		}
	}
}
