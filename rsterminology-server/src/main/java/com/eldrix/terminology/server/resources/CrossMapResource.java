package com.eldrix.terminology.server.resources;

import java.util.ArrayList;
import java.util.List;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Configuration;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.UriInfo;

import org.apache.cayenne.query.Ordering;

import com.eldrix.terminology.snomedct.Concept;
import com.eldrix.terminology.snomedct.CrossMapSet;
import com.eldrix.terminology.snomedct.CrossMapTable;
import com.eldrix.terminology.snomedct.CrossMapTarget;
import com.nhl.link.rest.DataResponse;
import com.nhl.link.rest.LinkRest;
import com.nhl.link.rest.annotation.listener.QueryAssembled;
import com.nhl.link.rest.runtime.processor.select.SelectContext;

@Path("snomedct/crossmaps")
@Produces(MediaType.APPLICATION_JSON)
public class CrossMapResource {
	@Context
	private Configuration config;

	@GET
	public DataResponse<CrossMapSet> getAll(@Context UriInfo uriInfo) {
		return LinkRest.select(CrossMapSet.class, config).uri(uriInfo).select();
	}

	@GET
	@Path("{setId}")
	public DataResponse<CrossMapSet> getOne(@PathParam("setId") long id, @Context UriInfo uriInfo) {
		return LinkRest.select(CrossMapSet.class, config)
				.byId(id).uri(uriInfo)
				.selectOne();
	}

	@GET
	@Path("{setId}/{conceptId}")
	public DataResponse<CrossMapTable> crossMapForConcept(
			@PathParam("setId") long setId, 
			@PathParam("conceptId") long conceptId,
			@Context UriInfo uriInfo) {
		return LinkRest.select(CrossMapTable.class, config)
				.toManyParent(Concept.class, conceptId, Concept.CROSS_MAPS)
				.property(CrossMapTable.TARGET.dot(CrossMapTarget.CODES).getName())
				.listener(new CrossMapTableFilter(setId))
				.uri(uriInfo)
				.select();
	}


	/**
	 * Filter crossmaps to include only crossmaps matching the specified "map set"
	 * @author mark
	 *
	 */
	public static class CrossMapTableFilter {
		private long _mapSetId;

		public CrossMapTableFilter(long mapSetId) {
			_mapSetId = mapSetId;
		}

		@QueryAssembled
		public void queryAssembled(SelectContext<CrossMapTable> context) {
			context.getSelect().andQualifier(CrossMapTable.MAP_SET_ID.eq(_mapSetId));
			List<Ordering> orderings = new ArrayList<>();
			orderings.add(CrossMapTable.OPTION.asc());
			orderings.add(CrossMapTable.PRIORITY.asc());
			context.getSelect().addOrderings(orderings);
		}
	}
}
