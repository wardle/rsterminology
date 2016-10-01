package com.eldrix.terminology.server.resources;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
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
import org.apache.cayenne.query.Ordering;
import org.apache.cayenne.query.SelectQuery;
import org.apache.lucene.index.CorruptIndexException;
import org.apache.lucene.queryparser.classic.ParseException;

import com.eldrix.terminology.medicine.ParsedMedication;
import com.eldrix.terminology.medicine.ParsedMedicationBuilder;
import com.eldrix.terminology.server.resources.CrossMapResource.CrossMapTableFilter;
import com.eldrix.terminology.snomedct.Concept;
import com.eldrix.terminology.snomedct.CrossMapTable;
import com.eldrix.terminology.snomedct.CrossMapTarget;
import com.eldrix.terminology.snomedct.Description;
import com.eldrix.terminology.snomedct.Project;
import com.eldrix.terminology.snomedct.Search;
import com.eldrix.terminology.snomedct.Search.ResultItem;
import com.eldrix.terminology.snomedct.SearchUtilities;
import com.nhl.link.rest.DataResponse;
import com.nhl.link.rest.LinkRest;
import com.nhl.link.rest.LinkRestException;
import com.nhl.link.rest.SelectBuilder;
import com.nhl.link.rest.runtime.LinkRestRuntime;
import com.nhl.link.rest.runtime.cayenne.ICayennePersister;

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
				.selectOne();
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
		return sb.select();
	}
}
