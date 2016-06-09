package com.eldrix.terminology.snomedct;

/**
 * A SNOMED CT identifier (SCTID).
 * 
 * This is a utility class providing support for querying and understanding SNOMED CT identifiers.
 * http://vtsl.vetmed.vt.edu/Education/Documentation/SNOMED/SNOMED_CT_Technical_Reference_Guide_20090131-1.pdf
 * @author Mark Wardle
 *
 */
public class SnomedCtIdentifier {
	private String _id;

	
	public enum Type {
		CONCEPT(0),
		DESCRIPTION(1),
		RELATIONSHIP(2);
		public final int id;
		Type(int id) {
			this.id = id;
		}
	}


	/**
	 * Create a SNOMED CT identifier using the given number.
	 * @param id
	 */
	public SnomedCtIdentifier(long id) {
		_id = Long.toString(id);
	}

	private boolean _isValid() {
		return VerhoeffDihedral.validateVerhoeff(_id);
	}
	
	/**
	 * Is this identifier a valid concept identifier?
	 * @return
	 */
	public boolean isValidConcept() {
		return _isValid() ? Type.CONCEPT == partitionIdentifierType() : false;
	}

	/**
	 * Is this identifier a valid description identifier?
	 * @return
	 */
	public boolean isValidDescription() {
		return _isValid() ? Type.DESCRIPTION == partitionIdentifierType() : false;
	}

	/**
	 * Is this identifier a valid relationship identifier?
	 * @return
	 */
	public boolean isValidRelationship() {
		return _isValid() ? Type.RELATIONSHIP == partitionIdentifierType() : false;
	}
	
	
	/**
	 * Returns the first of the partition identifiers for this SCTID.
	 * This is defined at the "release".
	 * 0 - international.
	 * 1 - part of an extension set.
	 * @return
	 */
	public int releaseIdentifier() {
		int len = _id.length();
		return Integer.parseInt(_id.substring(len-3, len-2));
	}

	public int partitionIdentifier() {
		int len = _id.length();
		return Integer.valueOf(_id.substring(len-2, len-1));
	}
	
	public Type partitionIdentifierType() {
		int id = partitionIdentifier();
		switch(id) {
		case 0:
			return Type.CONCEPT;
		case 1: 
			return Type.DESCRIPTION;
		case 2:
			return Type.RELATIONSHIP;
		}
		return null;
	}
	
	public long namespaceIdentifier() {
		if (releaseIdentifier() == 1) {
			int len = _id.length();
			return Long.valueOf(_id.substring(len-10, len-3));
		}
		return 0;
	}
}
