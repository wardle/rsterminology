package com.eldrix.terminology.server.commands;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.cayenne.ObjectContext;
import org.apache.cayenne.configuration.server.ServerRuntime;
import org.apache.cayenne.query.ObjectSelect;

import com.eldrix.terminology.snomedct.Concept;
import com.eldrix.terminology.snomedct.Semantic.DmdProduct;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.nhl.bootique.cli.Cli;
import com.nhl.bootique.command.CommandMetadata;
import com.nhl.bootique.command.CommandOutcome;
import com.nhl.bootique.command.CommandWithMetadata;

public class Browser extends CommandWithMetadata {

	@Inject 
	public Provider<ServerRuntime> cayenne;

	private Concept _currentConcept;

	private static CommandMetadata createMetadata() {
		return CommandMetadata.builder(Browser.class)
				.description("Browse and search SNOMED-CT interactively.")
				.build();
	}

	public Browser() {
		super(createMetadata());
	}

	@Override
	public CommandOutcome run(Cli cli) {
		System.out.println("SNOMED-CT interactive browser and search.");
		boolean quit = false;
		while (!quit) {
			System.out.println("Enter command: " + (currentConcept() != null ? "Current: "+currentConcept().getConceptId()  + currentConcept().getFullySpecifiedName(): "") + "");
			BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(System.in));
			try {
				String line = bufferedReader.readLine();
				quit = performCommand(line.trim());
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		return CommandOutcome.succeeded();
	}

	private boolean performCommand(String line) {
		if (performQuit(line) == true) {
			return true;
		}
		performHelp(line);
		performShowConcept(line);
		performShowDescriptions(line);
		return false;
	}
	private boolean performQuit(String line) {
		return "q".equalsIgnoreCase(line) || "quit".equalsIgnoreCase(line);
	}

	private void performHelp(String line) {
		if ("h".equalsIgnoreCase(line) || "?".equalsIgnoreCase(line) || "help".equalsIgnoreCase(line)) {
			System.out.println("help/h/? : This help");
			System.out.println("quit/q   : Quit");
			System.out.println("c <conceptId> : Display the specified concept.");
			System.out.println("s        : Show information for currently selected concept");
			System.out.println("d        : Show descriptions for currently selected concept");
		}
	}

	private void setCurrentConcept(Concept c) {
		_currentConcept = c;
	}
	private Concept currentConcept() {
		return _currentConcept;
	}

	private void performShowConcept(String line) {
		Matcher m = Pattern.compile("c (\\d*)").matcher(line);
		if (m.matches()) {
			try {
				long conceptId = Long.parseLong(m.group(1));
				ObjectContext context = cayenne.get().newContext();
				Concept c = ObjectSelect.query(Concept.class, Concept.CONCEPT_ID.eq(conceptId)).selectOne(context);
				if (c != null) {
					setCurrentConcept(c);
					printConcept(c, false);					
				}
				else {
					System.err.println("No concept found with identifier: "+conceptId);
				}
			}
			catch (NumberFormatException e) {
				System.err.println("Invalid concept identifier");
			}
		}
		if ("s".equalsIgnoreCase(line) && currentConcept() != null) {
			printConcept(currentConcept(), true);
		}
	}

	private void performShowDescriptions(String line) {
		if ("d".equalsIgnoreCase(line) && currentConcept() != null) {
			currentConcept().getDescriptions().stream().forEach(d -> {
				System.out.println("Description " + d.getDescriptionId() + ": " + d.getTerm());
			});
		}
	}
	
	private static void printConcept(Concept c, boolean includeRelations) {
		StringBuilder sb = new StringBuilder();
		sb.append("Concept : " + c.getConceptId() + " : " + c.getFullySpecifiedName() + " DM&D structure : " + DmdProduct.productForConcept(c));
		if (includeRelations) {
			c.getChildConcepts().forEach(child -> sb.append("\n  childConcept: " + child.getFullySpecifiedName()));
			c.getParentConcepts().forEach(parent -> sb.append("\n  parentConcept: " + parent.getFullySpecifiedName()));
			c.getChildRelationships().forEach(r -> {
				sb.append("\n  childRelation: " + r.getSourceConcept().getFullySpecifiedName() + " [" + r.getRelationshipTypeConcept().getFullySpecifiedName() + "] " + r.getTargetConcept().getFullySpecifiedName());
			});
			c.getParentRelationships().forEach(r -> {
				sb.append("\n  parentRelation: " + r.getSourceConcept().getFullySpecifiedName() + " [" + r.getRelationshipTypeConcept().getFullySpecifiedName() + "] " + r.getTargetConcept().getFullySpecifiedName());
			});
		}
		System.out.println(sb.toString());
	}
}
