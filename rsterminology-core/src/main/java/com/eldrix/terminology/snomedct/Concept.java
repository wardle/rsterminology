package com.eldrix.terminology.snomedct;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.cayenne.exp.Expression;
import org.apache.cayenne.exp.Property;
import org.apache.cayenne.validation.BeanValidationFailure;
import org.apache.cayenne.validation.ValidationResult;

import com.eldrix.terminology.snomedct.Description.Type;
import com.eldrix.terminology.snomedct.auto._Concept;
import com.eldrix.terminology.snomedct.semantic.RelationType;

/**
 * A SNOMED CT clinical concept.
 * A concept is described by multiple descriptions.
 *
 * ConceptID: the unique SNOMED CT identifier for this concept.
 * ConceptStatus: status, whether in active use or not, and if not, indicates reason why withdrawn.
 * FullySpecifiedName: unique phrase describing concept (unambiguously).
 * CTV3ID: Read code for this concept.
 * SNOMEDID: SNOMED identifier for this concept.
 * isPrimitive: indicates whether concept is  primitive or fully defined by its current set of defining characteristics.
 *
 * Concept status:
 * 0: current (considered active)
 * 1: Retired (considered inactive)
 * 2: Duplicate (considered inactive)
 * 3: Outdated (considered inactive)
 * 4: Ambiguous (considered inactive)
 * 5: Erroneous (considered inactive)
 * 6: Limited (considered active)
 * 10: Moved elsewhere (considered inactive)
 * 11: Pending move (considered active)
 *
 *
 * @author Mark Wardle
 */
public class Concept extends _Concept {
	private static final long serialVersionUID = 1L;
	private static List<Locale.LanguageRange> _systemLocale = Locale.LanguageRange.parse(Locale.getDefault().toLanguageTag());

	volatile private Description _preferredDescription;
	volatile private Set<Long> _cachedRecursiveParents;

	public static final Property<Description> PREFERRED_DESCRIPTION = new Property<Description>("preferredDescription");

	public enum Status {
		CURRENT(0, "Current", true),
		RETIRED(1, "Retired", false),
		DUPLICATE(2, "Duplicate", false),
		OUTDATED(3, "Outdated", false),
		AMBIGUOUS(4, "Ambiguous", false),
		ERRONEOUS(5, "Erroneous", false),
		LIMITED(6, "Limited", true),
		MOVED_ELSEWHERE(10, "Moved elsewhere", false),
		PENDING_MOVE(11, "Pending move", true);

		private static final Map<Integer, Status> _lookup;
		private static List<Integer> activeCodes = new ArrayList<>();
		static {
			_lookup = new HashMap<Integer, Status>();
			for (Status st : Status.values()) {
				_lookup.put(st.code, st);
				if (st.isActive) {
					activeCodes.add(st.code);
				}
			}
		};
		int code;
		String title;
		boolean isActive;
		Status(int code, String title, boolean isActive) {
			this.code = code;
			this.title = title;
			this.isActive = isActive;
		}		
		public static List<Integer> activeCodes() {
			return activeCodes;
		}
	}

	public Optional<Status> getStatus() {
		return Optional.ofNullable(Status._lookup.get(super.getConceptStatusCode()));
	}

	/**
	 * Is this concept active? For most use-cases, only active concepts should be used.
	 * @return
	 */
	public boolean isActive() {
		return getStatus().orElse(Status.ERRONEOUS).isActive;
	}

	/**
	 * Return the preferred description for this concept based on the current locale.
	 * @return
	 */
	public Description getPreferredDescription() {
		Description result = _preferredDescription;
		if (result == null) {
			synchronized(this) {
				result = _preferredDescription;
				if (result == null) {
					_preferredDescription = getPreferredDescription(_systemLocale).orElseGet( () -> {
						return getDescriptions().size() > 0 ? getDescriptions().get(0) : null;
					});
					if (_preferredDescription == null) {
						throw new IllegalStateException("No descriptions found for concept: " + getConceptId());
					}
				}
			}
		}
		return _preferredDescription;
	}

	/**
	 * Return the preferred description for the given locale(s).
	 * @param preferredLocales - string of the format specified in {@link java.util.Locale.LanguageRange}
	 * @return
	 */
	public Optional<Description> getPreferredDescription(List<Locale.LanguageRange> preferredLocales) {
		ArrayList<Description> preferred = new ArrayList<>();
		ArrayList<Description> fallback1 = new ArrayList<>();
		ArrayList<Description> fallback2 = new ArrayList<>();
		for (Description d: getDescriptions()) {
			if (d.isPreferred()) {
				preferred.add(d);
			} else if (d.isActive() && d.getType().orElse(Type.UNSPECIFIED) == Type.SYNONYM) {
				fallback1.add(d);
			} else {
				fallback2.add(d);
			}
		}
		return Optional.ofNullable(_findDescriptionMatchingLocale(preferred, preferredLocales)
				.orElse(_findDescriptionMatchingLocale(fallback1, preferredLocales)
						.orElse(_findDescriptionMatchingLocale(fallback2, preferredLocales).orElse(null))));
	}

