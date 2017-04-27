package com.eldrix.terminology.server.resources;
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

import com.eldrix.terminology.snomedct.Concept;
import com.eldrix.terminology.snomedct.Project;
import com.nhl.link.rest.DataResponse;
import com.nhl.link.rest.LinkRest;
import com.nhl.link.rest.SelectBuilder;

@Path("projects")
@Produces(MediaType.APPLICATION_JSON)
public class ProjectResource {

	@Context
	private Configuration config;

	/**
	 * Return information about a specified project.
	 * Note: you can use a URL such as 
	 * http://localhost:8080/projects/41?include=commonConcepts.descriptions.term
	 * to get information about the project as well as the list of common concepts and the synonyms.
	 * @param id
	 * @param uriInfo
	 * @return
	 */
	@GET
	@Path("{projectId}")
	public DataResponse<Project> getOne(@PathParam("projectId") int id, @Context UriInfo uriInfo) {
		return LinkRest.select(Project.class, config)
				.byId(id).uri(uriInfo)
				.getOne();
	}
	
	@GET
	public DataResponse<Project> getAll(@Context UriInfo uriInfo) {
		return LinkRest.select(Project.class, config).uri(uriInfo).get();
	}
	
	@GET
	@Path("{projectId}/commonConcepts")
	public DataResponse<Concept> commonConcepts(
			@PathParam("projectId") int projectId, @Context UriInfo uriInfo,
			@QueryParam("root") final List<Long> recursiveParents ) {
		SelectBuilder<Concept> sb = LinkRest.select(Concept.class, config)
				.toManyParent(Project.class, projectId, Project.COMMON_CONCEPTS)
				.uri(uriInfo);
		if (!recursiveParents.isEmpty()) {
			sb.listener(new ConceptFilter(recursiveParents));
		}
		return sb.get();
	}
	
	public class ConceptFilter {
		final List<Long> _recursiveParents;
		public ConceptFilter(List<Long> recursiveParents) {
			_recursiveParents = recursiveParents;
		}
		
	}
}
