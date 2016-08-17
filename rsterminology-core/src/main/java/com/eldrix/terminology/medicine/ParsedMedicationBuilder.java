package com.eldrix.terminology.medicine;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang3.text.StrMatcher;
import org.apache.commons.lang3.text.StrTokenizer;
import org.apache.lucene.index.CorruptIndexException;
import org.apache.lucene.queryparser.classic.ParseException;

import com.eldrix.terminology.medicine.Medication.Frequency;
import com.eldrix.terminology.medicine.Medication.Route;
import com.eldrix.terminology.medicine.Medication.Units;
import com.eldrix.terminology.snomedct.Concept;
import com.eldrix.terminology.snomedct.Search;
import com.eldrix.terminology.snomedct.Search.ResultItem;


/**
 * Builder for creating a ParsedMedication.
 *
 * This allows flexible creation of medications either
 * from parsing a string or from an existing Medication.
 *
 * It means that we can make ParsedMedication immutable which has a number
 * of advantages including safely using in a variety of collections.
 *
 * @author mark
 *
 */
public class ParsedMedicationBuilder {
	private final static String DOSING_PATTERN_STRING = "(\\p{Digit}+\\.?\\p{Digit}*)\\p{Space}*(mg|mcg|g|u|units|unit|t|tab|tablets|puffs|p|puff)";
	private final static Pattern DOSING_PATTERN = Pattern.compile(DOSING_PATTERN_STRING);
	private final static long MED_PARENT_CONCEPT_ID=373873005L;
	String _drugName;
	BigDecimal _dose;
	Units _units;
	Frequency _frequency;
	Route _route = Route.ORAL;
	boolean _asRequired = false;
	Long _conceptId;
	String _notes;

	public ParsedMedicationBuilder setDrugName(String drugName) {
		_drugName = drugName;
		return this;
	}

	public ParsedMedicationBuilder setDose(BigDecimal dose) {
		_dose = dose;
		return this;
	}

	public ParsedMedicationBuilder setUnits(Units units) {
		_units = units;
		return this;
	}

	public ParsedMedicationBuilder setFrequency(Frequency frequency) {
		_frequency = frequency;
		return this;
	}

	public ParsedMedicationBuilder setRoute(Route route) {
		_route = route;
		return this;
	}

	public ParsedMedicationBuilder setAsRequired(boolean asRequired) {
		_asRequired = asRequired;
		return this;
	}

	public ParsedMedicationBuilder setNotes(String notes) {
		_notes = notes;
		return this;
	}

	public ParsedMedicationBuilder setMedicationConceptId(long conceptId) {
		_conceptId = Long.valueOf(conceptId);
		return this;
	}

	public ParsedMedicationBuilder setMedicationConcept(Concept c) {
		_conceptId = c.getConceptId();
		_drugName = c.getPreferredDescription().getTerm();
		return this;
	}

	public ParsedMedicationBuilder fromParsedMedication(ParsedMedication pm) {
		_reset();
		_conceptId = pm.getConceptId();
		_drugName = pm.getDrugName();
		_dose = pm.getDose();
		_units = pm.getUnits();
		_frequency = pm.getFrequency();
		_route = pm.getRoute();
		_asRequired = pm.getAsRequired();
		_notes = pm.getNotes();
		return this;
	}

	public ParsedMedicationBuilder parseString(String s) {
		_reset();
		_parseString(this, s);
		return this;
	}

	private void _reset() {
		_conceptId = null;
		_drugName = null;
		_dose = null;
		_units = null;
		_frequency = null;
		_route = Route.ORAL;
		_asRequired = false;
		_notes = null;
	}
	
	/**
	 * Build a parsed medication, convenience method using a default SNOMED-CT index location.
	 * @return
	 * @throws CorruptIndexException
	 * @throws IOException
	 * @throws ParseException
	 */
	public ParsedMedication build() throws CorruptIndexException, IOException {
		return build(Search.getInstance());
	}
	
	/**
	 * Build a parsed medication, mapping drug name to a SNOMED-CT concept if it can be found using the supplied
	 * SNOMED-CT search.
	 * @return
	 * @throws ParseException 
	 * @throws IOException 
	 * @throws CorruptIndexException 
	 */
	public ParsedMedication build(Search search) throws CorruptIndexException, IOException {
		String mappedDrugName = null;
		if (_conceptId == null) {
			ResultItem ri = _searchForMedicationConcept(search, _drugName);
			if (ri != null) {
				_conceptId = ri.getConceptId();
				mappedDrugName = ri.getPreferredTerm();
			}
		}
		return new ParsedMedication(_conceptId,
				_drugName, mappedDrugName, _dose, _units, _frequency, _route, _asRequired, _notes);
	}