	public Optional<Description> getPreferredDescription(String preferredLocales) {
		return getPreferredDescription(Locale.LanguageRange.parse(preferredLocales));
	}
	
	/**
	 * Find a matching description from the list of descriptions best matching the locale preferences.
	 * The format of the preferredLocales is the same as that in {@link java.util.Locale.LanguageRange}
	 * @see java.util.Locale.LanguageRange
	 * @see java.util.Locale
	 */
	private static Optional<Description> _findDescriptionMatchingLocale(List<Description> descriptions, List<Locale.LanguageRange> preferred) {
		List<String> tags = descriptions.stream().map(d -> d.getLanguageCode()).collect(Collectors.toList());
		String result = Locale.lookupTag(preferred, tags);
		if (result != null) { 
			return descriptions.stream().filter(d -> d.getLanguageCode().equalsIgnoreCase(result)).findFirst();
		}
		return Optional.empty();
	}

	/**
	 * Return the relationships for this concept of the specified type.
	 * Note: this returns the "parent" relationships - those in which this concept
	 * is the "source" and another is a "target".
	 * From a semantic point-of-view, parent relationships tell you the most about this concept.
	 * @param conceptId
	 * @return
	 */
	public List<Relationship> getParentRelationshipsOfType(long conceptId) {
		Expression qual = Relationship.RELATIONSHIP_TYPE_CONCEPT.dot(Concept.CONCEPT_ID).eq(conceptId);
		return qual.filterObjects(getParentRelationships());
	}

	/**
	 * Return the relationships for this concept of the specified type.
	 * @param type
	 * @return
	 */
	public List<Relationship> getParentRelationshipsOfType(RelationType type) {
		return getParentRelationshipsOfType(type.conceptId);
	}


	/**
	 * Is this concept a type of the specified concept?
	 * @param c
	 * @return
	 */
	public boolean isAConcept(Concept c) {
		return isAConcept(c.getConceptId());
	}

	/**
	 * Is this concept a type of the specified concept?
	 * @param c
	 * @return
	 */
	public boolean isAConcept(long conceptId) {
		if (this.getConceptId() == conceptId) {
			return true;
		}
		return getCachedRecursiveParents().contains(conceptId);
	}

	/**
	 * Is this concept a type of one of the specified concepts?
	 * @param conceptIds
	 * @return
	 */
	public boolean isAConcept(long[] conceptIds) {
		for (long conceptId : conceptIds) {
			if (this.getConceptId() == conceptId) {
				return true;
			}
			if (getCachedRecursiveParents().contains(conceptId)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Return parent concepts using IS-A relationships
	 * @return
	 */
	public List<Concept> getParentConcepts() {
		return getParentRelationships().stream()
				.filter(r -> r.getRelationshipTypeConcept().getConceptId() == RelationType.IS_A.conceptId)
				.map(Relationship::getTargetConcept)
				.collect(Collectors.collectingAndThen(Collectors.toList(), Collections::unmodifiableList));
	}

	/**
	 * Return child concepts using IS-A relationships.
	 * @return
	 */
	public List<Concept> getChildConcepts() {
		return getChildRelationships().stream()
				.filter(r -> r.getRelationshipTypeConcept().getConceptId() == RelationType.IS_A.conceptId)
				.map(Relationship::getSourceConcept)
				.collect(Collectors.collectingAndThen(Collectors.toList(), Collections::unmodifiableList));
	}

	/**
	 * This determines a list of concept identifiers that are the recursive parents
	 * of this concept.
	 * @return
	 */
	public Set<Long> getCachedRecursiveParents() {
		Set<Long> result = _cachedRecursiveParents;
		if (result == null) {
			synchronized(this) {
				result = _cachedRecursiveParents;
				if (result == null) {
					_cachedRecursiveParents = result = _getRecursiveParentsFromDatabaseCache();
				}
			}
		}
		return result;
	}

	public void clearCachedRecursiveParents() {
		if (_cachedRecursiveParents != null) {
			synchronized(this) {
				if (_cachedRecursiveParents != null) {
					_cachedRecursiveParents = null;
				}
			}			
		}
	}

	/**
	 * Determine recursive parents from a fetch the result of which is cached on a per-concept
	 * basis.
	 * @return
	 */
	@SuppressWarnings("unused")
	private Set<Long> _getRecursiveParentsFromFetch() {
		Set<Long> parents = new HashSet<>(ParentCache.fetchRecursiveParentsForConcept(getObjectContext(), getConceptId()));
		return Collections.unmodifiableSet(parents);
	}

	/**
	 * Determine recursive parents from the database table that acts as a cache.
	 * @return
	 */
	private Set<Long> _getRecursiveParentsFromDatabaseCache() {
		return getRecursiveParentConcepts().stream()
			.map(Concept::getConceptId)
			.collect(Collectors.collectingAndThen(Collectors.toSet(), Collections::unmodifiableSet));
	}

	@Override
	protected void validateForSave(ValidationResult validationResult) {
		super.validateForSave(validationResult);
		if (new SnomedCtIdentifier(getConceptId()).isValidConcept() == false) {
			validationResult.addFailure(new BeanValidationFailure(this, Concept.CONCEPT_ID.getName(), "Invalid concept identifier"));
		}
	}


}
