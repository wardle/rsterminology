package com.eldrix.terminology.server;

import com.google.inject.Binder;
import com.google.inject.Module;
import com.nhl.bootique.BQCoreModule;
import com.nhl.bootique.Bootique;
import com.nhl.bootique.cli.CliOption;
import com.nhl.bootique.jersey.JerseyModule;

/**
 * A runnable Bootique (http://bootique.io/) application.
 */
public class SnomedCTApplication implements Module {

	public static void main(String[] args) throws Exception {

		Module jersey = JerseyModule.builder()
				.packageRoot(ConceptResource.class)
				.build();
		Bootique.app(args)
		.module(SnomedCTApplication.class)
		.module(jersey)
		.autoLoadModules()
		.run();
	}

	@Override
	public void configure(Binder binder) {
		BQCoreModule.contributeCommands(binder)
		.addBinding()
		.to(CreateIndex.class);
		CliOption option = CliOption
				.builder("email", "An admin email address")
				.valueRequired("email_address")
				.build();
		BQCoreModule.contributeOptions(binder)
		.addBinding()
		.toInstance(option);
	}
}
