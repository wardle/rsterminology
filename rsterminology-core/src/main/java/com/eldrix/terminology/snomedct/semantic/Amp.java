package com.eldrix.terminology.snomedct.semantic;

import java.util.Optional;
import java.util.stream.Stream;

import com.eldrix.terminology.snomedct.Concept;
import com.eldrix.terminology.snomedct.Relationship;

/**
 * An actual medicinal product (AMP).
 *
 */
public class Amp extends Dmd {
	private final static long CONCEPT_EXCIPIENT_NOT_DECLARED = 8653301000001102L;

	public Amp(Concept c) {
		super(Product.ACTUAL_MEDICINAL_PRODUCT, c);
	}

	/**
	 * Is the concept specified a type of AMP?
	 * @param c
	 * @return
	 */
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

	public static Stream<Concept> getVtms(Concept amp) {
		return Amp.getVmp(amp).map(Vmp::getVtms).orElse(Stream.empty());
	}
	public Stream<Vtm> getVtms() {
		return getVtms(_concept).map(Vtm::new);
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
	
	/**
	 * Is this AMP appropriate to be prescribed as a VMP instead?
	 * This simply checks that 
	 * <ul> 
	 * <li> there is a VMP (should always be the case)
	 * <li> the VMP has a valid prescribing status
	 * <li> the VMP is available
	 * </ul>
	 * 
	 * @param amp
	 * @return
	 */
	public static boolean shouldPrescribeVmp(Concept amp) {
		return Amp.getVmp(amp).map(Vmp::isPrescribable).orElse(Boolean.FALSE);
	}
	public boolean shouldPrescribeVmp() {
		return Amp.shouldPrescribeVmp(_concept);
	}
	
	public static boolean isAvailable(Concept amp) {
		return Vmp.isAvailable(amp);		// the logic for VMP works for AMPs as well
	}
	public boolean isAvailable() {
		return Amp.isAvailable(_concept);
	}
	
	public static Stream<Concept> getActiveIngredients(Concept amp) {
		return amp.getParentRelationships().stream()
				.filter(r -> RelationType.HAS_ACTIVE_INGREDIENT.conceptId == r.getRelationshipTypeConcept().getConceptId())
				.map(Relationship::getTargetConcept);
	}
	public Stream<Concept> getActiveIngredients() {
		return Amp.getActiveIngredients(_concept);
	}
	
	/**
	 * Return the excipients for this AMP.
	 * If no excipients are recorded, DM&D explicitly includes an excipient 
	 * CONCEPT_EXCIPIENT_NOT_DECLARED which we filter out here as it makes no sense
	 * to include it.
	 * @param amp
	 * @return
	 */
	public static Stream<Concept> getExcipients(Concept amp) {
		return amp.getParentRelationships().stream()
				.filter(r -> RelationType.HAS_EXCIPIENT.conceptId == r.getRelationshipTypeConcept().getConceptId())
				.filter(r -> r.getTargetConcept().getConceptId() != CONCEPT_EXCIPIENT_NOT_DECLARED)
				.map(Relationship::getTargetConcept);
	}
	public Stream<Concept> getExcipients() {
		return Amp.getExcipients(_concept);
	}
}