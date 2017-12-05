package com.eldrix.terminology.server.resources;

import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Configuration;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response.Status;
import javax.ws.rs.core.UriInfo;

import org.apache.cayenne.DataRow;
import org.apache.cayenne.ObjectContext;
import org.apache.cayenne.exp.Expression;
import org.apache.cayenne.query.ObjectSelect;
import org.apache.cayenne.query.SelectQuery;
import org.apache.lucene.index.CorruptIndexException;
import org.apache.lucene.queryparser.classic.ParseException;

import com.eldrix.terminology.medicine.ParsedMedication;
import com.eldrix.terminology.medicine.ParsedMedicationBuilder;
import com.eldrix.terminology.snomedct.Concept;
import com.eldrix.terminology.snomedct.Description;
import com.eldrix.terminology.snomedct.Project;
import com.eldrix.terminology.snomedct.Search;
import com.eldrix.terminology.snomedct.Search.ResultItem;
import com.eldrix.terminology.snomedct.SearchUtilities;
import com.nhl.link.rest.DataResponse;
import com.nhl.link.rest.LinkRestException;
import com.nhl.link.rest.encoder.DataResponseEncoder;
import com.nhl.link.rest.encoder.Encoder;
import com.nhl.link.rest.encoder.GenericEncoder;
import com.nhl.link.rest.encoder.ListEncoder;
import com.nhl.link.rest.runtime.LinkRestRuntime;
import com.nhl.link.rest.runtime.cayenne.ICayennePersister;

@Path("snomedct")
@Produces(MediaType.APPLICATION_JSON)
public class SearchResource {
	private static final String ERROR_NO_SEARCH_PARAMETER = "No search parameter specified";

	@Context
	private Configuration config;

	/**
	 * Search for a concept using the search terms provided.
	 * @param search - search term
	 * @param root - one or more root concept identifiers
	 * @param is - zero or more direct parent concept identifiers
	 * @param maxHits - number of hits
	 * @param fsn - whether to include FSN terms in search results (defaults to 0)
	 * @param inactive - whether to include inactive terms in search results (defaults to 0)
	 * @param fuzzy - whether to use a fuzzy search for search (default to false)
	 * @param fallbackFuzzy - whether to use a fuzzy search if no results found for non-fuzzy search (defaults to true)
	 * @param project - optional name of project to limit search results to curated list for that project
	 * @param uriInfo
	 * @return
	 */
	@GET
	@Path("search")
	public DataResponse<ResultItem> search(@QueryParam("s") String search,
			@DefaultValue("138875005") @QueryParam("root") final List<Long> recursiveParents,
			@QueryParam("is") final List<Long> directParents,
			@DefaultValue("200") @QueryParam("maxHits") int maxHits,
			@DefaultValue("false") @QueryParam("fsn") boolean includeFsn,
			@DefaultValue("false") @QueryParam("inactive") boolean includeInactive,
			@DefaultValue("false") @QueryParam("fuzzy") boolean fuzzy,
			@DefaultValue("true") @QueryParam("fallbackFuzzy") boolean fallbackFuzzy,
			@QueryParam("project") String project,
			@Context UriInfo uriInfo) {
		if (search == null || search.length() == 0) {
			throw new LinkRestException(Status.BAD_REQUEST, ERROR_NO_SEARCH_PARAMETER);
		}
		try {
			List<ResultItem> result = _performSearch(search, recursiveParents, directParents, maxHits, includeFsn,
					includeInactive, fuzzy, fallbackFuzzy, project);
			return responseWithList(result);
		} catch (IOException e) {
			e.printStackTrace();
			throw new LinkRestException(Status.INTERNAL_SERVER_ERROR, e.getLocalizedMessage(), e);
		}
	}

	private List<ResultItem> _performSearch(String search, final List<Long> recursiveParents,
			final List<Long> directParents, int maxHits, boolean includeFsn, boolean includeInactive, boolean fuzzy,
			boolean fallbackFuzzy, String project) throws CorruptIndexException, IOException {
		Search.Request.Builder b = Search.getInstance().newBuilder();
		b.setMaxHits(maxHits)
		.withRecursiveParent(recursiveParents);
		if (search != null && search.length() > 0) {
			b.search(search);
		}
		if (!includeInactive) {
			b.onlyActive();
		}
		if (!includeFsn) {
			b.withoutFullySpecifiedNames();
		}
		if (fuzzy) {
			b.useFuzzy();
		}
		if (directParents.size() > 0) {
			b.withDirectParent(directParents);
		}
		List<ResultItem> result = b.build().search();
		if (!fuzzy && fallbackFuzzy && result.size() == 0) {
			result = b.useFuzzy().build().search();
		}
		if (project != null && project.length() > 0) {
			ICayennePersister cayenne = LinkRestRuntime.service(ICayennePersister.class, config);
			ObjectContext context = cayenne.newContext();
			Project p = ObjectSelect.query(Project.class, Project.NAME.eq(project)).selectOne(context);
			result = SearchUtilities.filterSearchForProject(result, p, recursiveParents);
		}
		return result;
	}




	@GET
	@Path("dmd/parse")
	public DataResponse<ParsedMedication> parseMedication(@QueryParam("s") String search, @Context UriInfo uriInfo) throws CorruptIndexException, IOException, ParseException {
		ParsedMedication pm = new ParsedMedicationBuilder().parseString(search).build(Search.getInstance());
		return responseWithObject(pm);
	}

