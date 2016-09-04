package com.eldrix.terminology.snomedct.parse;

import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Date;
import java.util.List;

import org.apache.cayenne.ObjectContext;
import org.apache.cayenne.configuration.server.ServerRuntime;
import org.apache.cayenne.query.ObjectSelect;
import org.apache.cayenne.query.SelectById;

import com.eldrix.terminology.snomedct.Concept;
import com.eldrix.terminology.snomedct.Description;
import com.eldrix.terminology.snomedct.Relationship;
import com.opencsv.CSVReader;

/**
 * Import data from a "release format 1" file.
 * @author mark
 *
 */
public class ParseRf1 {
	private static final int BATCH_SIZE = 500;

	static Rf1FileParser[] parsers = new Rf1FileParser[] {
			new ConceptRf1Parser(),
			new DescriptionRf1Parser(),
			new RelationshipRf1Parser(),
	};

	public static void processFile(ServerRuntime runtime, String file) throws IOException {
		long total = Files.lines(Paths.get(file)).count();
		try (CSVReader reader = new CSVReader(new FileReader(file), '\t')) {
			String[] csv;
			int i = 0;
			ObjectContext context = runtime.newContext();
			String[] header = reader.readNext();			// get header and check it is valid
			Rf1FileParser parser = null;
			for (Rf1FileParser p : parsers) {
				if (p.canParse(header)) {
					parser = p;
				}
			}
			if (parser != null) {
				String entityName = context.getEntityResolver().getObjEntity(parser.getEntityClass()).getName();
				System.out.println("Processing SNOMED RF-1 file. Type:" + entityName);
				while ((csv = reader.readNext()) != null) {
					parser.createOrUpdate(context, csv);
					i++;
					if (i == BATCH_SIZE) {
						i = 0;
						context.commitChanges();
						context = runtime.newContext();
					}
				}
			}
			else {
				System.err.println("Unknown file format.");
			}
		}
	}

	interface Rf1FileParser {

		/**
		 * Can this parser parse a file with this header?
		 * @param header
		 * @return
		 */
		boolean canParse(String[] header);

		/**
		 * Create or update an object using the data specified.
		 * @param context
		 * @param data
		 */
		void createOrUpdate(ObjectContext context, String[] data);

		/**
		 * Get entity class for this parser.
		 * @return
		 */
		Class<?> getEntityClass();
	}

	abstract static class DefaultRf1FileParser<T> implements Rf1FileParser {
		final Class<T> _clazz;
		final String _firstColumnName;
		final int _numberColumns;
		final Date _dateCreated;
		DefaultRf1FileParser(Class<T> clazz, String firstColumnName, int numberColumns) {
			_firstColumnName = firstColumnName;
			_numberColumns = numberColumns;
			_clazz = clazz;
			_dateCreated = new Date();
		}

		@Override
		public boolean canParse(String[] header) {
			return header.length == _numberColumns && _firstColumnName.equals(header[0]);
		}

		@Override
		public void createOrUpdate(ObjectContext context, String[] data) {
			long id = Long.parseLong(data[0]);

			T o = SelectById.query(_clazz, id).selectOne(context);
			if (o == null) {
				o = context.newObject(_clazz);
			}
			update(o, data);
		}

		@Override
		public Class<?> getEntityClass() {
			return _clazz;
		}

		abstract void update(T o, String[] data);
	}



	/**
	 * A file parser that will import SNOMED-CT concept files in format RF1.
	 * @author mark
	 *
	 */
	static class ConceptRf1Parser extends DefaultRf1FileParser<Concept> {

		ConceptRf1Parser() {
			super(Concept.class, "CONCEPTID", 6);
		}

		@Override
		void update(Concept c, String[] csv) {
			c.setConceptId(conceptId(csv));
			c.setConceptStatusCode(conceptStatus(csv));
			c.setCtvId(ctv3Id(csv));
			c.setFullySpecifiedName(fullySpecifiedName(csv));
			c.setIsPrimitive(isPrimitive(csv));
			c.setSnomedId(snomedId(csv));			
		}

