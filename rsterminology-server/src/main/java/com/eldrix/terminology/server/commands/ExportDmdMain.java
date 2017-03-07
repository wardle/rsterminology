package com.eldrix.terminology.server.commands;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.cayenne.ObjectContext;
import org.apache.cayenne.ResultBatchIterator;
import org.apache.cayenne.configuration.server.ServerRuntime;
import org.apache.cayenne.query.SelectQuery;

import com.eldrix.terminology.snomedct.Concept;
import com.eldrix.terminology.snomedct.semantic.Amp;
import com.eldrix.terminology.snomedct.semantic.Ampp;
import com.eldrix.terminology.snomedct.semantic.Category;
import com.eldrix.terminology.snomedct.semantic.Dmd;
import com.eldrix.terminology.snomedct.semantic.Dmd.Product;
import com.eldrix.terminology.snomedct.semantic.Tf;
import com.eldrix.terminology.snomedct.semantic.Vmp;
import com.eldrix.terminology.snomedct.semantic.Vmpp;
import com.eldrix.terminology.snomedct.semantic.Vtm;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.opencsv.CSVWriter;

import io.bootique.meta.application.CommandMetadata;
import io.bootique.cli.Cli;
import io.bootique.command.CommandOutcome;
import io.bootique.command.CommandWithMetadata;

/**
 * Exports the DMD structures ready to populate pick-lists in standalone applications
 * @author mark
 *
 */
public class ExportDmdMain extends CommandWithMetadata {

	@Inject
	public Provider<ServerRuntime> cayenne;

	private static CommandMetadata createMetadata() {
		return CommandMetadata.builder(ExportDmdMain.class)
				.description("Export DMD main file")
				.build();
	}

	public ExportDmdMain() {
		super(createMetadata());
	}

	@Override
	public CommandOutcome run(Cli cli) {
		ObjectContext context = cayenne.get().newContext();
		PrintWriter writer = new PrintWriter(System.out);
		try (CSVWriter csv = new CSVWriter(writer)) {
			SelectQuery<Concept> query = SelectQuery.query(Concept.class, Concept.RECURSIVE_PARENT_CONCEPTS.dot(Concept.CONCEPT_ID).eq(Category.PHARMACEUTICAL_OR_BIOLOGICAL_PRODUCT.conceptId));
			String[] row = new String[] {"product", "isPrescribable","conceptIdentifier","type", "isSearchable", "prescribeAs" };
			csv.writeNext(row);
			try (ResultBatchIterator<Concept> iterator = query.batchIterator(context, 500)) {
				while (iterator.hasNext()) {
					List<Concept> batch = iterator.next();
					for (Concept c : batch) {
						Dmd.Product.productForConcept(c).ifPresent(p -> {
							row[0] = c.getPreferredDescription().getTerm();
							row[1] = String.valueOf(_productIsPrescribable(c, p));
							row[2] = c.getConceptId().toString();
							row[3] = p.abbreviation();
							row[4] = String.valueOf(_productIsSearchable(c, p));
							row[5] = _prescribingNotes(c,  p);
							csv.writeNext(row);
						});
					}
				}
			}
		} catch (IOException e) {
			e.printStackTrace();
			return CommandOutcome.failed(-1, e);
		}
		return CommandOutcome.succeeded();
	}

	boolean _productIsPrescribable(Concept c, Product product) {
		switch (product) {
		case ACTUAL_MEDICINAL_PRODUCT:
			return !Amp.shouldPrescribeVmp(c);		// an AMP is prescribable only when VMP is not
		case ACTUAL_MEDICINAL_PRODUCT_PACK:
			return false;
		case TRADE_FAMILY:
			return Tf.getAmps(c).anyMatch(amp -> Amp.shouldPrescribeVmp(amp) == false);
		case VIRTUAL_MEDICINAL_PRODUCT:
			return Vmp.isPrescribable(c);
		case VIRTUAL_MEDICINAL_PRODUCT_PACK:
			return false;
		case VIRTUAL_THERAPEUTIC_MOIETY:
			return Vtm.getVmps(c).anyMatch(vmp -> Vmp.isPrescribable(vmp));
		default:
			return false;
		}
	}

	/**
	 * If a product is searchable by users - 
	 * @return
	 */
	boolean _productIsSearchable(Concept c, Product product) {
		if (c.isActive()) {
			if (product == Product.VIRTUAL_THERAPEUTIC_MOIETY || product == Product.VIRTUAL_MEDICINAL_PRODUCT) {
				return true;
			}
			if (product == Product.ACTUAL_MEDICINAL_PRODUCT) {
				if (Amp.shouldPrescribeVmp(c) == false) {
					return true;
				}
			}
		}
		return false;
	}

	String _prescribingNotes(Concept c, Product product) {
		String result = null;
		if (c.isActive()) {
			switch(product) {
			case ACTUAL_MEDICINAL_PRODUCT:
				if (Amp.shouldPrescribeVmp(c)) {
					result = Amp.getVmp(c).get().getPreferredDescription().getTerm();
				}
				break;
			case ACTUAL_MEDICINAL_PRODUCT_PACK:
				result = Ampp.getAmp(c).map(amp -> amp.getPreferredDescription().getTerm()).orElse(null);
				break;
			case TRADE_FAMILY:
				Tf tf = new Tf(c);
				if (tf.getAmps().anyMatch(Amp::shouldPrescribeVmp)) {
					if (tf.getVtms().findAny().isPresent()) {
						result = tf.getVtms().map(vtm -> vtm.getConcept().getPreferredDescription().getTerm()).collect(Collectors.joining(", "));
					} else {
						long count = tf.getVmps().count();
						if (count > 0) {
							StringBuilder sb = new StringBuilder();
							sb.append("One of ");
							sb.append(count);
							sb.append("VMPs (e.g. ");
							sb.append(tf.getVmps().findAny().get().getConcept().getPreferredDescription().getTerm());
							sb.append(")");
							result = sb.toString();
						}
					}
				}
				break;
			case VIRTUAL_MEDICINAL_PRODUCT:
				Vmp vmp = new Vmp(c);
				if (!vmp.isPrescribable()) { 
					if (vmp.getTfs().count() > 0) {
						result = vmp.getTfs().map(t -> t.getConcept().getPreferredDescription().getTerm()).collect(Collectors.joining(", "));
					} else {
						long count = vmp.getAmps().count();
						if (count > 0) {
							StringBuilder sb = new StringBuilder();
							sb.append("One of ");
							sb.append(count);
							sb.append("AMPs (e.g. ");
							sb.append(vmp.getAmps().findAny().get().getConcept().getPreferredDescription().getTerm());
							sb.append(")");
							result = sb.toString();
						}
					}
				}
				break;
			case VIRTUAL_MEDICINAL_PRODUCT_PACK:
				result = Vmpp.getVmp(c).map(v -> v.getPreferredDescription().getTerm()).orElse(null);
				break;
			case VIRTUAL_THERAPEUTIC_MOIETY:
				Vtm vtm = new Vtm(c);
				if (vtm.getVmps().allMatch(v -> v.isPrescribable() == false)) {
					result = vtm.getTfs().map(t -> t.getConcept().getPreferredDescription().getTerm()).collect(Collectors.joining(", "));
				}
				break;
			default:
				break;

			}
		} else {
			result = "Inactive or outdated";
		}
		return result;
	}
}
