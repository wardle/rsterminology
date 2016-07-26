package com.eldrix.terminology.snomedct;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.cayenne.ObjectContext;
import org.apache.cayenne.ResultBatchIterator;
import org.apache.cayenne.query.SelectQuery;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field.Store;
import org.apache.lucene.document.LongPoint;
import org.apache.lucene.document.StoredField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.CorruptIndexException;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.IndexWriterConfig.OpenMode;
import org.apache.lucene.index.IndexableField;
import org.apache.lucene.index.Term;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.BooleanQuery.Builder;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.MultiTermQuery;
import org.apache.lucene.search.PrefixQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.store.LockObtainFailedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.eldrix.terminology.snomedct.Description.Status;


/*
 * Provides full-text indexing and search facilities for SNOMED CT concepts (descriptions really) using Apache Lucene.
 * This provides a thin-wrapper around Apache Lucene's full-text search facilities.
 * 
 * Objects of this class are usually singleton objects for the index location specified and are 
 * essentially immutable and thread-safe.
 *
 */
public class Search {
	final static Logger log = LoggerFactory.getLogger(Search.class);
	final static ConcurrentHashMap<String, Search> factory = new ConcurrentHashMap<>();

	private static final String INDEX_LOCATION_PROPERTY_KEY="com.eldrix.snomedct.search.lucene.IndexLocation";
	private static final String DEFAULT_INDEX_LOCATION="/var/rsdb/sct_lucene6/";
	private static final String FIELD_TERM="term";
	private static final String FIELD_PREFERRED_TERM="preferredTerm";
	private static final String FIELD_CONCEPT_ID="conceptId";
	private static final String FIELD_PARENT_CONCEPT_ID="parentConceptId";
	private static final String FIELD_ISA_PARENT_CONCEPT_ID="isA";
	private static final String FIELD_LANGUAGE="language";
	private static final String FIELD_STATUS="status";
	private static final String FIELD_DESCRIPTION_ID="descriptionId";

	private StandardAnalyzer _analyzer = new StandardAnalyzer();
	private IndexSearcher _searcher;
	private String _indexLocation; 

	/**
	 * Get a shared instance at the default location.
	 * @return
	 * @throws CorruptIndexException
	 * @throws IOException
	 */
	public static Search getInstance() throws CorruptIndexException, IOException {
		return getInstance(null);
	}

