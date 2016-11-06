package com.eldrix.terminology.server.commands;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.List;

import org.apache.cayenne.ObjectContext;
import org.apache.cayenne.ResultBatchIterator;
import org.apache.cayenne.configuration.server.ServerRuntime;
import org.apache.cayenne.query.SelectQuery;

import com.eldrix.terminology.snomedct.Concept;
import com.eldrix.terminology.snomedct.semantic.Amp;
import com.eldrix.terminology.snomedct.semantic.Category;
import com.eldrix.terminology.snomedct.semantic.Dmd;
import com.eldrix.terminology.snomedct.semantic.Dmd.Product;
import com.eldrix.terminology.snomedct.semantic.Vmp;
import com.eldrix.terminology.snomedct.semantic.Vtm;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.opencsv.CSVWriter;

import io.bootique.application.CommandMetadata;
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
			String[] row = new String[] {"textDescription", "localCode","readCode","accessCode", "isActive" };
			csv.writeNext(row);
			try (ResultBatchIterator<Concept> iterator = query.batchIterator(context, 500)) {
				while (iterator.hasNext()) {
					List<Concept> batch = iterator.next();
					for (Concept c : batch) {
						Dmd.Product.productForConcept(c).ifPresent(p -> {
							row[0] = c.getPreferredDescription().getTerm();
							row[1] = "";
							row[2] = c.getConceptId().toString();
							row[3] = p.abbreviation();
							row[4] = String.valueOf(_productIsSearchable(c, p));
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

	/**
	 * If a product is searchable by users - 
	 * @return
	 */
	boolean _productIsSearchable(Concept c, Product product) {
		if (c.isActive()) {
			switch(product) {
			case VIRTUAL_THERAPEUTIC_MOIETY:
				if (Vtm.getVmps(c).anyMatch(vmp -> Vmp.isPrescribable(vmp) == true)) {
					return true;
				}
				break;
			case VIRTUAL_MEDICINAL_PRODUCT:
				if (Vmp.isPrescribable(c)) {
					return true;
				}
				break;
			case ACTUAL_MEDICINAL_PRODUCT:
				if (Amp.shouldPrescribeVmp(c) == false) {
					return true;
				}
				break;
			default:
				return false;
			}

		}
		return false;
	}
}
