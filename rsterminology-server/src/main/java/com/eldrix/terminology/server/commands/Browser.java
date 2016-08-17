package com.eldrix.terminology.server.commands;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.cayenne.ObjectContext;
import org.apache.cayenne.configuration.server.ServerRuntime;
import org.apache.cayenne.query.ObjectSelect;

import com.eldrix.terminology.snomedct.Concept;
import com.eldrix.terminology.snomedct.Search;
import com.eldrix.terminology.snomedct.Search.ResultItem;
import com.eldrix.terminology.snomedct.Semantic.Dmd;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.nhl.bootique.cli.Cli;
import com.nhl.bootique.command.CommandMetadata;
import com.nhl.bootique.command.CommandOutcome;
import com.nhl.bootique.command.CommandWithMetadata;

/**
 * This is a very simply command-line SNOMED-CT browser.
 * It is extremely rudimentary but it is intended to be help quickly find a concept and browse the hierarchy. 
 * @author Mark Wardle
 *
 */
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
			if (currentConcept() != null) {
				System.out.println("****************************************");
				System.out.println("Current: "+currentConcept().getConceptId()  + ": " + currentConcept().getFullySpecifiedName());
				System.out.println("****************************************");
			}
			System.out.println("Enter command:");
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
		performShowChildRelationships(line);
		performFind(line);
		return false;
	}
	private boolean performQuit(String line) {
		return "q".equalsIgnoreCase(line) || "quit".equalsIgnoreCase(line);
	}

	private void performHelp(String line) {
		if ("h".equalsIgnoreCase(line) || "?".equalsIgnoreCase(line) || "help".equalsIgnoreCase(line)) {
			System.out.println("help/h/? : This help");
			System.out.println("quit/q   : Quit");
			System.out.println("s <conceptId> : Show or change the currently selected concept");
			System.out.println("d        : Show descriptions for currently selected concept");
			System.out.println("c        : Show child relationships for currently selected concept");
			System.out.println("f <name> : Find a concept matching the specified name");
		}
	}

	private void setCurrentConcept(Concept c) {
		_currentConcept = c;
	}
	private Concept currentConcept() {
		return _currentConcept;
	}

	private void performShowConcept(String line) {
		Matcher m = Pattern.compile("s (\\d*)").matcher(line);
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
		if ("s".equalsIgnoreCase(line.trim()) && currentConcept() != null) {
			printConcept(currentConcept(), true);
		}
	}

	private void performShowDescriptions(String line) {
		if ("d".equalsIgnoreCase(line) && currentConcept() != null) {
			System.out.println("Descriptions (synonyms):");
			currentConcept().getDescriptions().stream().forEach(d -> {
				System.out.println("  |-- " + d.getDescriptionId() + ": " + d.getTerm());
			});
		}
	}
	
	private void performShowChildRelationships(String line) {
		if ("c".equalsIgnoreCase(line.trim()) && currentConcept() != null) {
			StringBuilder sb = new StringBuilder();
			sb.append("Child relationships:");
			currentConcept().getChildRelationships().forEach(r -> {
				sb.append("\n  |    |-" + r.getSourceConcept().getFullySpecifiedName()  + " " +r.getSourceConceptId() + " "+ " [" + r.getRelationshipTypeConcept().getFullySpecifiedName() + "] " + r.getTargetConcept().getFullySpecifiedName());
			});
			System.out.println(sb.toString());
		}
	}

	private static void printConcept(Concept c, boolean includeRelations) {
		StringBuilder sb = new StringBuilder();
		sb.append("Concept : " + c.getConceptId() + " : " + c.getFullySpecifiedName());
		if (Dmd.Product.productForConcept(c) != null) {
			sb.append("  DM&D concept: " + Dmd.Product.productForConcept(c));
		}
		if (includeRelations) {
			sb.append("\n  |-Recursive parents:");
			c.getRecursiveParentConcepts().forEach(parent -> 
			sb.append("\n  |    |-" + parent.getConceptId() + " " + parent.getFullySpecifiedName()));
			sb.append("\n  |-Parent relationships:");
			c.getParentRelationships().forEach(r -> {
				sb.append("\n  |    |-" + r.getSourceConcept().getFullySpecifiedName() + " [" + r.getRelationshipTypeConcept().getFullySpecifiedName() + "] " + r.getTargetConcept().getFullySpecifiedName() + " " + r.getTargetConceptId());
			});
		}
		System.out.println(sb.toString());
	}
	
	private void performFind(String line) {
		Matcher m = Pattern.compile("^f(?<number>\\d*)?\\s+(\\[(?<roots>.*?)\\])?\\s?(?<search>.*)").matcher(line);
		if (m.matches()) {
			String number = m.group("number");		// number of results requested
			String roots = m.group("roots");		// root identifiers
			String search = m.group("search");
			try {
				int hits = 20;
				if (number != null && number.length() > 0) {
					try {
						int h = Integer.parseInt(number);
						hits = h;
					}
					catch (NumberFormatException e) {
						;
					}
				}
				long[] rootConceptIds = new long[] { 138875005 };
				if (roots != null && roots.length() > 0) {
					long[] r = Search.parseLongArray(roots);
					if (r.length > 0) {
						rootConceptIds = r;
					}
				}
				List<ResultItem> results = Search.getInstance().newBuilder().searchFor(search).setMaxHits(hits).withRecursiveParent(rootConceptIds).build().search();
				if (results.size() > 0) {
					results.forEach(ri -> {
						System.out.println(ri.getTerm() + " -- " + ri.getPreferredTerm() + " -- " + ri.getConceptId());
					});
				}
				else {
					System.out.println("No results found");
				}
				
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}			
		}
		
	}
}
