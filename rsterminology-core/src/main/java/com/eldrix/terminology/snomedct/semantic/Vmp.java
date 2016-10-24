package com.eldrix.terminology.snomedct.semantic;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.HashSet;
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
	private static long VMP_IS_AVAILABLE=8940901000001109L;

	public enum PrescribingStatus {
		VALID(true, 8940201000001104L),
		INVALID(false, 8940301000001108L),
		NOT_RECOMMENDED_BRANDS_NOT_BIOEQUIVALENT(false, 8940301000001108L),
		NOT_RECOMMENDED_PATIENT_TRAINING(false, 9900101000001103L);
		@SuppressWarnings("serial")
		static HashMap<Long, PrescribingStatus> _lookup = new HashMap<Long, PrescribingStatus>() {{
			for (PrescribingStatus ps : PrescribingStatus.values()) {
				put(ps.conceptId, ps);
			}
		}};
		public final boolean isValid;
		final long conceptId;
		PrescribingStatus(boolean valid, long conceptId) {
			this.isValid = valid;
			this.conceptId = conceptId;
		}
		public static PrescribingStatus statusForConcept(long conceptId) {
			return Optional.ofNullable(_lookup.get(conceptId)).orElse(PrescribingStatus.INVALID);
		}
	}

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
	public static Stream<Concept> getVtms(Concept vmp) {
		HashSet<Concept> vtms = new HashSet<>();
		_findVtm(vmp, vtms);
		return vtms.stream();
	}

	/*
	 * Deal with hierarchically nested VMPs for a VTM.
	 * e.g. see amlodipine or pyridostigmine
	 */
	private static void _findVtm(Concept vmp, HashSet<Concept> vtms) {
		// if we've reached the top of the pharmaceutical hierarchy, don't explore further
		if (vmp.getConceptId() == Category.PHARMACEUTICAL_OR_BIOLOGICAL_PRODUCT.conceptId) {
			return;
		}
		for (Concept parent : vmp.getParentConcepts()) {	// looking only at IS-A relationships
			if (Product.VIRTUAL_THERAPEUTIC_MOIETY.isAProduct(parent)) {
				vtms.add(parent);
			} else {
				_findVtm(parent, vtms);
			}
		}
	}

	public Stream<Vtm> getVtms() {
		return getVtms(_concept).map(Vtm::new);
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
	 * Return the dispensed dose form for the specified VMP.
	 * @param vmp
	 * @return
	 */
	public static Optional<Concept> getDispensedDoseForm(Concept vmp) {
		return vmp.getParentRelationships().stream()
				.filter(r -> r.getRelationshipTypeConcept().getConceptId() == RelationType.HAS_DISPENSED_DOSE_FORM.conceptId)
				.findFirst()
				.map(Relationship::getTargetConcept);
	}
	public Optional<Concept> getDispensedDoseForm() {
		return getDispensedDoseForm(_concept);
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
	public static boolean hasNoVtms(Concept vmp) {
		return getVtms(vmp).findAny().isPresent();
	}
	public boolean hasNoVtms() {
		return hasNoVtms(_concept);
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

	public static Optional<Concept> getPrescribingStatus(Concept vmp) {
		return vmp.getParentRelationships().stream()
				.filter(r -> r.getRelationshipTypeConcept().getConceptId() == RelationType.VMP_PRESCRIBING_STATUS.conceptId)
				.findAny()
				.map(Relationship::getTargetConcept);
	}
	public PrescribingStatus getPrescribingStatus() {
		return Vmp.getPrescribingStatus(_concept)
				.map(Concept::getConceptId)
				.map(PrescribingStatus::statusForConcept)
				.orElse(PrescribingStatus.INVALID);
	}

	public static boolean isAvailable(Concept vmp) {
		return vmp.getParentRelationships().stream()
				.filter(r -> r.getRelationshipTypeConcept().getConceptId() == RelationType.VMP_NON_AVAILABILITY_INDICATOR.conceptId)
				.anyMatch(r -> r.getTargetConcept().getConceptId() == VMP_IS_AVAILABLE);
	}
	
	public boolean isAvailable() {
		return Vmp.isAvailable(_concept);
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
	 * TODO: how would this work for liquids? getDose() as an API doesn't really work!
	 * TODO: remove and replace with something that can cope with VMPs of multiple types.
	 */
	public static BigDecimal getDose(Concept vmp) {
		//String term = vmp.getPreferredDescription().getTerm();
		return BigDecimal.ZERO;
	}
	public BigDecimal getDose() {
		return getDose(_concept);
	}
}