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
import javax.ws.rs.core.UriInfo;

import org.apache.lucene.queryParser.ParseException;

import com.eldrix.terminology.snomedct.Concept;
import com.eldrix.terminology.snomedct.Search;
import com.eldrix.terminology.snomedct.Search.ResultItem;
import com.nhl.link.rest.DataResponse;
import com.nhl.link.rest.LinkRest;

@Path("concept")
@Produces(MediaType.APPLICATION_JSON)
public class ConceptResource {

	@Context
	private Configuration config;

	@GET
	@Path("{conceptId}")
	public DataResponse<Concept> getOne(@PathParam("conceptId") int id, @Context UriInfo uriInfo) {
		return LinkRest.select(Concept.class, config).byId(id).uri(uriInfo).selectOne();
	}
	
	@GET
	@Path("search")
	public DataResponse<ResultItem> search(@QueryParam("s") String search, @QueryParam("rootIds") String rootIds, @Context UriInfo uriInfo) {
		long[] rootConceptIds = _parseLongArray(rootIds);
		List<ResultItem> result;
		try {
			result = Search.query(search, 200, rootConceptIds);
			return DataResponse.forObjects(result);
		} catch (ParseException | IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return null;
	}
	

	static long[] _parseLongArray(String list) {
		String[] roots = list.split(",");
		long[] rootConceptIds = new long[roots.length];
		try {
			for (int i=0; i<roots.length; i++) {
				rootConceptIds[i] = Long.parseLong(roots[i]);
			}
			return rootConceptIds;
		}
		catch (NumberFormatException e) {
			return new long[] {} ;
		}
	}

}
