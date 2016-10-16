package com.eldrix.terminology.server;

import com.eldrix.terminology.server.commands.Browser;
import com.eldrix.terminology.server.commands.BuildParentCache;
import com.eldrix.terminology.server.commands.BuildIndex;
import com.eldrix.terminology.server.commands.ImportRf1;
import com.eldrix.terminology.server.resources.CrossMapResource;
import com.eldrix.terminology.server.resources.ProjectResource;
import com.eldrix.terminology.server.resources.SearchResource;
import com.eldrix.terminology.server.resources.ConceptResource;
import com.google.inject.Binder;
import com.google.inject.Module;
import com.google.inject.multibindings.Multibinder;
import io.bootique.BQCoreModule;
import io.bootique.Bootique;
import io.bootique.command.Command;
import io.bootique.jersey.JerseyModule;

/**
 * A runnable Bootique (http://bootique.io/) application.
 */
public class SnomedCTApplication implements Module {

	public static void main(String[] args) throws Exception {

		Bootique.app(args)
		.autoLoadModules()
		.module(SnomedCTApplication.class)
		.run();
	}

	@Override
	public void configure(Binder binder) {
		Multibinder<Command> multibinder = BQCoreModule.contributeCommands(binder);
		multibinder.addBinding().to(BuildIndex.class);
		multibinder.addBinding().to(BuildParentCache.class);
		multibinder.addBinding().to(ImportRf1.class);
		multibinder.addBinding().to(Browser.class);
		
		Multibinder<Object> jersey = JerseyModule.contributeResources(binder);
		jersey.addBinding().to(SearchResource.class);
		jersey.addBinding().to(ConceptResource.class);
		jersey.addBinding().to(ProjectResource.class);
		jersey.addBinding().to(CrossMapResource.class);
	}
}
