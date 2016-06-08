package com.eldrix.terminology.snomedct;

import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.cayenne.exp.Expression;

import com.eldrix.terminology.snomedct.Semantic.RelationType;
import com.eldrix.terminology.snomedct.auto._Concept;
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
	private Description _preferredDescription;
	private Set<Long> _cachedRecursiveParents;

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

		static final Map<Integer, Status> _lookup = new HashMap<Integer, Status>();
		static {
			for (Status st : EnumSet.allOf(Status.class)) {
				_lookup.put(st.code, st);
			}
		};
		int code;
		String title;
		boolean active;
		Status(int code, String title, boolean active) {
			this.code = code;
			this.title = title;
			this.active = active;
		}
	}

	public Status getStatus() {
		return Status._lookup.get(super.getConceptStatusCode());
	}

	/**
	 * Is this concept active? For most use-cases, only active concepts should be used.
	 * @return
	 */
	public boolean isActive() {
		return getStatus().active;
	}

	/**
	 * Return the preferred description for this concept.
	 * TODO: This should take into account the language preferences.
	 * @return
	 */
	public Description getPreferredDescription() {
		if (_preferredDescription == null) {
			List<Description> all = getDescriptions();
			List<Description> descs = Description.QUALIFIER_FOR_PREFERRED.filterObjects(all);
			if (descs.size() == 0) {
				descs = Description.QUALIFIER_FALLBACK_PREFERRED_DESCRIPTION.filterObjects(all);
			}
			if (descs.size() == 0) {
				descs = Description.QUALIFIER_FSN_DESCRIPTION.filterObjects(all);
			}
			if (descs.size() == 0) {
				descs = all;
			}
			if (descs.size() > 0) {
				_preferredDescription = descs.get(0);
			}
			else {
				throw new IllegalStateException("No descriptions found for concept");
			}
		}
		return _preferredDescription;
	}

	/**
	 * Return the relationships for this concept of the specified type.
	 * Note: this returns the "parent" relationships - those in which this concept
	 * is the "source" and another is a "target".
	 * From a semantic point-of-view, parent relationships tell you the most about this concept.
	 * @param conceptId
	 * @return
	 */
	public List<Relationship> getRelationshipsOfType(long conceptId) {
		Expression qual = Relationship.RELATIONSHIP_TYPE_CONCEPT.dot(Concept.CONCEPT_ID).eq(conceptId);
		return qual.filterObjects(getParentRelationships());
	}

	/**
	 * Return the relationships for this concept of the specified type.
	 * @param type
	 * @return
	 */
	public List<Relationship> getRelationshipsOfType(RelationType type) {
		return getRelationshipsOfType(type.conceptId);
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
	 * This determines a list of concept identifiers that are the recursive parents
	 * of this concept.
	 * @return
	 */
	public Set<Long> getCachedRecursiveParents() {
		if (_cachedRecursiveParents == null) {
			synchronized(this) {
				if (_cachedRecursiveParents == null) {
					HashSet<Long> parents = new HashSet<Long>();
					getParentConcepts().forEach(c -> {
						parents.add(c.getConceptId());
					});
					_cachedRecursiveParents = Collections.unmodifiableSet(parents);
				}
			}
		}
		return _cachedRecursiveParents;
	}


}
