package com.eldrix.terminology.snomedct;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.stream.Collectors;

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

	/**
	 * The DM&D consists of the following structure:
	 * VTM - Virtual therapeutic moiety
	 * VMP - Virtual medicinal product
	 * VMPP - Virtual medicinal product pack
	 * AMP - Actual medicinal product
	 * AMPP - Actual medicinal product pack
	 * TF - Trade family
	 * A TF has multiple AMP.
	 * An AMP has multiple AMPP
	 * A VMPP has multiple AMPP
	 * A VMP has multiple AMPs and multiple VMPPs
	 * A VTM has multiple VMP. 
	 * 
	 * @see http://www.nhsbsa.nhs.uk/PrescriptionServices/Documents/PrescriptionServices/dmd_Implementation_Guide_Secondary_Care.pdf
	 * @author mark
	 *
	 */
	public enum DmdProduct {
		TRADE_FAMILY("TF", 9191801000001103L),
		VIRTUAL_MEDICINAL_PRODUCT("VMP", 10363801000001108L),
		VIRTUAL_MEDICINAL_PRODUCT_PACK("VMPP",8653601000001108L),
		VIRTUAL_THERAPEUTIC_MOIETY("VTM",10363701000001104L),
		ACTUAL_MEDICINAL_PRODUCT("AMP", 10363901000001102L),
		ACTUAL_MEDICINAL_PRODUCT_PACK("AMPP", 10364001000001104L);

		private String _abbreviation;
		private long _conceptId;

		DmdProduct(String abbreviation, long conceptId) {
			_abbreviation = abbreviation;
			_conceptId = conceptId;
		}

		public String abbreviation() {
			return _abbreviation;
		}

		/**
		 * Determine the type of pharmaceutical product of this concept
		 * @param c
		 * @return
		 */
		public static DmdProduct productForConcept(Concept c) {
			for (Concept p : c.getParentConcepts()) {
				for (DmdProduct med : DmdProduct.values()) {
					if (p.getConceptId() == med._conceptId) {
						return med;
					}
				}
			}
			return null;
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
		public boolean isAConcept(Concept c) {
			for (Concept p : c.getParentConcepts()) {
				if (_conceptId == p.getConceptId()) {
					return true;
				}
			}
			return false;
		}
	}
	
	
	public static class Vtm {

		public static boolean isA(Concept c) {
			return DmdProduct.VIRTUAL_THERAPEUTIC_MOIETY.isAConcept(c);
		}

		/**
		 * Return the VMPs for the given VTM
		 * VTM <->> VMP
		 * @param vtm
		 * @return
		 */
		public static List<Concept> getVmps(Concept vtm) {
			return vtm.getChildConcepts().stream()
					.filter(child -> DmdProduct.VIRTUAL_MEDICINAL_PRODUCT.isAConcept(child))
					.collect(Collectors.toList());
		}
		
		public static List<Concept> getDispensedDoseForms(Concept vtm) {
			HashSet<Concept> doseForms = new HashSet<Concept>();
			getVmps(vtm).forEach(vmp -> doseForms.addAll(Vmp.getDispensedDoseForms(vmp)));
			return new ArrayList<Concept>(doseForms);
		}
	}

	public static class Vmp {
		
		public static boolean isA(Concept c) {
			return DmdProduct.VIRTUAL_MEDICINAL_PRODUCT.isAConcept(c);
		}
		
		/**
		 * Return the VTM for the given VMP
		 * VTM <->> VMP
		 * @param vmp
		 * @return
		 */
		public static Concept getVtm(Concept vmp) {
			return vmp.getParentConcepts().stream()
					.filter(parent -> DmdProduct.VIRTUAL_THERAPEUTIC_MOIETY.isAConcept(parent))
					.findFirst().orElse(null);
		}
		
		/**
		 * Return the AMPs for the given VMP
		 * VMP <->> AMP
		 * @param vmp
		 * @return
		 */
		public static List<Concept> getAmps(Concept vmp) {
			return vmp.getChildConcepts().stream()
					.filter(child -> DmdProduct.ACTUAL_MEDICINAL_PRODUCT.isAConcept(child))
					.collect(Collectors.toList());
		}

		/**
		 * Return the VMPPs for the given VMP
		 * VMP <->> VMPP
		 * @param vmp
		 * @return
		 */
		public static List<Concept> getVmpps(Concept vmp) {
			return vmp.getChildRelationships().stream()
				.filter(r -> r.getRelationshipTypeConcept().getConceptId() == RelationType.HAS_VMP.conceptId)
				.map(Relationship::getSourceConcept)
				.collect(Collectors.toList());
		}
		
		public static List<Concept> getDispensedDoseForms(Concept vmp) {
			return vmp.getParentRelationships().stream()
				.filter(r -> r.getRelationshipTypeConcept().getConceptId() == RelationType.HAS_DISPENSED_DOSE_FORM.conceptId)
				.map(Relationship::getTargetConcept)
				.collect(Collectors.toList());
		}
	}
	

	public static class Amp {
		
		public static boolean isA(Concept c) {
			return DmdProduct.ACTUAL_MEDICINAL_PRODUCT.isAConcept(c);
		}

		/**
		 * Return the TF for the given AMP.
		 * TF <->> AMP
		 * @param amp
		 * @return
		 */
		public static Concept getTf(Concept amp) {
			return amp.getParentConcepts().stream()
					.filter(parent -> DmdProduct.TRADE_FAMILY.isAConcept(parent))
					.findFirst().orElse(null);
		}

		/**
		 * Return the AMPPs for the given AMP
		 * AMP <->> AMPP 
		 * @param amp
		 * @return
		 */
		public static List<Concept> getAmpps(Concept amp) {
			return amp.getChildRelationships().stream()
				.filter(r -> r.getRelationshipTypeConcept().getConceptId() == RelationType.HAS_AMP.conceptId)
				.map(Relationship::getSourceConcept)
				.collect(Collectors.toList());
		}
		

		/**
		 * Return the VMP for the given AMP
		 * VMP <->> AMP
		 * @param amp
		 * @return
		 */
		public static Concept getVmp(Concept amp) {
			return amp.getParentConcepts().stream()
					.filter(parent -> DmdProduct.VIRTUAL_MEDICINAL_PRODUCT.isAConcept(parent))
					.findFirst().orElse(null);
		}
		
		/**
		 * Return the dose forms for the given AMP
		 */
		public static List<Concept> getDispensedDoseForms(Concept amp) {
			return amp.getParentRelationships().stream()
					.filter(r -> RelationType.HAS_DISPENSED_DOSE_FORM.conceptId == r.getRelationshipTypeConcept().getConceptId())
					.map(Relationship::getTargetConcept)
					.distinct()
					.collect(Collectors.toList());
		}

	}

	public static class Tf {

		public static boolean isA(Concept c) {
			return DmdProduct.TRADE_FAMILY.isAConcept(c);
		}

		/**
		 * Return the AMPs for the given TF
		 * TF <->> AMP
		 * @param tf
		 * @return
		 */
		public static List<Concept> getAmps(Concept tf) {
			return tf.getChildConcepts().stream()
					.filter(child -> DmdProduct.ACTUAL_MEDICINAL_PRODUCT.isAConcept(child))
					.collect(Collectors.toList());
		}

		/**
		 * Return the available licensed dose forms for products in this trade family.
		 * @param tf
		 * @return
		 */
		public static List<Concept> getDispensedDoseForms(Concept tf) {
			HashSet<Concept> doseForms = new HashSet<Concept>();
			getAmps(tf).forEach(amp -> doseForms.addAll(Amp.getDispensedDoseForms(amp)));
			return new ArrayList<Concept>(doseForms);
		}
	}
	
	public static class Vmpp {

		public static boolean isA(Concept c) {
			return DmdProduct.VIRTUAL_MEDICINAL_PRODUCT_PACK.isAConcept(c);
		}
		
		/**
		 * Return the VMP for the given VMPP
		 * VMP <->> VMPP
		 * @param vmpp
		 * @return
		 */
		public static Concept getVmp(Concept vmpp) {
			Relationship vmpRelationship = vmpp.getParentRelationships().stream()
					.filter(r -> r.getRelationshipTypeConcept().getConceptId() == RelationType.HAS_VMP.conceptId)
					.findFirst().orElse(null);
			return vmpRelationship != null ? vmpRelationship.getTargetConcept() : null;
		}

		/**
		 * Return the AMPPs for the given VMPP.
		 * VMPP <->> AMPP
		 */
		public static List<Concept>getAmpps(Concept vmpp) {
			return vmpp.getChildConcepts().stream()
					.filter(child -> DmdProduct.ACTUAL_MEDICINAL_PRODUCT_PACK.isAConcept(child))
					.collect(Collectors.toList());
		}	
	}
	
	public static class Ampp {
		
		public static boolean isA(Concept c) {
			return DmdProduct.ACTUAL_MEDICINAL_PRODUCT_PACK.isAConcept(c);
		}

		/**
		 * Return the AMP for the given AMPP
		 * AMP <->> AMPP
		 * @param ampp
		 * @return
		 */
		public static Concept getAmp(Concept ampp) {
			Relationship ampRelationship = ampp.getParentRelationships().stream()
					.filter(r -> r.getRelationshipTypeConcept().getConceptId() == RelationType.HAS_AMP.conceptId)
					.findFirst().orElse(null);
			return ampRelationship != null ? ampRelationship.getTargetConcept() : null;
		}

		/**
		 * Return the VMPP for the given AMPP
		 * VMPP <-->> AMPP
		 * @param ampp
		 * @return
		 */
		public static Concept getVmpp(Concept ampp) {
			return ampp.getParentConcepts().stream()
					.filter(parent -> DmdProduct.VIRTUAL_MEDICINAL_PRODUCT_PACK.isAConcept(parent))
					.findFirst().orElse(null);
		}		
	}

}
