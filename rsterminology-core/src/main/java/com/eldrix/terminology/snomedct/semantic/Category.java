package com.eldrix.terminology.snomedct.semantic;

public enum Category {
	SNOMED_CT_ROOT(138875005L),
	DISEASE(64572001L),
	CLINICAL_FINDING(404684003L),
	LABORATORY_TEST(15220000L),
	PROCEDURE(71388002L),
	PHARMACEUTICAL_OR_BIOLOGICAL_PRODUCT(373873005L);
	public final long conceptId;
	Category(long conceptId) {
		this.conceptId = conceptId;
	}
}