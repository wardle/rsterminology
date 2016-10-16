package com.eldrix.terminology.snomedct.parse;

import java.io.FileReader;
import java.io.IOException;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Optional;

import org.apache.cayenne.ObjectContext;
import org.apache.cayenne.configuration.server.ServerRuntime;
import org.apache.cayenne.exp.Expression;
import org.apache.cayenne.query.ObjectSelect;
import org.apache.cayenne.query.SelectById;

import com.eldrix.terminology.snomedct.Concept;
import com.eldrix.terminology.snomedct.CrossMapSet;
import com.eldrix.terminology.snomedct.CrossMapTable;
import com.eldrix.terminology.snomedct.CrossMapTarget;
import com.eldrix.terminology.snomedct.Description;
import com.eldrix.terminology.snomedct.Relationship;
import com.opencsv.CSVParser;
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
			new CrossMapSetParser(),
			new CrossMapTableParser(),
			new CrossMapTargetParser(),
	};

	public static void processFile(ServerRuntime runtime, String file) throws IOException {
		try (CSVReader reader = new CSVReader(new FileReader(file), '\t', CSVParser.NULL_CHARACTER, false)) {
			String[] csv;
			int i = 0;
			long total = 0;
			ObjectContext context = runtime.newContext();
			String[] header = reader.readNext();			// get header and check it is valid
			Optional<Rf1FileParser> parser = Arrays.stream(parsers)
					.filter(p -> p.canParse(header))
					.findFirst();
			if (parser.isPresent()) {
				Rf1FileParser p = parser.get();
				String entityName = context.getEntityResolver().getObjEntity(p.getEntityClass()).getName();
				System.out.println("Processing SNOMED RF-1 file. Type:" + entityName);
				while ((csv = reader.readNext()) != null) {
					try {
						p.createOrUpdate(context, csv);
						context.commitChanges();
					} catch (Exception e) {
						System.err.println("Error: couldn't import: " + Arrays.toString(csv));
						e.printStackTrace();
					}
					i++;
					total++;
					if (i == BATCH_SIZE) {
						System.out.print("\rProcessed " + total);
						i = 0;
						context = runtime.newContext();
					}
				}
				System.out.println("\nProcessed " + total + "... complete.");
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

	interface FileFormatChecker {

		boolean canParse(String[] header);

	}

	static class SimpleFileFormatChecker implements FileFormatChecker {
		final String _firstColumnName;
		final int _numberColumns;
		public SimpleFileFormatChecker(String firstColumnName, int numberOfColumns) {
			_firstColumnName = firstColumnName;
			_numberColumns = numberOfColumns;
		}
		@Override
		public boolean canParse(String[] header) {
			return header.length == _numberColumns && _firstColumnName.equals(header[0]);
		}
	}

	static class AllColumnFileFormatChecker implements FileFormatChecker {
		final String[] _columnNames;
		public AllColumnFileFormatChecker(String[] columns) {
			_columnNames = columns;
		}

		@Override
		public boolean canParse(String[] header) {
			int length = _columnNames.length;
			if (length == header.length) {
				for (int i=0; i<length; i++) {
					if (header[i].equals(_columnNames[i]) == false) {
						return false;
					}
				}
				return true;
			}
			return false;
		}
	}

	abstract static class DefaultRf1FileParser<T> implements Rf1FileParser {
		final Class<T> _clazz;
		final FileFormatChecker _checker;
		final Date _dateCreated;
		DefaultRf1FileParser(Class<T> clazz, FileFormatChecker formatChecker) {
			_checker = formatChecker;
			_clazz = clazz;
			_dateCreated = new Date();
		}

		@Override
		public boolean canParse(String[] header) {
			return _checker.canParse(header);
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
			super(Concept.class, new SimpleFileFormatChecker("CONCEPTID", 6));
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
			super(Description.class, new SimpleFileFormatChecker("DESCRIPTIONID", 7));
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
			super(Relationship.class, new SimpleFileFormatChecker("RELATIONSHIPID", 7));
		}
		@Override
		void update(Relationship r, String[] csv) {
			ObjectContext context = r.getObjectContext();
			r.setDateUpdated(_dateCreated);
			r.setRelationshipId(relationshipId(csv));
			r.setCharacteristicType(characteristicType(csv));
			r.setRefinability(refinability(csv));
			r.setRelationshipGroup(relationshipGroup(csv));
			Concept type = ObjectSelect.query(Concept.class, Concept.CONCEPT_ID.eq(relationTypeConceptId(csv))).selectOne(context);
			Concept source = ObjectSelect.query(Concept.class, Concept.CONCEPT_ID.eq(sourceConceptId(csv))).selectOne(context);
			Concept target = ObjectSelect.query(Concept.class, Concept.CONCEPT_ID.eq(targetConceptId(csv))).selectOne(context);
			if (type != null && source != null && target != null) {
				r.setRelationshipTypeConcept(type);
				r.setSourceConcept(source);
				r.setTargetConcept(target);
			}
			else {
				System.err.println("Could not import relationship " + relationshipId(csv) + 
						": One or more concepts not found. Source:" + source + 
						" Type:" + type + " Target:" + target);
				context.deleteObject(r);
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


	static class CrossMapSetParser extends DefaultRf1FileParser<CrossMapSet> {
		final static String[] columns = new String[] {
				"MAPSETID",
				"MAPSETNAME",
				"MAPSETTYPE",
				"MAPSETSCHEMEID",
				"MAPSETSCHEMENAME",
				"MAPSETSCHEMEVERSION",
				"MAPSETREALMID",
				"MAPSETSEPARATOR",
				"MAPSETRULETYPE"
		};
		public CrossMapSetParser() {
			super(CrossMapSet.class, new AllColumnFileFormatChecker(columns));
		}
		@Override
		void update(CrossMapSet o, String[] csv) {
			o.setSetId(id(csv));
			o.setName(name(csv));
			o.setType(type(csv));
			o.setSchemeId(schemeId(csv));
			o.setSchemeName(schemeName(csv));
			o.setSchemeVersion(schemeVersion(csv));
			o.setRealmId(realmId(csv));
			o.setSeparator(separator(csv));
			o.setRuleType(ruleType(csv));
		}

		Long id(String[] csv) {
			return Long.parseLong(csv[0]);
		}
		String name(String[] csv) {
			return csv[1];
		}
		int type(String[] csv) {
			return Integer.parseInt(csv[2]);
		}
		String schemeId(String[] csv) {
			return csv[3];
		}
		String schemeName(String[] csv) {
			return csv[4];
		}
		String schemeVersion(String[] csv) {
			return csv[5];
		}
		String realmId(String[] csv) {
			return csv[6];
		}
		String separator(String[] csv) {
			return csv[7];
		}
		int ruleType(String[] csv) {
			return Integer.parseInt(csv[8]);
		}
	}

	static class CrossMapTargetParser extends DefaultRf1FileParser<CrossMapTarget> {

		final static String[] columns = new String[] {
				"TARGETID",
				"TARGETSCHEMEID",
				"TARGETCODES",
				"TARGETRULE",
				"TARGETADVICE"
		};

		public CrossMapTargetParser() {
			super(CrossMapTarget.class, new AllColumnFileFormatChecker(columns));
		}

		@Override
		void update(CrossMapTarget o, String[] csv) {
			o.setTargetId(targetId(csv));
			o.setSchemeId(schemeId(csv));
			o.setCodes(targetCodes(csv));
			o.setRule(targetRules(csv));
			o.setAdvice(targetAdvice(csv));
		}	
		Long targetId(String[] csv) {
			return Long.parseLong(csv[0]);
		}
		String schemeId(String[] csv) {
			return csv[1];
		}
		String targetCodes(String[] csv) {
			return csv[2];
		}
		String targetRules(String[] csv) {
			return csv[3];
		}
		String targetAdvice(String[] csv) {
			return csv[4];
		}

	}

	static class CrossMapTableParser implements Rf1FileParser {
		final static String[] columns = new String[] {
				"MAPSETID",
				"MAPCONCEPTID",
				"MAPOPTION",
				"MAPPRIORITY",
				"MAPTARGETID",
				"MAPRULE",
				"MAPADVICE",
		};
		final static FileFormatChecker checker = new AllColumnFileFormatChecker(columns);
		final Date _dateCreated = new Date();

		@Override
		public boolean canParse(String[] header) {
			return checker.canParse(header);
		}

		@Override
		public void createOrUpdate(ObjectContext context, String[] data) {
			CrossMapSet set = ObjectSelect.query(CrossMapSet.class, CrossMapSet.SET_ID.eq(mapSetId(data))).selectOne(context);
			Concept concept = ObjectSelect.query(Concept.class, Concept.CONCEPT_ID.eq(conceptId(data))).selectOne(context);
			CrossMapTarget target = ObjectSelect.query(CrossMapTarget.class, CrossMapTarget.TARGET_ID.eq(mapTargetId(data))).selectOne(context);
			if (set != null && concept != null && target != null) {
				Expression qual = CrossMapTable.SET.eq(set).andExp(CrossMapTable.CONCEPT.eq(concept).andExp(CrossMapTable.TARGET.eq(target)));
				List<CrossMapTable> results = ObjectSelect.query(CrossMapTable.class, qual).select(context);
				CrossMapTable cmt = null;
				if (results.size() > 1) {
					System.err.println("Found duplicate rows matching setId: " + mapSetId(data) + " conceptId:" + conceptId(data) + " targetId:" + mapTargetId(data));
					context.deleteObjects(results);
				} else if (results.size() == 1) {
					cmt = results.get(0);
				}
				if (cmt == null) {
					cmt = context.newObject(CrossMapTable.class);
				}
				cmt.setSet(set);
				cmt.setMapSetId(set.getSetId());
				cmt.setConcept(concept);
				cmt.setConceptId(concept.getConceptId());
				cmt.setTarget(target);
				cmt.setTargetId(target.getTargetId());
				cmt.setOption(mapOption(data));
				cmt.setPriority(mapPriority(data));
				cmt.setRule(mapRule(data));
				cmt.setAdvice(mapAdvice(data));
				cmt.setDateUpdated(_dateCreated);
			}
			else {
				System.err.println("Could not identify set, concept or target: " + data);
			}
		}


		@Override
		public Class<?> getEntityClass() {
			return CrossMapTable.class;
		}

		Long mapSetId(String[] csv) {
			return Long.parseLong(csv[0]);
		}
		Long conceptId(String[] csv) {
			return Long.parseLong(csv[1]);
		}
		Integer mapOption(String[] csv) {
			return Integer.parseInt(csv[2]);
		}
		Integer mapPriority(String[] csv) {
			return Integer.parseInt(csv[3]);
		}
		Long mapTargetId(String[] csv) {
			return Long.parseLong(csv[4]);
		}
		String mapRule(String[] csv) {
			return csv[5]; 
		}
		String mapAdvice(String[] csv) {
			return csv[6];
		}
	}


}