		static Long conceptId(String[] csv) {			
			return Long.parseLong(csv[0]);
		}
		static int conceptStatus(String[] csv) {	
			return Integer.parseInt(csv[1]);		
		}
		static String fullySpecifiedName(String[] csv) {
			return csv[2];		
		}
		static String ctv3Id(String[] csv) {			
			return csv[3];		
		}
		static String snomedId(String[] csv) {			
			return csv[4];		
		}
		static int isPrimitive(String []csv) {		
			return Integer.parseInt(csv[5]);	
		}


	}

	static class DescriptionRf1Parser extends DefaultRf1FileParser<Description> {

		DescriptionRf1Parser() {
			super(Description.class, "DESCRIPTIONID", 7);
		}

		@Override
		void update(Description d, String[] csv) {
			d.setDescriptionId(descriptionId(csv));
			ObjectSelect<Concept> query = ObjectSelect.query(Concept.class, Concept.CONCEPT_ID.eq(conceptId(csv)));
			Concept c = query.selectOne(d.getObjectContext());
			if (c != null ) {
				d.setConcept(c);
				d.setDescriptionStatusCode(descriptionStatus(csv));
				d.setDescriptionTypeCode(descriptionType(csv));
				d.setInitialCapitalStatus(initialCapitalStatus(csv));
				d.setLanguageCode(languageCode(csv));
				d.setTerm(term(csv));
			}
			else {
				System.err.println("No concept found for description : "+descriptionId(csv) + ":" + term(csv) + ". ConceptId=" + conceptId(csv));
				d.getObjectContext().deleteObject(d);
			}
		}

		static Long descriptionId(String[] csv) {			
			return Long.parseLong(csv[0]);		
		}
		static int descriptionStatus(String[] csv) {		
			return Integer.parseInt(csv[1]);	
		}
		static Long conceptId(String[] csv) {				
			return Long.parseLong(csv[2]);		
		}
		static String term(String[] csv) {					
			return csv[3];	
		}
		static String initialCapitalStatus(String[] csv) {	
			return csv[4];		
		}
		static int descriptionType(String[] csv) {			
			return Integer.parseInt(csv[5]);		
		}
		static String languageCode(String[] csv) {			
			return csv[6];		
		}
	}

	static class RelationshipRf1Parser extends DefaultRf1FileParser<Relationship> {

		RelationshipRf1Parser() {
			super(Relationship.class, "RELATIONSHIPID", 7);
		}
		@Override
		void update(Relationship r, String[] csv) {
			r.setDateUpdated(_dateCreated);
			r.setRelationshipId(relationshipId(csv));
			r.setCharacteristicType(characteristicType(csv));
			r.setRefinability(refinability(csv));
			r.setRelationshipGroup(relationshipGroup(csv));
			List<Concept> concepts = ObjectSelect.query(Concept.class, Concept.CONCEPT_ID.in(relationTypeConceptId(csv), sourceConceptId(csv), targetConceptId(csv))).select(r.getObjectContext());
			if (concepts.size() == 3) {
				r.setRelationshipTypeConcept(concepts.get(0));
				r.setSourceConcept(concepts.get(1));
				r.setTargetConcept(concepts.get(2));
			}
			else {
				System.err.println("Could not import relationship " + relationshipId(csv) + ": One or more concepts not found");
				r.getObjectContext().deleteObject(r);
			}
		}
		Long relationshipId(String[] csv) {		
			return Long.parseLong(csv[0]); 			
		}
		Long sourceConceptId(String[] csv) {			
			return Long.parseLong(csv[1]); 		
		}
		Long relationTypeConceptId(String[] csv) {	
			return Long.parseLong(csv[2]);		
		}
		Long targetConceptId(String[] csv) {		
			return Long.parseLong(csv[3]);		
		}
		int characteristicType(String[] csv) {	
			return Integer.parseInt(csv[4]);		
		}
		int refinability(String[] csv) {			
			return Integer.parseInt(csv[5]);		
		}
		String relationshipGroup(String[] csv) {		
			return csv[6];		
		}

	}

}
