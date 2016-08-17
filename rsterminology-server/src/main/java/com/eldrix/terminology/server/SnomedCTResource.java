package com.eldrix.terminology.server;
import java.io.IOException;
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
import org.apache.cayenne.query.SelectQuery;
import org.apache.lucene.index.CorruptIndexException;
import org.apache.lucene.queryparser.classic.ParseException;

import com.eldrix.terminology.medicine.ParsedMedication;
import com.eldrix.terminology.medicine.ParsedMedicationBuilder;
import com.eldrix.terminology.snomedct.Concept;
import com.eldrix.terminology.snomedct.Description;
import com.eldrix.terminology.snomedct.Search;
import com.eldrix.terminology.snomedct.Search.ResultItem;
import com.nhl.link.rest.DataResponse;
import com.nhl.link.rest.LinkRest;
import com.nhl.link.rest.LinkRestException;
import com.nhl.link.rest.runtime.LinkRestRuntime;
import com.nhl.link.rest.runtime.cayenne.ICayennePersister;

@Path("snomedct")
@Produces(MediaType.APPLICATION_JSON)
public class SnomedCTResource {

	@Context
	private Configuration config;

	/**
	 * Return information about a specified concept.
	 * @param id
	 * @param uriInfo
	 * @return
	 */
	@GET
	@Path("concepts/{conceptId}")
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
	public DataResponse<ResultItem> search(@QueryParam("s") String search, 
			@DefaultValue("138875005") @QueryParam("root") final List<Long> recursiveParents, 
			@QueryParam("is") final List<Long> directParents, 
			@DefaultValue("200") @QueryParam("maxHits") int maxHits,
			@DefaultValue("0") @QueryParam("fsn") int includeFsn,
			@DefaultValue("0") @QueryParam("inactive") int includeInactive,
			@Context UriInfo uriInfo) {
		try {
			Search.Request.Builder b = Search.getInstance().newBuilder();
			b.searchFor(search)
				.setMaxHits(maxHits)
				.withRecursiveParent(recursiveParents);
			if (includeInactive == 0) {
				b.withActive();
			}
			if (includeFsn == 0) {
				b.withoutFullySpecifiedNames();
			}
			if (directParents.size() > 0) {
				b.withDirectParent(directParents);
			}
			List<ResultItem> result = b.build().search(); 
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
				b.withActive();
			}
			if (includeFsn == 0) {
				b.withoutFullySpecifiedNames();
			}
			List<Long> conceptIds = b.build().searchForConcepts();
			ICayennePersister cayenne = LinkRestRuntime.service(ICayennePersister.class, config);
			ObjectContext context = cayenne.newContext();
			SelectQuery<DataRow> select = SelectQuery.dataRowQuery(Description.class, Description.CONCEPT_ID.in(conceptIds));
			List<DataRow> data = select.select(context);
			List<String> result = data.stream().map(row -> (String) row.get(Description.TERM.getName())).collect(Collectors.toList());
			return DataResponse.forObjects(result);
		} catch (IOException e) {
			e.printStackTrace();	
			throw new LinkRestException(Status.INTERNAL_SERVER_ERROR, e.getLocalizedMessage(), e);
		}
	}

}
