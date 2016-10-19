package com.eldrix.terminology.snomedct.semantic;

import java.util.Optional;

import com.eldrix.terminology.snomedct.Concept;
import com.eldrix.terminology.snomedct.Relationship;
import com.eldrix.terminology.snomedct.semantic.Dmd.Product;

/**
 * An actual medicinal product pack (AMPP).
 *
 */
public class Ampp extends Dmd{

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