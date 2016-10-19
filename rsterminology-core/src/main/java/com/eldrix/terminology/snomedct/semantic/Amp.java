package com.eldrix.terminology.snomedct.semantic;

import java.util.Optional;
import java.util.stream.Stream;

import com.eldrix.terminology.snomedct.Concept;
import com.eldrix.terminology.snomedct.Relationship;
import com.eldrix.terminology.snomedct.semantic.Dmd.Product;

/**
 * An actual medicinal product (AMP).
 *
 */
public class Amp extends Dmd {

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

	public static Optional<Concept> getVtm(Concept amp) {
		return Amp.getVmp(amp).flatMap(Vmp::getVtm);
	}
	public Optional<Vtm> getVtm() {
		return getVtm(_concept).map(Vtm::new);
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