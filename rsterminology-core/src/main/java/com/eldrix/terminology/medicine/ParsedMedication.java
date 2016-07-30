package com.eldrix.terminology.medicine;

import java.math.BigDecimal;

import com.eldrix.terminology.medicine.Medication.Frequency;
import com.eldrix.terminology.medicine.Medication.Route;
import com.eldrix.terminology.medicine.Medication.Units;

/**
 * A medication representing the name, dosing, frequency and other attributes.
 *
 * Equality checks compare the core attributes which should allow
 * appropriate round-tripping when a medication is converted into a string
 * and then re-parsed. This allows a list of medications to be used in NSSet
 * and thus allow intersections and other logic.
 *
 * @author mark
 *
 */
public class ParsedMedication implements Comparable<ParsedMedication> {

	final String _drugName;
	final String _mappedDrugName;
	final BigDecimal _dose;
	final BigDecimal _equivalentDose;
	final Units _units;
	final Frequency _frequency;
	final Route _route;
	final boolean _asRequired;
	final Long _conceptId;
	final String _notes;
	private volatile int _hash;

	ParsedMedication(Long conceptId, String drugName, String mappedDrugName,
			BigDecimal dose, Units units, Frequency frequency, Route route, boolean asRequired,
			String notes) {
		_conceptId = conceptId;
		_drugName = drugName;
		_mappedDrugName = mappedDrugName;
		_dose = dose;
		_units = units;
		_equivalentDose = (dose != null && units != null) ? dose.multiply(units.conversion) : null;
		_frequency = frequency;
		_route = route != null ? route : Route.ORAL;
		_asRequired = asRequired;
		_notes = notes;
	}

	public String drugName() {
		return _drugName;
	}

	public BigDecimal dose() {
		return _dose;
	}

	public Units units() {
		return _units;
	}

	public Frequency frequency() {
		return _frequency;
	}

	public Route route() {
		return _route;
	}

	public boolean asRequired() {
		return _asRequired;
	}

	public String notes() {
		return _notes;
	}

	public Long conceptId() {
		return _conceptId;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (o == null || o.getClass() != this.getClass()) {
			return false;
		}
		ParsedMedication pmed = (ParsedMedication) o;
		boolean matchedConcepts = false;
		boolean matchedDrugNames = false;

		if (this._conceptId != null) {
			if (this._conceptId.equals(pmed._conceptId)) {
				matchedConcepts = true;
			}
		}
		if (_drugName != null && _drugName.length() > 0 &&
				this._drugName.equalsIgnoreCase(pmed._drugName)) {
			matchedDrugNames = true;
		}

		if (matchedConcepts == false && matchedDrugNames == false) {
			return false;
		}

		if (this._asRequired != pmed._asRequired) {
			return false;
		}
		if (this._frequency != pmed._frequency) {
			return false;
		}
		if (this._route != pmed._route) {
			return false;
		}
		if (this._equivalentDose != null && pmed._equivalentDose != null) {
			if (this._equivalentDose.compareTo(pmed._equivalentDose) != 0) {
				return false;
			}
		}
		else {
			if (this._dose != null && pmed._dose != null && this._dose.compareTo(pmed._dose) != 0) {
				return false;
			}
			if (this._dose == null && pmed._dose != null) {
				return false;
			}
			if (this._units != pmed._units) {
				return false;
			}
		}
		return true;
	}

	@Override public int hashCode() {
		if (_hash == 0) {
			int hash = 97;
			if (_equivalentDose != null) {
				hash = 23 * hash * new Double(_equivalentDose.doubleValue()).hashCode();
			}
			else {
				hash = 23 * hash * (_dose != null ? new Double(_dose.doubleValue()).hashCode() : 3);
				hash = 23 * hash * (_units != null ? _units.hashCode() : 5);
			}
			hash = 23 * hash * (_frequency != null ? _frequency.hashCode() : 2);
			hash = 23 * hash * (_route != null ? _route.hashCode() : 3);
			hash = 23 * hash * (_asRequired ? 1 : 2);
			_hash = hash;
		}
		return _hash;
	}

	@Override public String toString() {
		StringBuilder sb = new StringBuilder();
		sb.append(getClass().getName());
		sb.append("-");
		sb.append(userPresentableDescription());
		if (notes() != null) {
			sb.append(" ");
			sb.append(notes());
		}
		return sb.toString().trim();
	}

	/**
	 * Output a string version which is guaranteed to be parsed and result
	 * in an object that would be "equal" to the original (ie. round-tripping).
	 * @return
	 */
	public String userPresentableDescription() {
		StringBuilder sb = new StringBuilder();
		if (_mappedDrugName == null || _mappedDrugName.length() == 0) {
			sb.append(_drugName);
		}
		else {
			sb.append(_mappedDrugName);
		}
		sb.append(" ");
		if (_dose != null && _units != null) {
			sb.append(_dose.stripTrailingZeros().toPlainString());
			sb.append(_units.abbreviation());
			sb.append(" ");
		}
		if (_frequency != null) {
			sb.append(_frequency.title());
			sb.append(" ");
		}
		if (_route != null) {
			sb.append(_route.abbreviation());
			sb.append(" ");
		}
		if (_asRequired) {
			sb.append("PRN");
		}
		return sb.toString().trim();
	}

	@Override
	public int compareTo(ParsedMedication o) {
		String thisName = _mappedDrugName != null ? _mappedDrugName : _drugName;
		String thatName = o._mappedDrugName != null ? o._mappedDrugName : o._drugName;
		return thisName.compareToIgnoreCase(thatName);
	}
}
