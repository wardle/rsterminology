package com.eldrix.terminology.server;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Configuration;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.UriInfo;

import com.eldrix.terminology.snomedct.Project;
import com.nhl.link.rest.DataResponse;
import com.nhl.link.rest.LinkRest;

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
				.selectOne();
	}
	
	@GET
	public DataResponse<Project> getAll(@Context UriInfo uriInfo) {
		return LinkRest.select(Project.class, config).uri(uriInfo).select();
	}
	
}