	/**
	 * Return the synonyms for a search term
	 * @param search - search term
	 * @param roots - roots, defaults to clinical diagnoses
	 * @param maxHits - max number of hits to be returned, default 200.
	 * @param fsn - whether to include fully specified names, default false
	 * @param inactive - whether to include inactive concepts, default false
	 * @param fuzzy - whether to perform a fuzzy match, default false, as otherwise one gets surprising matches.
	 * @param fallbackFuzzy - whether to fallback to a fuzzy search if there are no results, default true.
	 * @param uriInfo
	 * @return
	 */
	@GET
	@Path("synonyms")
	public DataResponse<String> synonyms(@QueryParam("s") String search,
			@DefaultValue("138875005") @QueryParam("root") List<Long> roots,
			@DefaultValue("200") @QueryParam("maxHits") int maxHits,
			@DefaultValue("false") @QueryParam("fsn") boolean includeFsn,
			@DefaultValue("false") @QueryParam("inactive") boolean includeInactive,
			@DefaultValue("false") @QueryParam("fuzzy") boolean fuzzy,
			@DefaultValue("true") @QueryParam("fallbackFuzzy") boolean fallbackFuzzy,
			@DefaultValue("false") @QueryParam("includeChildren") boolean includeChildren,
			@Context UriInfo uriInfo) {
		if (search == null || search.length() == 0) {
			throw new LinkRestException(Status.BAD_REQUEST, ERROR_NO_SEARCH_PARAMETER);
		}
		try {
			List<String> result = _performSynonymSearch(search, roots, maxHits, includeFsn, includeInactive, fuzzy, fallbackFuzzy, includeChildren);
			return responseWithList(result);
		} catch (IOException e) {
			e.printStackTrace();
			throw new LinkRestException(Status.INTERNAL_SERVER_ERROR, e.getLocalizedMessage(), e);
		}
	}

	/**
	 * Search for a concept by using Read code
	 */
	@GET
	@Path("read/{readCode}")
	public DataResponse<Long> getByRead(@PathParam("readCode") String readCode, 
			@Context UriInfo uriInfo) {
		ICayennePersister cayenne = LinkRestRuntime.service(ICayennePersister.class, config);
		ObjectContext context = cayenne.newContext();
		Expression qual = Concept.CTV_ID.startsWith(readCode);
		List<Concept> concepts = ObjectSelect.query(Concept.class, qual).select(context);
		List<Long> results = concepts.stream().map(c -> c.getConceptId()).collect(Collectors.toList()); 
		return responseWithList(results);
	}

	private List<String> _performSynonymSearch(String search, List<Long> roots, int maxHits, boolean includeFsn,
			boolean includeInactive, boolean fuzzy, boolean fallbackFuzzy, boolean includeChildren) throws IOException, CorruptIndexException {
		Search.Request.Builder b = Search.getInstance().newBuilder()
				.search(search).setMaxHits(maxHits).withRecursiveParent(roots);
		if (!includeInactive) {
			b.onlyActive();
		}
		if (!includeFsn) {
			b.withoutFullySpecifiedNames();
		}
		if (fuzzy) {
			b.useFuzzy();
		}
		List<Long> conceptIds = b.build().searchForConcepts();
		if (!fuzzy && fallbackFuzzy && conceptIds.size() == 0) {
			conceptIds = b.useFuzzy().build().searchForConcepts();
		}
		ICayennePersister cayenne = LinkRestRuntime.service(ICayennePersister.class, config);
		ObjectContext context = cayenne.newContext();
		Expression qual;
		if (includeChildren) {
			Expression q1 = Concept.CONCEPT_ID.in(conceptIds);
			List<Concept> concepts = ObjectSelect.query(Concept.class, q1).select(context);
			Set<Concept> allConcepts = new HashSet<>();
			concepts.forEach(c -> {
				allConcepts.add(c);
				allConcepts.addAll(c.getRecursiveChildConcepts());
			});
			qual = Description.CONCEPT.in(allConcepts);
		} else {
			qual = Description.CONCEPT_ID.in(conceptIds);
		}
		if (!includeFsn) {
			qual = qual.andExp(Description.DESCRIPTION_TYPE_CODE.ne(Description.Type.FULLY_SPECIFIED_NAME.code));
		}
		if (!includeInactive) {
			qual = qual.andExp(Description.DESCRIPTION_STATUS_CODE.in(Description.Status.activeCodes()));
		}
		SelectQuery<DataRow> select = SelectQuery.dataRowQuery(Description.class, qual);
		List<DataRow> data = context.select(select);
		return data.stream()
				.map(row -> (String) row.get(Description.TERM.getName()))
				.collect(Collectors.toList());
	}

	<T> DataResponse<T> responseWithList(List<T> data) {
		DataResponse<T> response = DataResponse.forObjects(data);
		response.setEncoder(encoder());
		return response;
	}
	<T> DataResponse<T> responseWithObject(T object) {
		DataResponse<T> response = DataResponse.forObject(object);
		response.setEncoder(encoder());
		return response;
	}
	Encoder encoder() {
		return new DataResponseEncoder("data", new ListEncoder(GenericEncoder.encoder()), "total", GenericEncoder.encoder());
	}
}
