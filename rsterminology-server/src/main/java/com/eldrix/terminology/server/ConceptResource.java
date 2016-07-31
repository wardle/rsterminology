package com.eldrix.terminology.server;
import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

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
import org.apache.cayenne.query.SelectQuery;
import org.apache.lucene.queryparser.classic.ParseException;

import com.eldrix.terminology.snomedct.Concept;
import com.eldrix.terminology.snomedct.Description;
import com.eldrix.terminology.snomedct.Search;
import com.eldrix.terminology.snomedct.Search.ResultItem;
import com.eldrix.terminology.snomedct.Semantic;
import com.nhl.link.rest.DataResponse;
import com.nhl.link.rest.LinkRest;
import com.nhl.link.rest.LinkRestException;
import com.nhl.link.rest.runtime.LinkRestRuntime;
import com.nhl.link.rest.runtime.cayenne.ICayennePersister;

@Path("concept")
@Produces(MediaType.APPLICATION_JSON)
public class ConceptResource {

	@Context
	private Configuration config;
	
	/**
	 * Return information about a specified concept.
	 * @param id
	 * @param uriInfo
	 * @return
	 */
	@GET
	@Path("{conceptId}")
	public DataResponse<Concept> getOne(@PathParam("conceptId") int id, @Context UriInfo uriInfo) {
		return LinkRest.select(Concept.class, config)
				.byId(id).uri(uriInfo)
				.selectOne();
	}

	/**
	 * Search for a concept using the search terms provided.
	 * @param search
	 * @param rootIds
	 * @param uriInfo
	 * @return
	 */
	@GET
	@Path("search")
	public DataResponse<ResultItem> search(@QueryParam("s") String search, @QueryParam("rootIds") String rootIds, @Context UriInfo uriInfo) {
		long[] rootConceptIds = Search.parseLongArray(rootIds);
		if (rootConceptIds.length == 0) {
			rootConceptIds = new long[] { Semantic.Category.SNOMED_CT_ROOT.conceptId };
		}
		try {
			List<ResultItem> result = new Search.Request.Builder().search(search).setMaxHits(200).withParents(rootConceptIds).build().search(Search.getInstance());
			return DataResponse.forObjects(result);
		} catch (IOException e) {
			e.printStackTrace();			
			throw new LinkRestException(Status.INTERNAL_SERVER_ERROR, e.getLocalizedMessage(), e);
		} catch (ParseException e) {
			throw new LinkRestException(Status.BAD_REQUEST, e.getLocalizedMessage(), e);
		}
	}

	@GET
	@Path("synonyms")
	public DataResponse<String> synonyms(@QueryParam("s") String search, @QueryParam("rootIds") String rootIds, @Context UriInfo uriInfo) {
		long[] rootConceptIds = Search.parseLongArray(rootIds);
		if (rootConceptIds.length == 0) {
			rootConceptIds = new long[] { Semantic.Category.SNOMED_CT_ROOT.conceptId };
		}
		try {
			List<Long> conceptIds = new Search.Request.Builder().search(search).setMaxHits(5).withParents(rootConceptIds).build().searchForConcepts(Search.getInstance());
			ICayennePersister cayenne = LinkRestRuntime.service(ICayennePersister.class, config);
			ObjectContext context = cayenne.newContext();
			SelectQuery<DataRow> select = SelectQuery.dataRowQuery(Description.class, Description.CONCEPT_ID.in(conceptIds));
			List<DataRow> data = select.select(context);
			List<String> result = data.stream().map(row -> (String) row.get(Description.TERM.getName())).collect(Collectors.toList());
			return DataResponse.forObjects(result);
		} catch (ParseException e) {
			throw new LinkRestException(Status.BAD_REQUEST, e.getLocalizedMessage(), e);
		} catch (IOException e) {
			e.printStackTrace();	
			throw new LinkRestException(Status.INTERNAL_SERVER_ERROR, e.getLocalizedMessage(), e);
		}
	}

}
