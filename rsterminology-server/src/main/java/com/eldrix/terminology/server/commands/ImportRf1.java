package com.eldrix.terminology.server.commands;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.List;

import org.apache.cayenne.configuration.server.ServerRuntime;

import com.eldrix.terminology.snomedct.parse.ParseRf1;
import com.google.inject.Inject;
import com.google.inject.Provider;

import io.bootique.application.CommandMetadata;
import io.bootique.cli.Cli;
import io.bootique.command.CommandOutcome;
import io.bootique.command.CommandWithMetadata;

public class ImportRf1 extends CommandWithMetadata {

	@Inject
	public Provider<ServerRuntime> cayenne;

	private static CommandMetadata createMetadata() {
		return CommandMetadata.builder(ImportRf1.class)
				.description("Import concepts, descriptions and relationships for SNOMED-CT in RF1 format.")
				.build();
	}

	public ImportRf1() {
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
		System.out.println("Importing file: " + filename);
		try {
			ParseRf1.processFile(cayenne.get(), filename);
			return CommandOutcome.succeeded();
		} catch (IOException e) {
			e.printStackTrace();
			return CommandOutcome.failed(-1, e);
		}
		}
		return CommandOutcome.failed(1, "No file specified");
	}
}
