package com.eldrix.terminology.server.resources;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Configuration;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.UriInfo;

import com.eldrix.terminology.server.resources.CrossMapResource.CrossMapTableFilter;
import com.eldrix.terminology.snomedct.Concept;
import com.eldrix.terminology.snomedct.CrossMapTable;
import com.eldrix.terminology.snomedct.CrossMapTarget;
import com.eldrix.terminology.snomedct.Description;
import com.eldrix.terminology.snomedct.Relationship;
import com.nhl.link.rest.DataResponse;
import com.nhl.link.rest.LinkRest;
import com.nhl.link.rest.SelectBuilder;

@Path("snomedct/concepts")
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
	public DataResponse<Concept> getOne(@PathParam("conceptId") long id, @Context UriInfo uriInfo) {
		return LinkRest.select(Concept.class, config)
				.byId(id).uri(uriInfo)
				.getOne();
	}

	@GET
	@Path("{conceptId}/descriptions")
	public DataResponse<Description> descriptions(@PathParam("conceptId") long conceptId, @Context UriInfo uriInfo) {
		return LinkRest.select(Description.class, config)
				.toManyParent(Concept.class, conceptId, Concept.DESCRIPTIONS)
				.uri(uriInfo)
				.get();
	}

	@GET
	@Path("{conceptId}/children")
	public DataResponse<Relationship> children(@PathParam("conceptId") long conceptId, @Context UriInfo uriInfo) {
		return LinkRest.select(Relationship.class, config)
				.toManyParent(Concept.class, conceptId, Concept.CHILD_RELATIONSHIPS)
				.uri(uriInfo)
				.get();
	}

	@GET
	@Path("{conceptId}/parents")
	public DataResponse<Relationship> parents(@PathParam("conceptId") long conceptId, @Context UriInfo uriInfo) {
		return LinkRest.select(Relationship.class, config)
				.toManyParent(Concept.class, conceptId, Concept.PARENT_RELATIONSHIPS)
				.uri(uriInfo)
				.get();
	}

	@GET
	@Path("{conceptId}/recursiveParents") 
	public DataResponse<Concept> recursiveParents(@PathParam("conceptId") long conceptId, @Context UriInfo uriInfo) {
		return LinkRest.select(Concept.class, config)
				.toManyParent(Concept.class, conceptId, Concept.RECURSIVE_PARENT_CONCEPTS)
				.uri(uriInfo)
				.get();
	}


	@GET
	@Path("{conceptId}/crossmaps")
	public DataResponse<CrossMapTable> getAll(@PathParam("conceptId") long conceptId, @QueryParam("set") final Long crossMapSetId, @Context UriInfo uriInfo) {
		SelectBuilder<CrossMapTable> sb = LinkRest.select(CrossMapTable.class, config)
				.toManyParent(Concept.class, conceptId, Concept.CROSS_MAPS)
				.property(CrossMapTable.TARGET.dot(CrossMapTarget.CODES).getName())
				.uri(uriInfo);
		if (crossMapSetId != null) {
			sb.listener(new CrossMapTableFilter(crossMapSetId));
		}
		return sb.get();
	}
}
