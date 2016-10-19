package com.eldrix.terminology.snomedct.semantic;

import java.util.Optional;
import java.util.stream.Stream;

import com.eldrix.terminology.snomedct.Concept;
import com.eldrix.terminology.snomedct.Relationship;
import com.eldrix.terminology.snomedct.semantic.Dmd.Product;

/**
 * A virtual medicine product pack (VMPP).
 *
 */
public class Vmpp extends Dmd {

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