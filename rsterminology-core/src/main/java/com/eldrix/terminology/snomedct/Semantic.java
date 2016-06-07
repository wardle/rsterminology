package com.eldrix.terminology.snomedct;

import java.util.HashMap;

public class Semantic {

	public enum RelationType {
		IS_A(116680003L),
		HAS_ACTIVE_INGREDIENT(127489000L),
		HAS_AMP(10362701000001108L),
		HAS_ARP(12223201000001101L),
		HAS_BASIS_OF_STRENGTH(10363001000001101L),
		HAS_DISPENSED_DOSE_FORM(10362901000001105L),
		HAS_DOSE_FORM(411116001L),
		HAS_SPECIFIC_ACTIVE_INGREDIENT(10362801000001104L),
		HAS_TRADE_FAMILY_GROUP(9191701000001107L),
		HAS_VMP(10362601000001103L),
		VMP_NON_AVAILABILITY_INDICATOR(8940601000001102L),
		VMP_PRESCRIBING_STATUS(8940001000001105L),
		VRP_PRESCRIBING_STATUS(12223501000001103L);

		public final long conceptId;

		private final static HashMap<Long, RelationType> _lookup = new HashMap<Long, RelationType>();
		static {
			for (RelationType type : RelationType.values()) {
				_lookup.put(type.conceptId, type);
			}
		}
		RelationType(long conceptId) {
			this.conceptId = conceptId;
		}
		public static RelationType relationTypeForConceptId(long conceptId) {
			return _lookup.get(conceptId);
		}
	}
	
}
