package com.eldrix.terminology.server.commands;

import org.apache.cayenne.ObjectContext;
import org.apache.cayenne.configuration.server.ServerRuntime;

import com.eldrix.terminology.snomedct.ParentCache;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.nhl.bootique.cli.Cli;
import com.nhl.bootique.command.CommandMetadata;
import com.nhl.bootique.command.CommandOutcome;
import com.nhl.bootique.command.CommandWithMetadata;

public class BuildParentCache extends CommandWithMetadata {

	@Inject 
	public Provider<ServerRuntime> cayenne;

	private static CommandMetadata createMetadata() {
		return CommandMetadata.builder(BuildParentCache.class)
				.description("Rebuilds the concept parent cache. Use after updating concepts from a new release.")
				.build();
	}

	public BuildParentCache() {
		super(createMetadata());
	}

	@Override
	public CommandOutcome run(Cli cli) {
		ObjectContext context = cayenne.get().newContext();
		ParentCache.buildParentCache(context);
		return CommandOutcome.succeeded();
	}
}
