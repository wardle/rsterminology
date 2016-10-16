package com.eldrix.terminology.server.commands;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.List;

import org.apache.cayenne.ObjectContext;
import org.apache.cayenne.configuration.server.ServerRuntime;

import com.eldrix.terminology.snomedct.Search;
import com.google.inject.Inject;
import com.google.inject.Provider;

import io.bootique.application.CommandMetadata;
import io.bootique.cli.Cli;
import io.bootique.command.CommandOutcome;
import io.bootique.command.CommandWithMetadata;

/**
 * Create or update a lucene index.
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
		List<String> args = cli.standaloneArguments();
		String filename = null;
		if (args.size() == 1) {
			filename = args.get(0);
		}
		else {
			System.out.println("Enter filename:");
			BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(System.in));
			try {
				filename = bufferedReader.readLine();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		if (filename != null) {
			System.out.println("Building lucene index at location: " + filename);
			ObjectContext context = cayenne.get().newContext();
			try {
				Search.getInstance(filename).processAllDescriptions(context);
				return CommandOutcome.succeeded();
			} catch (IOException e) {
				e.printStackTrace();
				return CommandOutcome.failed(-1, e);
			}
		}
		return CommandOutcome.failed(1, "No file specified");
	}
}
