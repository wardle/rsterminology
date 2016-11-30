package com.eldrix.terminology.snomedct.semantic;

import java.util.ArrayList;
import java.util.Optional;
import java.util.stream.Stream;

import com.eldrix.terminology.snomedct.Concept;

/**
 * A virtual therapeutic moiety (VTM).
 *
 */
public class Vtm extends Dmd {

	public Vtm(Concept c) {
		super(Product.VIRTUAL_THERAPEUTIC_MOIETY, c);
	}

	/**
	 * Is the concept specified a type of VTM?
	 * @param c
	 * @return
	 */
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
		for (Concept child : vtm.getChildConcepts()) {	// looking only at IS-A relationships
			if (Product.VIRTUAL_MEDICINAL_PRODUCT.isAProduct(child)) {
				vmps.add(child);
			}
			_addVmps(child, vmps);
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
				.map(Vmp::getDispensedDoseForm)
				.filter(Optional::isPresent)
				.map(Optional::get)
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
	
	public static boolean isPrescribable(Concept vtm) {
		return vtm.isActive() && Vtm.getVmps(vtm).anyMatch(vmp -> Vmp.isAvailable(vmp));
	}
	public boolean isPrescribable() {
		return Vtm.isPrescribable(_concept);
	}
}