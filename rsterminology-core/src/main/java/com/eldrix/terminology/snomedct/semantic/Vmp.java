package com.eldrix.terminology.snomedct.semantic;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.stream.Stream;

import org.apache.commons.lang3.StringUtils;

import com.eldrix.terminology.snomedct.Concept;
import com.eldrix.terminology.snomedct.Relationship;

/**
 * A virtual medicinal product (VMP).
 *
 */
public class Vmp extends Dmd {

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
	 * e.g. see amlodipine or pyridostigmine
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
		return BigDecimal.ZERO;
	}
	public BigDecimal getDose() {
		return getDose(_concept);
	}
}