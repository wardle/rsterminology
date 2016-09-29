package com.eldrix.terminology.server;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.cayenne.ObjectContext;
import org.apache.cayenne.exp.Expression;
import org.apache.cayenne.query.ObjectSelect;
import org.apache.cayenne.query.Ordering;

import com.eldrix.terminology.snomedct.Concept;
import com.eldrix.terminology.snomedct.CrossMapTable;
import com.eldrix.terminology.snomedct.CrossMapTarget;
import com.nhl.link.rest.DataResponse;
import com.nhl.link.rest.LinkRest;
import com.nhl.link.rest.SelectBuilder;
import com.nhl.link.rest.annotation.listener.QueryAssembled;
import com.nhl.link.rest.constraints.ConstraintsBuilder;
import com.nhl.link.rest.runtime.processor.select.SelectContext;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Configuration;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.UriInfo;

@Produces(MediaType.APPLICATION_JSON)
public class CrossMapSubResource {

	private Configuration _configuration;
	private long _conceptId;

	public CrossMapSubResource(Configuration configuration, long conceptId) {
		_configuration = configuration;
		_conceptId = conceptId;
	}
	
	@GET
	public DataResponse<CrossMapTable> getAll(@QueryParam("set") final Long crossMapSetId, @Context UriInfo uriInfo) {		
		SelectBuilder<CrossMapTable> sb = LinkRest.select(CrossMapTable.class, _configuration)
				.toManyParent(Concept.class, _conceptId, Concept.CROSS_MAPS)
				.uri(uriInfo);
		if (crossMapSetId != null) {
			sb.listener(new CrossMapTableFilter(crossMapSetId));
		}
		return sb.select();	
	}
	
	public class CrossMapTableFilter {
		private long _mapSetId;
		
		CrossMapTableFilter(long mapSetId) {
			_mapSetId = mapSetId;
		}
		
		@QueryAssembled
		public void queryAssembled(SelectContext<CrossMapTable> context) {
			context.getSelect().andQualifier(CrossMapTable.MAP_SET_ID.eq(_mapSetId));
		}
	}
	
	/*
		ICayennePersister cayenne = LinkRestRuntime.service(ICayennePersister.class, config);
		ObjectContext context = cayenne.newContext();
		Expression q1 = CrossMapTable.CONCEPT_ID.eq(conceptId);
		Expression q2 = CrossMapTable.MAP_SET_ID.eq(crossMapSetId);
		List<Ordering> orderings = Arrays.asList(CrossMapTable.OPTION.asc(), CrossMapTable.PRIORITY.asc());
		List<CrossMapTable> cmt = ObjectSelect.query(CrossMapTable.class, q1.andExp(q2), orderings).select(context);
		return DataResponse.forObjects(cmt);
		*/
	
}
