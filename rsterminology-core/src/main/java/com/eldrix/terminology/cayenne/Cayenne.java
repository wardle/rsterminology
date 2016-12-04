package com.eldrix.terminology.cayenne;

import org.apache.cayenne.configuration.server.ServerRuntime;

public class Cayenne {
	public static ServerRuntime createServerRuntime() {
		return new ServerRuntime("cayenne-project.xml");        
	}
}
