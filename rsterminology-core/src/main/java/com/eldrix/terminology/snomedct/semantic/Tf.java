package com.eldrix.terminology.snomedct.semantic;

import java.util.Optional;
import java.util.stream.Stream;

import com.eldrix.terminology.snomedct.Concept;
import com.eldrix.terminology.snomedct.semantic.Dmd.Product;

/**
 * A trade family (TF).
 *
 */
public class Tf extends Dmd {
	
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

	public static Stream<Concept> getVmps(Concept tf) {
		return Tf.getAmps(tf).map(Amp::getVmp)
				.filter(Optional::isPresent)
				.map(Optional::get)
				.distinct();
	}
	public Stream<Vmp> getVmps() {
		return Tf.getVmps(_concept).map(Vmp::new);
	}
	
	public static Stream<Concept> getVtms(Concept tf) {
		return getVmps(tf).map(Vmp::getVtm).filter(Optional::isPresent).map(Optional::get).distinct();
	}
	public Stream<Vtm> getVtms() {
		return Tf.getVtms(_concept).map(Vtm::new);
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