	/**
	 * Get a shared instance at the location specified
	 * @param indexLocation
	 * @return
	 * @throws CorruptIndexException
	 * @throws IOException
	 */
	public static Search getInstance(String indexLocation) throws CorruptIndexException, IOException {
		if (indexLocation == null) {
			indexLocation = System.getProperty(INDEX_LOCATION_PROPERTY_KEY, DEFAULT_INDEX_LOCATION);
		}
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

	@Override
	public String toString() {
		return super.toString() + ": loc: `" + _indexLocation + "'";
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
				writer.commit();
			}
		}
		writer.forceMerge(1);
		writer.close();
		_searcher = createSearcher();		// create a new searcher now the index has changed.
	}

	/**
	 * Process a single description.
	 *
	 * @param writer
	 * @param d
	 * @throws CorruptIndexException
	 * @throws IOException
	 */
	protected void processDescription(IndexWriter writer, Description d) throws CorruptIndexException, IOException {
		writer.deleteDocuments(new Term("descriptionId", d.getDescriptionId().toString()));
		Document doc = new Document();
		doc.add(new TextField(FIELD_TERM, d.getTerm(), Store.YES));
		doc.add(new StoredField(FIELD_PREFERRED_TERM, d.getConcept().getPreferredDescription().getTerm()));
		doc.add(new StoredField(FIELD_LANGUAGE, d.getLanguageCode()));
		doc.add(new StoredField(FIELD_STATUS, d.getStatus().orElse(Status.CURRENT).getTitle()));
		doc.add(new StoredField(FIELD_DESCRIPTION_ID, d.getDescriptionId()));
		doc.add(new StoredField(FIELD_CONCEPT_ID, d.getConcept().getConceptId()));
		for (long parent : d.getConcept().getCachedRecursiveParents()) {
			doc.add(new LongPoint(FIELD_PARENT_CONCEPT_ID, parent));
		}
		for (Concept parent : d.getConcept().getParentConcepts()) {
			doc.add(new LongPoint(FIELD_ISA_PARENT_CONCEPT_ID, parent.getConceptId()));
		}
		writer.addDocument(doc);
	}

	public TopDocs query(Query query, Query filter, int n) throws CorruptIndexException, IOException {
		Builder builder = new BooleanQuery.Builder();
		builder.add(query, Occur.MUST);
		if (filter != null) {
			builder.add(filter, Occur.FILTER);
		}
		return searcher().search(builder.build(), n);
	}
	
	public List<ResultItem> query(String searchText, int n, long[] parentConceptIds) throws CorruptIndexException, ParseException, IOException {
		TopDocs docs = queryForTopHitsWithFilter(searchText, n, parentConceptIds);
		return resultsFromTopDocs(docs);
	}
	public List<ResultItem> query(String searchText, int n, long parentConceptId) throws CorruptIndexException, ParseException, IOException {
		return query(searchText, n, new long[] { parentConceptId });
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
		Query q1 = new BooleanQuery.Builder()
			.add(queryParser().parse("\"" + search + "\""), Occur.MUST)
			.add(filterForParentConcepts(parentConceptIds), Occur.FILTER)
			.build();
		TopDocs docs = searcher().search(q1, 500);
		//printDocuments(docs);
		ScoreDoc[] sds = docs.scoreDocs;
		Document top = null;
		if (sds.length > 0) {
			top = searcher().doc(sds[0].doc);
		}
		else {
			Query q2 = new BooleanQuery.Builder()
					.add(new PrefixQuery(new Term(FIELD_TERM, search)), Occur.MUST)
					.add(filterForParentConcepts(parentConceptIds), Occur.FILTER)
					.build();
			docs = searcher().search(q2, 500);
			sds = docs.scoreDocs;
			int topLength = 0;
			for (ScoreDoc sd : sds) {
				Document doc = searcher().doc(sd.doc);
				IndexableField termField = doc.getField(FIELD_TERM);
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

	public List<String> queryForDescriptions(String searchText, int n, long[] parentConceptIds) throws CorruptIndexException, ParseException, IOException {
		TopDocs docs = queryForTopHitsWithFilter(searchText, n, parentConceptIds);
		return descriptionsFromTopDocs(docs);
	}
	
	protected List<String> descriptionsFromTopDocs(TopDocs docs) throws CorruptIndexException, IOException {
		ArrayList<String> descs = new ArrayList<String>(docs.totalHits);
		ScoreDoc[] sds = docs.scoreDocs;
		for (ScoreDoc sd : sds) {
			Document doc = searcher().doc(sd.doc);
			IndexableField term = doc.getField(FIELD_TERM);
			descs.add(term.stringValue());
		}
		return Collections.unmodifiableList(descs);
	}

	protected List<Long> conceptsFromTopDocs(TopDocs docs) throws CorruptIndexException, IOException {
		ArrayList<Long> concepts = new ArrayList<Long>(docs.totalHits);
		ScoreDoc[] sds = docs.scoreDocs;
		for (ScoreDoc sd : sds) {
			Document doc = searcher().doc(sd.doc);
			IndexableField conceptField = doc.getField(FIELD_CONCEPT_ID);
			Long conceptId = conceptField.numericValue().longValue();
			concepts.add(conceptId);
		}
		return Collections.unmodifiableList(concepts);
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
		Query q = new BooleanQuery.Builder().add(q1, Occur.MUST).add(filterForParentConcepts(new long[] { parentConceptId}), Occur.FILTER).build();
		return searcher().search(q, n);
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
		Query q = parentConceptIds.length == 0 ? q1 : new BooleanQuery.Builder().add(q1, Occur.MUST).add(filterForParentConcepts(parentConceptIds), Occur.FILTER).build();
		return searcher().search(q, n);
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
	 * Returns a cached filter for descriptions with one of the given parent concepts.
	 *
	 * @param parentConceptIds
	 * @return
	 */
	protected static Query filterForParentConcepts(long[] parentConceptIds) {
		Builder builder = new BooleanQuery.Builder(); 
		for (long conceptId: parentConceptIds) {
			Query q = LongPoint.newExactQuery(FIELD_PARENT_CONCEPT_ID, conceptId);
			builder.add(q, Occur.MUST);
		}
		return builder.build();
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
	protected static IndexReader createOrLoadIndexReader(URI index, StandardAnalyzer analyser) throws CorruptIndexException, IOException {
		Directory directory = FSDirectory.open(Paths.get(index));
		if (DirectoryReader.indexExists(directory) == false) {
			IndexWriter writer = createOrLoadIndexWriter(index, analyser);
			writer.close();
		}
		IndexReader reader = DirectoryReader.open(directory);
		return reader;
	}

	protected static IndexWriter createOrLoadIndexWriter(URI index, StandardAnalyzer analyser) throws CorruptIndexException, LockObtainFailedException, IOException  {
		Directory directory = FSDirectory.open(Paths.get(index));
		IndexWriterConfig iwc = new IndexWriterConfig(analyser);
		iwc.setOpenMode(OpenMode.CREATE_OR_APPEND);
		IndexWriter writer = new IndexWriter(directory, iwc);
		return writer;
	}

	protected URI indexFile() {
		return new File(_indexLocation).toURI();
	}

	protected StandardAnalyzer analyser() {
		return _analyzer;
	}

	/**
	 * A queryparser is not thread safe so we create a new instance when required.
	 * @return
	 */
	protected QueryParser queryParser() {
		QueryParser qp = new QueryParser(FIELD_TERM, analyser());
		qp.setDefaultOperator(QueryParser.Operator.AND);
		qp.setMultiTermRewriteMethod(MultiTermQuery.SCORING_BOOLEAN_REWRITE);
		return qp;
	}

	protected List<ResultItem> resultsFromTopDocs(TopDocs docs) throws CorruptIndexException, IOException {
		ArrayList<ResultItem> results = new ArrayList<ResultItem>(docs.totalHits);
		ScoreDoc[] sds = docs.scoreDocs;
		for (ScoreDoc sd : sds) {
			Document doc = searcher().doc(sd.doc);
			results.add(new _ResultItem(doc));
		}
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
			IndexableField preferredTerm = doc.getField(FIELD_PREFERRED_TERM);
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

	/**
	 * Parse a list of long numbers delimited by commas into an array.
	 * @param list
	 * @return
	 */
	public static long[] parseLongArray(String list) {
		if (list != null && list.length() > 0) {
			String[] roots = list.split(",");
			long[] rootConceptIds = new long[roots.length];
			try {
				for (int i=0; i<roots.length; i++) {
					rootConceptIds[i] = Long.parseLong(roots[i]);
				}
				return rootConceptIds;
			}
			catch (NumberFormatException e) {
				;	//NOP
			}
		}
		return new long[] {} ;
	}
}
