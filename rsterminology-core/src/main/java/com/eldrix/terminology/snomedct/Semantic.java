package com.eldrix.terminology.snomedct;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

import org.apache.commons.lang.NotImplementedException;
import org.apache.commons.lang3.StringUtils;

/**
 * Helper methods to provide runtime-based understanding within SNOMED-CT.
 * @author Mark Wardle
 *
 */
public class Semantic {

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

	public abstract static class Dmd {

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
			public static Product productForConcept(Concept c) {
				for (Concept p : c.getParentConcepts()) {
					for (Product med : Product.values()) {
						if (p.getConceptId() == med.conceptId) {
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
			public boolean isAProduct(Concept c) {
				if (c != null) {
					for (Relationship r : c.getParentRelationships()) {
						if (r.getRelationshipTypeConceptId() == Semantic.RelationType.IS_A.conceptId && conceptId == r.getTargetConceptId()) {
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

		protected final Concept _concept;

		protected Dmd(Product product, Concept c) {
			if (product == null || c == null) {
				throw new NullPointerException("Product and concept mandatory.");
			}
			if (!product.isAProduct(c)) {
				throw new IllegalArgumentException("Concept is not a " + product);
			}
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
	}

	/**
	 * A virtual therapeutic moiety (VTM).
	 *
	 */
	public static class Vtm extends Dmd {

		public Vtm(Concept c) {
			super(Product.VIRTUAL_THERAPEUTIC_MOIETY, c);
		}

		public static boolean isA(Concept c) {
			return Product.VIRTUAL_THERAPEUTIC_MOIETY.isAProduct(c);
		}

		/**
		 * Return the VMPs for the given VTM
		 * There is some hierarchy here, in which a child of a VTM is a broad concept (such as oral dose) which has
		 * itself children that are VMP but it itself is not a VMP.
		 * VTM <->> VMP
		 * @param vtm
		 * @return
		 */
		public static Stream<Concept> getVmps(Concept vtm) {
			ArrayList<Concept> vmps = new ArrayList<>();
			_addVmps(vtm, vmps);
			return vmps.stream()
					.filter(child -> Product.VIRTUAL_MEDICINAL_PRODUCT.isAProduct(child));
		}

		/*
		 * Deal with hierarchically nested VMPs for a VTM.
		 * e.g. see amlodipine.
		 */
		private static void _addVmps(Concept vtm, ArrayList<Concept> vmps) {
			for (Concept child : vtm.getChildConcepts()) {	// looking only as IS-A relationships
				if (Product.VIRTUAL_MEDICINAL_PRODUCT.isAProduct(child)) {
					vmps.add(child);
				} else {
					_addVmps(child, vmps);
				}
			}
		}
		

		/**
		 * Return the VMPs for this VTM.
		 * @return
		 */
		public Stream<Vmp> getVmps() {
			return getVmps(_concept).map(c -> new Vmp(c));
		}


		public static Stream<Concept> getDispensedDoseForms(Concept vtm) {
			return getVmps(vtm)
				.flatMap(vmp -> Vmp.getDispensedDoseForms(vmp))
				.distinct();
		}
		
		public Stream<Concept> getDispensedDoseForms() {
			return getDispensedDoseForms(_concept);
		}

		public static Stream<Concept> getAmps(Concept vtm) {
			return getVmps(vtm)
					.flatMap(vmp -> Vmp.getAmps(vmp));
		}

		public Stream<Amp> getAmps() {
			return getAmps(_concept)
					.map(c -> new Amp(c));
		}

		public static Stream<Concept> getTfs(Concept vtm) {
			return getAmps(vtm)
					.map(amp -> Amp.getTf(amp))
					.filter(Optional::isPresent)
					.map(Optional::get)
					.distinct();
		}
		public Stream<Tf> getTfs() {
			return getTfs(_concept).map(Tf::new);
		}
	}

	/**
	 * A virtual medicinal product (VMP).
	 *
	 */
	public static class Vmp extends Dmd {

		public static long VMP_VALID_AS_A_PRESCRIBABLE_PRODUCT=8940201000001104L;
		public static long VMP_INVALID_AS_A_PRESCRIBABLE_PRODUCT=8940301000001108L;
		public static long VMP_NOT_RECOMMENDED_TO_PRESCRIBE__BRANDS_NOT_BIOEQUIVALENT=9900001000001104L;
		public static long VMP_NOT_RECOMMENDED_TO_PRESCRIBE__PATIENT_TRAINING=9900101000001103L;

		public Vmp(Concept c) {
			super(Product.VIRTUAL_MEDICINAL_PRODUCT, c);
		}
		
		public static boolean isA(Concept c) {
			return Product.VIRTUAL_MEDICINAL_PRODUCT.isAProduct(c);
		}

		/**
		 * Return the VTM for the given VMP
		 * VTM <->> VMP
		 * @param vmp
		 * @return
		 */
		public static Optional<Concept> getVtm(Concept vmp) {
			return Optional.ofNullable(_findVtm(vmp));
		}
		
		/*
		 * Deal with hierarchically nested VMPs for a VTM.
		 * e.g. see amlodipine.
		 */
		private static Concept _findVtm(Concept vmp) {
			Concept vtm = null;
			for (Concept parent : vmp.getParentConcepts()) {	// looking only at IS-A relationships
				if (Product.VIRTUAL_THERAPEUTIC_MOIETY.isAProduct(parent)) {
					vtm = parent;
				} else {
					vtm = _findVtm(parent);
				}
				if (vtm != null) {
					return vtm;
				}
			}
			return vtm;
		}

		public Optional<Vtm> getVtm() {
			return getVtm(_concept).map(Vtm::new);
		}

		/**
		 * Return the AMPs for the given VMP
		 * VMP <->> AMP
		 * @param vmp
		 * @return
		 */
		public static Stream<Concept> getAmps(Concept vmp) {
			return vmp.getChildConcepts().stream()
					.filter(child -> Product.ACTUAL_MEDICINAL_PRODUCT.isAProduct(child));
		}

		public Stream<Amp> getAmps() {
			return getAmps(_concept).map(c -> new Amp(c));
		}

		/**
		 * Return the trade families available for the given VMP.
		 * VMP <-~~~->> TF
		 * @param vmp
		 * @return
		 */
		public static Stream<Concept> getTfs(Concept vmp) {
			return getAmps(vmp)
					.map(amp -> Amp.getTf(amp))
					.filter(Optional::isPresent)
					.map(Optional::get)
					.distinct();
		}

		public Stream<Tf> getTfs() {
			return getTfs(_concept).map(c -> new Tf(c));
		}

		/**
		 * Return the VMPPs for the given VMP
		 * VMP <->> VMPP
		 * @param vmp
		 * @return
		 */
		public static Stream<Concept> getVmpps(Concept vmp) {
			return vmp.getChildRelationships().stream()
					.filter(r -> r.getRelationshipTypeConcept().getConceptId() == RelationType.HAS_VMP.conceptId)
					.map(Relationship::getSourceConcept);
		}

		public Stream<Vmpp> getVmpps() {
			return getVmpps(_concept).map(c -> new Vmpp(c));
		}

		/**
		 * Return the dispensed dose forms for the specified VMP.
		 * It is usually the case that VMPs have a single dose form, but there are some VMPs that have multiple dose forms.
		 * For example, many ear drops are recorded as "ear drops" AND "drops".
		 * @param vmp
		 * @return
		 */
		public static Stream<Concept> getDispensedDoseForms(Concept vmp) {
			return vmp.getParentRelationships().stream()
					.filter(r -> r.getRelationshipTypeConcept().getConceptId() == RelationType.HAS_DISPENSED_DOSE_FORM.conceptId)
					.map(Relationship::getTargetConcept)
					.distinct();
		}
		public Stream<Concept> getDispensedDoseForms() {
			return getDispensedDoseForms(_concept);
		}

		/**
		 * Does this VMP contain multiple active ingredients listed in its name?
		 * See Rule #1 of the DM&D implementation guide
		 * Note: this does not (rather unintuitively) include all drugs with multiple active ingredients
		 * @param vmp
		 * @return
		 */
		public static boolean hasMultipleActiveIngredientsInName(Concept vmp) {
			return vmp.getFullySpecifiedName().contains("+");
		}
		public boolean hasMultipleActiveIngredientsInName() {
			return hasMultipleActiveIngredientsInName(_concept);
		}

		/**
		 * Is this a top level VMP with no VTM?
		 * See Rule #2 of the DM&D implementation guide.
		 * @param vmp
		 * @return
		 */
		public static boolean hasNoVtm(Concept vmp) {
			return !getVtm(vmp).isPresent();
		}
		public boolean hasNoVtm() {
			return hasNoVtm(_concept);
		}

		/**
		 * Is this VMP invalid to prescribe?
		 * See Rule #3 of the DM&D implementation guide
		 * @param vmp
		 * @return
		 */
		public static boolean isInvalidToPrescribe(Concept vmp) {
			return vmp.getParentRelationships().stream()
					.filter(r -> r.getRelationshipTypeConcept().getConceptId() == RelationType.VMP_PRESCRIBING_STATUS.conceptId)
					.map(Relationship::getTargetConcept)
					.map(Concept::getConceptId)
					.anyMatch(id -> VMP_INVALID_AS_A_PRESCRIBABLE_PRODUCT == id);
		}
		public boolean isInvalidToPrescribe() {
			return isInvalidToPrescribe(_concept);
		}

		/**
		 * Is this a co-name drug?
		 * @param vmp
		 * @return
		 */
		public static boolean isCoNameDrug(Concept vmp) {
			return StringUtils.containsIgnoreCase(vmp.getFullySpecifiedName(), "co-");
		}
		public boolean isCoNameDrug() {
			return isCoNameDrug(_concept);
		}

		/**
		 * Is this VMP not recommended for prescribing?
		 * @param vmp
		 * @return
		 */
		public static boolean isNotRecommendedToPrescribe(Concept vmp) {
			return vmp.getParentRelationships().stream()
					.filter(r -> r.getRelationshipTypeConcept().getConceptId() == RelationType.VMP_PRESCRIBING_STATUS.conceptId)
					.map(Relationship::getTargetConcept)
					.map(Concept::getConceptId)
					.anyMatch(id -> VMP_NOT_RECOMMENDED_TO_PRESCRIBE__BRANDS_NOT_BIOEQUIVALENT == id ||
					VMP_NOT_RECOMMENDED_TO_PRESCRIBE__PATIENT_TRAINING == id);
		}
		public boolean isNotRecommendedToPrescribe() {
			return isNotRecommendedToPrescribe(_concept);
		}

		/**
		 * Return the active ingredients for this VMP.
		 * @param vmp
		 * @return
		 */
		public static Stream<Concept> getActiveIngredients(Concept vmp) {
			return vmp.getParentRelationships().stream()
					.filter(r -> r.getRelationshipTypeConcept().getConceptId() == RelationType.HAS_ACTIVE_INGREDIENT.conceptId)
					.map(Relationship::getTargetConcept);
		}
		public Stream<Concept> getActiveIngredients() {
			return getActiveIngredients(_concept);
		}
		
		/**
		 * Parse the dose and units for a given VMP.
		 * TODO: not implemented
		 */
		public static BigDecimal getDose(Concept vmp) {
			//String term = vmp.getPreferredDescription().getTerm();
			throw new NotImplementedException();
			//return null;
		}
		public BigDecimal getDose() {
			return getDose(_concept);
		}
	}


	/**
	 * An actual medicinal product (AMP).
	 *
	 */
	public static class Amp extends Dmd {

		public Amp(Concept c) {
			super(Product.ACTUAL_MEDICINAL_PRODUCT, c);
		}

		public static boolean isA(Concept c) {
			return Product.ACTUAL_MEDICINAL_PRODUCT.isAProduct(c);
		}

		/**
		 * Return the TF for the given AMP.
		 * TF <->> AMP
		 * @param amp
		 * @return
		 */
		public static Optional<Concept> getTf(Concept amp) {
			return amp.getParentConcepts().stream()
					.filter(parent -> Product.TRADE_FAMILY.isAProduct(parent))
					.findFirst();
		}

		public Optional<Tf> getTf() {
			return getTf(_concept).map(Tf::new);
		}

		/**
		 * Return the AMPPs for the given AMP
		 * AMP <->> AMPP 
		 * @param amp
		 * @return
		 */
		public static Stream<Concept> getAmpps(Concept amp) {
			return amp.getChildRelationships().stream()
					.filter(r -> r.getRelationshipTypeConcept().getConceptId() == RelationType.HAS_AMP.conceptId)
					.map(Relationship::getSourceConcept);
		}
		public Stream<Ampp> getAmpps() {
			return getAmpps(_concept).map(Ampp::new);
		}

		/**
		 * Return the VMP for the given AMP
		 * VMP <->> AMP
		 * @param amp
		 * @return
		 */
		public static Optional<Concept> getVmp(Concept amp) {
			return amp.getParentConcepts().stream()
					.filter(parent -> Product.VIRTUAL_MEDICINAL_PRODUCT.isAProduct(parent))
					.findFirst();
		}
		public Optional<Vmp> getVmp() {
			return getVmp(_concept).map(Vmp::new);
		}

		/**
		 * Return the dose forms for the given AMP
		 */
		public static Stream<Concept> getDispensedDoseForms(Concept amp) {
			return amp.getParentRelationships().stream()
					.filter(r -> RelationType.HAS_DISPENSED_DOSE_FORM.conceptId == r.getRelationshipTypeConcept().getConceptId())
					.map(Relationship::getTargetConcept)
					.distinct();
		}
		public Stream<Concept> getDispensedDoseForms() {
			return getDispensedDoseForms(_concept);
		}
	}

	/**
	 * A trade family (TF).
	 *
	 */
	public static class Tf extends Dmd {
		
		public Tf(Concept c) {
			super(Product.TRADE_FAMILY, c);
		}

		public static boolean isA(Concept c) {
			return Product.TRADE_FAMILY.isAProduct(c);
		}

		/**
		 * Return the AMPs for the given TF
		 * TF <->> AMP
		 * @param tf
		 * @return
		 */
		public static Stream<Concept> getAmps(Concept tf) {
			return tf.getChildConcepts().stream()
					.filter(child -> Product.ACTUAL_MEDICINAL_PRODUCT.isAProduct(child));
		}

		public Stream<Amp> getAmps() {
			return getAmps(_concept).map(Amp::new);
		}

		/**
		 * Return the available licensed dose forms for products in this trade family.
		 * @param tf
		 * @return
		 */
		public static Stream<Concept> getDispensedDoseForms(Concept tf) {
			return getAmps(tf)
					.flatMap(amp -> Amp.getDispensedDoseForms(amp))
					.distinct();
		}
	}

	/**
	 * A virtual medicine product pack (VMPP).
	 *
	 */
	public static class Vmpp extends Dmd {

		public Vmpp(Concept c) {
			super(Product.VIRTUAL_MEDICINAL_PRODUCT_PACK, c);
		}

		public static boolean isA(Concept c) {
			return Product.VIRTUAL_MEDICINAL_PRODUCT_PACK.isAProduct(c);
		}

		/**
		 * Return the VMP for the given VMPP
		 * VMP <->> VMPP
		 * @param vmpp
		 * @return
		 */
		public static Optional<Concept> getVmp(Concept vmpp) {
			return vmpp.getParentRelationships().stream()
					.filter(r -> r.getRelationshipTypeConcept().getConceptId() == RelationType.HAS_VMP.conceptId)
					.findFirst()
					.map(Relationship::getTargetConcept);
		}

		public Optional<Vmp> getVmp() {
			return getVmp(_concept).map(Vmp::new);
		}
		
		public static Optional<Concept> getVtm(Concept vmpp) {
			return getVmp(vmpp).flatMap(vmp -> Vmp.getVtm(vmp));
		}
		public Optional<Vtm> getVtm() {
			return getVtm(_concept).map(Vtm::new);
		}

		/**
		 * Return the AMPPs for the given VMPP.
		 * VMPP <->> AMPP
		 */
		public static Stream<Concept>getAmpps(Concept vmpp) {
			return vmpp.getChildConcepts().stream()
					.filter(child -> Product.ACTUAL_MEDICINAL_PRODUCT_PACK.isAProduct(child));
		}	
		public Stream<Ampp> getAmpps() {
			return getAmpps(_concept).map(Ampp::new);
		}
	}

	/**
	 * An actual medicinal product pack (AMPP).
	 *
	 */
	public static class Ampp extends Dmd{

		public Ampp(Concept c) {
			super(Product.ACTUAL_MEDICINAL_PRODUCT_PACK, c);
		}

		public static boolean isA(Concept c) {
			return Product.ACTUAL_MEDICINAL_PRODUCT_PACK.isAProduct(c);
		}

		/**
		 * Return the AMP for the given AMPP
		 * AMP <->> AMPP
		 * @param ampp
		 * @return
		 */
		public static Optional<Concept> getAmp(Concept ampp) {
			return ampp.getParentRelationships().stream()
					.filter(r -> r.getRelationshipTypeConcept().getConceptId() == RelationType.HAS_AMP.conceptId)
					.findFirst()
					.map(Relationship::getTargetConcept);
		}

		public Optional<Amp> getAmp() {
			return getAmp(_concept).map(Amp::new);
		}

		/**
		 * Return the VMPP for the given AMPP
		 * VMPP <-->> AMPP
		 * @param ampp
		 * @return
		 */
		public static Optional<Concept> getVmpp(Concept ampp) {
			return ampp.getParentConcepts().stream()
					.filter(parent -> Product.VIRTUAL_MEDICINAL_PRODUCT_PACK.isAProduct(parent))
					.findFirst();
		}

		public Optional<Vmpp> getVmpp() {
			return getVmpp(_concept).map(Vmpp::new);
		}

		/**
		 * Return the trade family for the specified AMPP.
		 * @param ampp
		 * @return
		 */
		public static Optional<Concept> getTf(Concept ampp) {
			return getAmp(ampp).flatMap(amp -> Amp.getTf(amp));
		}
		public Optional<Tf> getTf() {
			return getTf(_concept).map(Tf::new);
		}
	}
}
