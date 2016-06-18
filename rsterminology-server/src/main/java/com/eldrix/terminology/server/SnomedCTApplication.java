package com.eldrix.terminology.server;

import com.eldrix.terminology.server.commands.BuildParentCache;
import com.eldrix.terminology.server.commands.CreateIndex;
import com.eldrix.terminology.server.commands.ImportRf1;
import com.google.inject.Binder;
import com.google.inject.Module;
import com.google.inject.multibindings.Multibinder;
import com.nhl.bootique.BQCoreModule;
import com.nhl.bootique.Bootique;
import com.nhl.bootique.command.Command;
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
		Multibinder<Command> multibinder = BQCoreModule.contributeCommands(binder);
		multibinder.addBinding().to(CreateIndex.class);
		multibinder.addBinding().to(BuildParentCache.class);
		multibinder.addBinding().to(ImportRf1.class);
	}
}
