package com.eldrix.terminology.server;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;
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

import com.eldrix.terminology.server.resources.ConceptResource;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Module;
import io.bootique.Bootique;
import io.bootique.jersey.JerseyModule;

public class TestServer {
	final static ExecutorService executor = Executors.newSingleThreadExecutor();
	final static Client client = ClientBuilder.newClient().register(JacksonJsonProvider.class);
	final static WebTarget target = client.target("http://localhost:8080");
	final static ObjectMapper mapper = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_IGNORED_PROPERTIES, false);

	@BeforeClass
	public static void startServer() throws InterruptedException {
		executor.submit(() -> {
			Module jersey = JerseyModule.builder()
					.packageRoot(ConceptResource.class)
					.build();
			Bootique.app(new String[] {"--config=run.yml", "--server"})
			.module(SnomedCTApplication.class)
			.module(jersey)
			.autoLoadModules()
			.run();
		});
		int numberOfTries = 10;
		while (numberOfTries > 0) {
			Thread.sleep(500);
			numberOfTries--;
			try {
				Optional<ClientConcept> cc = performRequest(target.path("snomedct/concepts/24700007"), ClientConcept.class).findFirst();
				if (cc.isPresent()) {
					return;
				}
			}
			catch(Exception e) {
				;	// NOP
			}
		}
		System.err.println("Failed to start server");
		System.exit(1);
	}
	
	private static <T> Stream<T> performRequest(WebTarget t, Class<T> clazz) {
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
	
	@Test
	public void testParsingMedication() {
		@SuppressWarnings("rawtypes")
		Optional<HashMap> pmb = performRequest(
			target.path("snomedct/dmd/parse").queryParam("s", "amlodipine 5mg od"), HashMap.class)
			.findFirst();
		assertTrue(pmb.isPresent());
		assertEquals(Long.valueOf(108537001L), Long.valueOf( (int) pmb.get().get("conceptId")));
		assertEquals(5, pmb.get().get("dose"));
	}
	
	
	@Test
	public void testFilteredSearch() {
		long unfiltered = performRequest(target.path("snomedct/search").queryParam("s", "gastroent"), ClientResultItem.class).count();
		long filtered = performRequest(target.path("snomedct/search").queryParam("s", "gastroent").queryParam("project", "CAVACUTEPAEDS"), ClientResultItem.class).count();
		assertTrue(unfiltered > filtered);
	}
	
	
	@Test
	public void testProject() {
		@SuppressWarnings("rawtypes")
		Optional<HashMap> r = performRequest(target.path("projects/41").queryParam("include", "commonConcepts"), HashMap.class).findFirst();
		assertEquals("CAVACUTEPAEDS", r.get().get("name"));
		@SuppressWarnings({ "unchecked", "rawtypes" })
		List<HashMap> common = (List<HashMap>) r.get().get("commonConcepts");
		List<Long> commonConcepts = common.stream().map(hm -> mapper.convertValue(hm, ClientConcept.class)).map(c -> c.conceptId).collect(Collectors.toList());
		Stream<ClientResultItem> search = performRequest(target.path("snomedct/search").queryParam("s", "gastroent").queryParam("project", "CAVACUTEPAEDS"), ClientResultItem.class);
		assertTrue(search.allMatch(ri -> commonConcepts.contains(ri.conceptId)));
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