	static String[] _tokenizeString(String string) {
		final StrTokenizer _tokenizer = new StrTokenizer().
				setDelimiterMatcher(StrMatcher.trimMatcher()).
				setQuoteMatcher(StrMatcher.quoteMatcher()).
				setTrimmerMatcher(StrMatcher.trimMatcher()).
				setIgnoredMatcher(StrMatcher.quoteMatcher());
		_tokenizer.reset(string.toLowerCase());
		return _tokenizer.getTokenArray();
	}

	/**
	 * Parse a medication string of the format
	 * drug name (which may contain spaces and dose information)
	 * dose and units (e.g. 5mg) without whitespace
	 * frequency (e.g. tds) without whitespace
	 * route (e.g. po) without whitespace.
	 *
	 * For example,
	 * amlodipine 5mg od
	 *
	 * We track the first appropriate parsed token and then assume all tokens before that reflect the
	 * drug name.
	 * @param string
	 * @return
	 */
	static void _parseString(ParsedMedicationBuilder builder, String string) {
		String[] tokens = _tokenizeString(string);
		int numberTokens = tokens.length;
		boolean asRequired = false, _asRequired = false;
		Frequency frequency = null, _frequency = null;
		DosingToken dosing = null, _dosing = null;
		Route route = null, _route = null;
		StringBuilder drugName = new StringBuilder();
		StringBuilder notes = new StringBuilder();
		boolean makingDrugName = true;
		boolean makingNotes = false;
		for (int i=0; i < numberTokens; i++) {
			String token = tokens[i];
			if (makingNotes) {
				notes.append(token);
				notes.append(" ");
			} else {
				_asRequired = matchAsRequired(token);
				if (_asRequired) {
					asRequired = _asRequired;
					makingDrugName = false;
				}
				else {
					_frequency = matchFrequency(token);
					if (_frequency != null) {
						frequency = _frequency;
						makingDrugName = false;
					}
					else {
						_dosing = matchDosing(token);
						if (_dosing != null) {
							dosing = _dosing;
							makingDrugName = false;
						}
						else {
							_route = matchRoute(token);
							if (_route != null) {
								route = _route;
								makingDrugName = false;
							}
							else if (makingDrugName) {
								drugName.append(token);
								drugName.append(" ");
							}
							else {
								makingNotes = true;
								notes.append(token);
								notes.append(" ");
							}
						}
					}
				}
			}
		}
		builder._conceptId = null;
		builder._drugName = drugName.toString().trim();
		if (dosing != null) {
			builder._dose = dosing.dose();
			builder._units = dosing.units();
		}
		builder._frequency = frequency;
		builder._route = route;
		builder._asRequired = asRequired;
		builder._notes = notes.length() > 0 ? notes.toString().trim() : null;
	}

	/**
	 * Does the token matched the "as required" PRN instruction?
	 * @param token
	 * @return
	 */
	static boolean matchAsRequired(String token) {
		return "prn".equalsIgnoreCase(token);
	}
	static Frequency matchFrequency(String token) {
		return Frequency.frequencyWithName(token);
	}
	static DosingToken matchDosing(String token) {
		Matcher matcher = DOSING_PATTERN.matcher(token);
		if (matcher.matches()) {
			int count = matcher.groupCount();
			if (count == 2) {
				try {
					BigDecimal dose = new BigDecimal(matcher.group(1));
					Units units = Units.unitForAbbreviation(matcher.group(2));
					if (dose != null && units != null) {
						return new DosingToken(dose, units);
					}
				}
				catch(NumberFormatException e) {
					; //NOP
				}
			}
		}
		return null;
	}

	static Route matchRoute(String token) {
		return Route.routeForAbbreviation(token);
	}


	static class DosingToken {
		private final BigDecimal _dose;
		private final Units _units;
		DosingToken(BigDecimal dose, Units units) {
			if (dose == null || units == null) {
				throw new NullPointerException("Must have dose and units.");
			}
			_dose = dose;
			_units = units;
		}
		public BigDecimal dose() {
			return _dose;
		}
		public Units units() {
			return _units;
		}
		@Override public String toString() {
			return _dose.toPlainString() + _units.abbreviation();
		}
	}


	static ResultItem _searchForMedicationConcept(Search search, String drugName) throws CorruptIndexException, IOException {
		return _search(search, drugName, new long[]{MED_PARENT_CONCEPT_ID});
	}

	protected static ResultItem _search(Search search, String searchText, long[] parentConceptIds) throws CorruptIndexException, IOException {
		List<ResultItem> ris = search.newBuilder().searchFor(searchText).withRecursiveParent(parentConceptIds).setMaxHits(1).build().search();
		return ris.isEmpty() ? null : ris.get(0);
	}

}
