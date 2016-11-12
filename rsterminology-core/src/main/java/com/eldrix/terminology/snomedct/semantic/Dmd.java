package com.eldrix.terminology.snomedct.semantic;

import java.util.Objects;
import java.util.Optional;

import com.eldrix.terminology.snomedct.Concept;
import com.eldrix.terminology.snomedct.Relationship;

public abstract class Dmd {

	/**
	 * The DM&D consists of the following structure:
	 * VTM - Virtual therapeutic moiety
	 * VMP - Virtual medicinal product
	 * VMPP - Virtual medicinal product pack
	 * AMP - Actual medicinal product
	 * AMPP - Actual medicinal product pack
	 * TF - Trade family
	 *
	 * TF <-->> AMP
	 * AMP <-->> AMPP
	 * VMPP <-->> AMPP
	 * VMP <-->> AMP
	 * VMP <-->> VMPP
	 * VTM <-->> VMP
	 * 
	 * @see http://www.nhsbsa.nhs.uk/PrescriptionServices/Documents/PrescriptionServices/dmd_Implementation_Guide_Secondary_Care.pdf
	 * @author mark
	 *
	 */
	public enum Product {
		TRADE_FAMILY("TF", 9191801000001103L),
		VIRTUAL_MEDICINAL_PRODUCT("VMP", 10363801000001108L),
		VIRTUAL_MEDICINAL_PRODUCT_PACK("VMPP",8653601000001108L),
		VIRTUAL_THERAPEUTIC_MOIETY("VTM",10363701000001104L),
		ACTUAL_MEDICINAL_PRODUCT("AMP", 10363901000001102L),
		ACTUAL_MEDICINAL_PRODUCT_PACK("AMPP", 10364001000001104L);
	
		private final String _abbreviation;
		public final long conceptId;
	
		Product(String abbreviation, long conceptId) {
			_abbreviation = abbreviation;
			this.conceptId = conceptId;
		}
	
		public String abbreviation() {
			return _abbreviation;
		}
	
		/**
		 * Determine the type of pharmaceutical product of this concept
		 * @param c
		 * @return
		 */
		public static Optional<Dmd.Product> productForConcept(Concept c) {
			for (Concept p : c.getParentConcepts()) {
				for (Dmd.Product med : Product.values()) {
					if (p.getConceptId() == med.conceptId) {
						return Optional.of(med);
					}
				}
			}
			return Optional.empty();
		}
		
		/**
		 * Is this concept a type of this product?
		 * Note: This is different to the usual SNOMED-CT understanding as the DM&D has slightly
		 * broken semantics as IS-A relationships should result in a grandchild concept being equal
		 * to a parent AND the grandparent, but this isn't the case for the DM&D structures.
		 * As such, we check only DIRECT relationships for equality within DM&D concepts.
		 * @param c
		 * @return
		 */
		public boolean isAProduct(Concept c) {
			if (c != null) {
				for (Relationship r : c.getParentRelationships()) {
					if (r.getRelationshipTypeConceptId() == RelationType.IS_A.conceptId && conceptId == r.getTargetConceptId()) {
						return true;
					}
				}
			}
			else {
				throw new NullPointerException("Concept null.");
			}
			return false;
		}
	}
	protected final Product _product;
	protected final Concept _concept;

	protected Dmd(Dmd.Product product, Concept c) {
		if (product == null || c == null) {
			throw new NullPointerException("Product and concept mandatory.");
		}
		if (!product.isAProduct(c)) {
			throw new IllegalArgumentException("Concept is not a " + product);
		}
		_product = product;
		_concept = c;
	}
	
	public Concept getConcept() {
		return _concept;
	}
	
	@Override
	public boolean equals(Object obj) {
		if (super.equals(obj)) {
			return true;
		}
		if (obj instanceof Dmd) {
			Dmd d = (Dmd) obj;
			return d._concept == this._concept;
		}
		return false;
	}
	
	@Override
	public int hashCode() {
		return Objects.hash(_concept);
	}
	
	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		sb.append(_product.abbreviation());
		sb.append("-");
		sb.append(_concept.getPreferredDescription().getTerm());
		sb.append("-");
		sb.append(_concept.getConceptId());
		return sb.toString();
	}
}