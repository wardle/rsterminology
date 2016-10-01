package com.eldrix.terminology.server.resources;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Configuration;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.UriInfo;
import javax.ws.rs.core.Response.Status;

import org.apache.cayenne.DataRow;
import org.apache.cayenne.ObjectContext;
import org.apache.cayenne.exp.Expression;
import org.apache.cayenne.query.ObjectSelect;
import org.apache.cayenne.query.SelectQuery;
import org.apache.lucene.index.CorruptIndexException;
import org.apache.lucene.queryparser.classic.ParseException;

import com.eldrix.terminology.medicine.ParsedMedication;
import com.eldrix.terminology.medicine.ParsedMedicationBuilder;
import com.eldrix.terminology.snomedct.Description;
import com.eldrix.terminology.snomedct.Project;
import com.eldrix.terminology.snomedct.Search;
import com.eldrix.terminology.snomedct.SearchUtilities;
import com.eldrix.terminology.snomedct.Search.ResultItem;
import com.nhl.link.rest.DataResponse;
import com.nhl.link.rest.LinkRestException;
import com.nhl.link.rest.runtime.LinkRestRuntime;
import com.nhl.link.rest.runtime.cayenne.ICayennePersister;

@Path("snomedct")
@Produces(MediaType.APPLICATION_JSON)
public class SearchResource {

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
			@DefaultValue("0") @QueryParam("fsn") int includeFsn,
			@DefaultValue("0") @QueryParam("inactive") int includeInactive,
			@QueryParam("project") String project,
			@Context UriInfo uriInfo) {
		try {
			Search.Request.Builder b = Search.getInstance().newBuilder();
			b.setMaxHits(maxHits)
			.withRecursiveParent(recursiveParents);
			if (search != null && search.length() > 0) {
				b.searchFor(search);
			}
			if (includeInactive == 0) {
				b.onlyActive();
			}
			if (includeFsn == 0) {
				b.withoutFullySpecifiedNames();
			}
			if (directParents.size() > 0) {
				b.withDirectParent(directParents);
			}
			List<ResultItem> result = b.build().search();
			if (project != null && project.length() > 0) {
				ICayennePersister cayenne = LinkRestRuntime.service(ICayennePersister.class, config);
				ObjectContext context = cayenne.newContext();
				Project p = ObjectSelect.query(Project.class, Project.NAME.eq(project)).selectOne(context);
				result = SearchUtilities.filterSearchForProject(result, p, recursiveParents);
			}
			return DataResponse.forObjects(result);
		} catch (IOException e) {
			e.printStackTrace();			
			throw new LinkRestException(Status.INTERNAL_SERVER_ERROR, e.getLocalizedMessage(), e);
		}
	}
	
	

	
	@GET
	@Path("dmd/parse")
	public DataResponse<ParsedMedication> parseMedication(@QueryParam("s") String search, @Context UriInfo uriInfo) throws CorruptIndexException, IOException, ParseException {
		ParsedMedication pm = new ParsedMedicationBuilder().parseString(search).build(Search.getInstance());
		return DataResponse.forObject(pm);
	}

	/**
	 * Return the synonyms for a search term
	 * @param search - search term
	 * @param roots - roots, defaults to clinical diagnoses
	 * @param maxHits - max number of hits to be returned, default 200.
	 * @param includeFsn - whether to include fully specified names, default false
	 * @param includeInactive - whether to include inactive concepts, default false
	 * @param uriInfo
	 * @return
	 */
	@GET
	@Path("synonyms")
	public DataResponse<String> synonyms(@QueryParam("s") String search, 
			@DefaultValue("138875005") @QueryParam("root") List<Long> roots,
			@DefaultValue("200") @QueryParam("maxHits") int maxHits,
			@DefaultValue("0") @QueryParam("fsn") int includeFsn,
			@DefaultValue("0") @QueryParam("inactive") int includeInactive,
			@Context UriInfo uriInfo) {
		try {
			Search.Request.Builder b = Search.getInstance().newBuilder()
				.searchFor(search)
				.setMaxHits(maxHits)
				.withRecursiveParent(roots);
			if (includeInactive == 0) {
				b.onlyActive();
			}
			if (includeFsn == 0) {
				b.withoutFullySpecifiedNames();
			}
			List<Long> conceptIds = b.build().searchForConcepts();
			ICayennePersister cayenne = LinkRestRuntime.service(ICayennePersister.class, config);
			ObjectContext context = cayenne.newContext();
			Expression qual = Description.CONCEPT_ID.in(conceptIds); 
			if (includeFsn == 0) {
				qual = qual.andExp(Description.DESCRIPTION_TYPE_CODE.ne(Description.Type.FULLY_SPECIFIED_NAME.code));
			}
			SelectQuery<DataRow> select = SelectQuery.dataRowQuery(Description.class, qual);
			List<DataRow> data = context.select(select);
			List<String> result = data.stream()
					.map(row -> (String) row.get(Description.TERM.getName()))
					.collect(Collectors.toList());
			return DataResponse.forObjects(result);
		} catch (IOException e) {
			e.printStackTrace();	
			throw new LinkRestException(Status.INTERNAL_SERVER_ERROR, e.getLocalizedMessage(), e);
		}
	}
}
