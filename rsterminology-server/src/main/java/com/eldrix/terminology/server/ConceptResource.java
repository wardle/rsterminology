package com.eldrix.terminology.server;
import java.io.IOException;
import java.util.List;

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

import org.apache.lucene.queryparser.classic.ParseException;

import com.eldrix.terminology.snomedct.Concept;
import com.eldrix.terminology.snomedct.Search;
import com.eldrix.terminology.snomedct.Search.ResultItem;
import com.nhl.link.rest.DataResponse;
import com.nhl.link.rest.LinkRest;
import com.nhl.link.rest.LinkRestException;

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
		try {
			List<ResultItem> result = Search.getInstance().query(search, 200, rootConceptIds);
			return DataResponse.forObjects(result);
		} catch (IOException e) {
			e.printStackTrace();			
			throw new LinkRestException(Status.INTERNAL_SERVER_ERROR, e.getLocalizedMessage(), e);
		} catch (ParseException e) {
			throw new LinkRestException(Status.BAD_REQUEST, e.getLocalizedMessage(), e);
		}
	}



}
