package com.eldrix.terminology.server;

import com.eldrix.terminology.server.commands.Browser;
import com.eldrix.terminology.server.commands.BuildIndex;
import com.eldrix.terminology.server.commands.BuildParentCache;
import com.eldrix.terminology.server.commands.ExportDmdMain;
import com.eldrix.terminology.server.commands.ImportRf1;
import com.eldrix.terminology.server.resources.ConceptResource;
import com.eldrix.terminology.server.resources.CrossMapResource;
import com.eldrix.terminology.server.resources.ProjectResource;
import com.eldrix.terminology.server.resources.SearchResource;
import com.google.inject.Binder;
import com.google.inject.Module;

import io.bootique.BQCoreModule;
import io.bootique.Bootique;
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
		BQCoreModule.extend(binder)
			.addCommand(BuildIndex.class)
			.addCommand(BuildParentCache.class)
			.addCommand(ImportRf1.class)
			.addCommand(Browser.class)
			.addCommand(ExportDmdMain.class);
		JerseyModule.extend(binder)
			.addResource(SearchResource.class)
			.addResource(ConceptResource.class)
			.addResource(ProjectResource.class)
			.addResource(CrossMapResource.class);
	}
}
