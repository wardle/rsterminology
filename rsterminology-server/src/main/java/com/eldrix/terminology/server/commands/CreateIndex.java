package com.eldrix.terminology.server.commands;

import java.io.IOException;

import org.apache.cayenne.ObjectContext;
import org.apache.cayenne.configuration.server.ServerRuntime;

import com.eldrix.terminology.snomedct.Search;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.nhl.bootique.cli.Cli;
import com.nhl.bootique.command.CommandMetadata;
import com.nhl.bootique.command.CommandOutcome;
import com.nhl.bootique.command.CommandWithMetadata;

/**
 * Create or update a lucene index.
 * TODO: Allow configuration of location of index.
 * TODO: Migrate to a modern version of lucene.
 * @author Mark Wardle
 *
 */
public class CreateIndex extends CommandWithMetadata {

	@Inject 
	public Provider<ServerRuntime> cayenne;

	private static CommandMetadata createMetadata() {
		return CommandMetadata.builder(CreateIndex.class)
				.description("Builds a new lucene index.")
				.build();
	}

	public CreateIndex() {
		super(createMetadata());
	}

	@Override
	public CommandOutcome run(Cli cli) {
		System.out.println("Building lucene index.... ");
		ObjectContext context = cayenne.get().newContext();
		try {
			Search.processAllDescriptions(context);
			return CommandOutcome.succeeded();
		} catch (IOException e) {
			return CommandOutcome.failed(-1, e);
		}
	}
}
