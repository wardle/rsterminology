package com.eldrix.terminology.snomedct.semantic;

import java.util.Optional;
import java.util.stream.Stream;

import com.eldrix.terminology.snomedct.Concept;

/**
 * A trade family (TF).
 *
 */
public class Tf extends Dmd {
	
	public Tf(Concept c) {
		super(Product.TRADE_FAMILY, c);
	}

	/**
	 * Is the concept specified a type of TF?
	 * @param c
	 * @return
	 */
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
		return getVmps(tf).flatMap(Vmp::getVtms).distinct();
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
	public Stream<Concept> getDispensedDoseForms() {
		return Tf.getDispensedDoseForms(_concept);
	}
	
	public static boolean isPrescribable(Concept tf) {
		return tf.isActive() && Tf.getAmps(tf).anyMatch(amp -> Amp.isAvailable(amp));
	}
	public boolean isPrescribable() {
		return Tf.isPrescribable(_concept);
	}
}