package com.eldrix.terminology.server;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Stream;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.GenericType;
import javax.ws.rs.core.MediaType;

import org.codehaus.jackson.jaxrs.JacksonJsonProvider;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Module;
import com.nhl.bootique.Bootique;
import com.nhl.bootique.jersey.JerseyModule;

public class TestServer {
	final static ExecutorService executor = Executors.newSingleThreadExecutor();
	final Client client = ClientBuilder.newClient().register(JacksonJsonProvider.class);
	final WebTarget target = client.target("http://localhost:8080");
	final static ObjectMapper mapper = new ObjectMapper();

	@BeforeClass
	public static void startServer() throws InterruptedException {
		executor.submit(() -> {
			Module jersey = JerseyModule.builder()
					.packageRoot(SnomedCTResource.class)
					.build();
			Bootique.app(new String[] {"--config=run.yml", "--server"})
			.module(SnomedCTApplication.class)
			.module(jersey)
			.autoLoadModules()
			.run();
		});
		Thread.sleep(3000);
	}
	
	private <T> Stream<T> performRequest(WebTarget t, Class<T> clazz) {
		Map<String,?> r1 = t.request(MediaType.APPLICATION_JSON).get(new GenericType<Map<String, ?>>() {});
		List<?> r2 = (List<?>) r1.get("data");
		return r2.stream().map(o -> mapper.convertValue(o, clazz));
	}
	
	@Test
	public void testFetchConcept() throws InterruptedException {
		ClientConcept cc = performRequest(target.path("snomedct/concepts/24700007"), ClientConcept.class).findFirst().get();
		assertEquals(24700007L, cc.conceptId);
	}
	
	@Test
	public void testSearch() {
		ClientResultItem ri = performRequest(
				target.path("snomedct/search").queryParam("s", "multip scler").queryParam("root", "64572001"), 
				ClientResultItem.class).findFirst().get();
		assertEquals(24700007L, ri.conceptId);
	}
	
	@Test
	public void testSynonyms() {
		assertTrue(performRequest(target.path("snomedct/synonyms").queryParam("s", "heart attack"), String.class)
			.anyMatch(s -> s.equals("Myocardial infarction")));
	}
	
	public static class ClientConcept {
		public long id;
		public long conceptId;
		public String ctvId;
		public int conceptStatusCode;
		public String fullySpecifiedName; 
		public boolean isPrimitive;
		public String snomedId;
	}

	public static class ClientResultItem {
		public long conceptId;
		public String term;
		public String preferredTerm;
	}
	
	@AfterClass
	public static void stopServer() {
		executor.shutdown();
	}
}
