package com.eldrix.terminology.medicine;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;

import org.apache.lucene.index.CorruptIndexException;
import org.apache.lucene.queryparser.classic.ParseException;
import org.junit.Test;

import com.eldrix.terminology.medicine.Medication.Frequency;
import com.eldrix.terminology.medicine.Medication.Route;
import com.eldrix.terminology.medicine.Medication.Units;

public class MedicationParserTest {
	
	
	
	@Test
	public void testStringTokenizer() {
		assertArrayEquals(ParsedMedicationBuilder._tokenizeString("amlodipine 5mg od"),
				new String[]{"amlodipine", "5mg", "od"});
		assertArrayEquals(ParsedMedicationBuilder._tokenizeString("\"amlodipine\" 5mg od"),
				new String[]{"amlodipine", "5mg", "od"});
		assertArrayEquals(ParsedMedicationBuilder._tokenizeString("'co-coreldopa 50/100' i 5/day"),
				new String[]{"co-coreldopa 50/100", "i", "5/day"});
		assertArrayEquals(ParsedMedicationBuilder._tokenizeString("bendroflumethiazide     2.5mg  \t    od  PO"),
				new String[]{"bendroflumethiazide","2.5mg","od","po"});
		assertArrayEquals(ParsedMedicationBuilder._tokenizeString("\"st john's wort\" 1 od po"),
				new String[]{"st john's wort","1","od","po"});
	}

	@Test
	public void testAsRequiredMatcher() {
		assertTrue(ParsedMedicationBuilder.matchAsRequired("prn"));
		assertFalse(ParsedMedicationBuilder.matchAsRequired("ddd"));
	}

	@Test
	public void testFrequencyMatcher() {
		assertNotNull(ParsedMedicationBuilder.matchFrequency("od"));
		assertNotNull(ParsedMedicationBuilder.matchFrequency("bd"));
		assertNotNull(ParsedMedicationBuilder.matchFrequency("tds"));
		assertEquals(ParsedMedicationBuilder.matchFrequency("tid"), Frequency.THREE_TIMES_DAILY);
		assertNotNull(ParsedMedicationBuilder.matchFrequency("qds"));
		assertNotNull(ParsedMedicationBuilder.matchFrequency("5/day"));
		assertNotNull(ParsedMedicationBuilder.matchFrequency("7/day"));
		assertNotNull(ParsedMedicationBuilder.matchFrequency("12/d"));
		assertNotNull(ParsedMedicationBuilder.matchFrequency("alt"));
		assertNotNull(ParsedMedicationBuilder.matchFrequency("12/day"));
		assertNotNull(ParsedMedicationBuilder.matchFrequency("1/w"));
		assertEquals(ParsedMedicationBuilder.matchFrequency("8/day"), Frequency.EIGHT_TIMES_DAILY);
		assertNull(ParsedMedicationBuilder.matchFrequency("2.5mg"));
	}

	@Test
	public void testDosing() {
		assertEquals(ParsedMedicationBuilder.matchDosing("5mg").dose().compareTo(new BigDecimal(5)), 0);
		assertEquals(ParsedMedicationBuilder.matchDosing("5mg").units(), Units.MILLIGRAM);
		assertEquals(ParsedMedicationBuilder.matchDosing("10mcg").dose().compareTo(new BigDecimal(10)), 0);
		assertEquals(ParsedMedicationBuilder.matchDosing("10mcg").units(), Units.MICROGRAM);
		assertEquals(ParsedMedicationBuilder.matchDosing("2.5mg").dose().compareTo(new BigDecimal(2.5)), 0);
		assertEquals(ParsedMedicationBuilder.matchDosing("2.5mg").units(), Units.MILLIGRAM);
		assertEquals(ParsedMedicationBuilder.matchDosing("2.5 mg").dose().compareTo(new BigDecimal(2.5)), 0);
		assertEquals(ParsedMedicationBuilder.matchDosing("2.5 mg").units(), Units.MILLIGRAM);
		assertEquals(ParsedMedicationBuilder.matchDosing("0.5mg").dose().compareTo(new BigDecimal(0.5)), 0);
		assertEquals(ParsedMedicationBuilder.matchDosing("0.5g").units(), Units.GRAM);
		assertEquals(ParsedMedicationBuilder.matchDosing("1u").dose().compareTo(new BigDecimal(1)), 0);
		assertEquals(ParsedMedicationBuilder.matchDosing("1u").units(), Units.UNITS);
		assertEquals(ParsedMedicationBuilder.matchDosing("5units").dose().compareTo(new BigDecimal(5)), 0);
		assertEquals(ParsedMedicationBuilder.matchDosing("5units").units(), Units.UNITS);
		assertEquals(ParsedMedicationBuilder.matchDosing("5 units").dose().compareTo(new BigDecimal(5)), 0);
		assertEquals(ParsedMedicationBuilder.matchDosing("5 units").units(), Units.UNITS);

		assertNull(ParsedMedicationBuilder.matchDosing("12/day"));
		assertNull(ParsedMedicationBuilder.matchDosing("prn"));
		assertNull(ParsedMedicationBuilder.matchDosing("1"));
	}

	@Test
	public void testParsingMedications() throws CorruptIndexException, IOException, ParseException {

		ParsedMedicationBuilder pmb = new ParsedMedicationBuilder();
		ParsedMedication med1 = pmb.parseString("amlodipine 5mg od").build();
		assertEquals(med1.drugName(), "amlodipine");
		assertEquals(Long.valueOf(108537001L), med1.conceptId());
		assertEquals(med1.dose(), new BigDecimal(5));
		assertEquals(med1.units(), Units.MILLIGRAM);
		assertEquals(med1.frequency(), Frequency.ONCE_DAILY);

		
		ParsedMedication med2 = pmb.parseString("co-careldopa 25mg/250mg 1tab qds").build();
		assertEquals(med2.drugName(), "co-careldopa 25mg/250mg");
		assertNotNull(med2.conceptId());
		assertEquals(377270003, med2.conceptId() != null ? med2.conceptId().longValue() : 0 );
		assertEquals(med2.dose(), new BigDecimal(1));
		assertEquals(med2.units(), Units.TABLETS);
		assertEquals(med2.frequency(), Frequency.FOUR_TIMES_DAILY);
		
		ParsedMedication med3 = pmb.parseString("bendroflumethiazide 2.5mg od").build();
		assertEquals(med3.drugName(), "bendroflumethiazide");
		assertEquals(med3.dose(), new BigDecimal(2.5));
		assertEquals(med3.units(), Units.MILLIGRAM);
		assertEquals(med3.frequency(), Frequency.ONCE_DAILY);
		assertTrue(med3.conceptId() == 91135008L);

		ParsedMedication med4 = pmb.parseString("Sinemet CR 1tab od").build();
		assertTrue(med4.conceptId() == 9341401000001101L);		// sinemet CR (product)
		assertEquals(med4.dose(), new BigDecimal(1));
		assertEquals(med4.units(), Units.TABLETS);

		ParsedMedication med5 = pmb.parseString("Sinemet CR 0.5tab od").build();;
		assertTrue(med5.conceptId() == 9341401000001101L);		// sinemet CR (product)
		assertEquals(med5.dose(), new BigDecimal(0.5));
		assertEquals(med5.units(), Units.TABLETS);

		ParsedMedication med6 = pmb.parseString("bendroflumethiazide 2.5mg od").build();
		assertTrue(med6.conceptId() == 91135008L);

		assertNotNull(pmb.parseString("co-careldopa 1t od").build().conceptId());
		
		ParsedMedication med7 = pmb.parseString("co-careldopa 25mg/250mg 0.5tab qds").build();
		//assertEquals(med7.drugName(), "co-careldopa 25mg/250mg");
		assertEquals(Long.valueOf(377270003L), med7.conceptId());
		assertEquals(med7.dose(), new BigDecimal(0.5));
		assertEquals(med7.units(), Units.TABLETS);
		assertEquals(med7.frequency(), Frequency.FOUR_TIMES_DAILY);
		
		ParsedMedication med8 = pmb.parseString("apomorphine 2.5mg /h").build();
		assertEquals(med8.drugName(), "apomorphine");
		assertEquals(med8.dose(), new BigDecimal(2.5));
		assertEquals(med8.units(), Units.MILLIGRAM);
		assertEquals(med8.frequency(), Frequency.PER_HOUR);
	}

	@Test
	public void testEqualityChecks() throws CorruptIndexException, IOException, ParseException {
		;
		ParsedMedicationBuilder pmb = new ParsedMedicationBuilder();
		ParsedMedication med1 = pmb.parseString("amlodipine 5mg od").build();
		ParsedMedication med2 = pmb.parseString("amlodipine   5mg 1/day").build();
		ParsedMedication med3 = pmb.parseString("amlodipine 5mg 2/day").build();
		ParsedMedication med4 = pmb.parseString("amlodipine 5mg bd").build();
		ParsedMedication med5 = pmb.parseString("  AMLODIPINE    5mg     od").build();

		ParsedMedication med6 = pmb.parseString("aspirin 75mg od").build();
		ParsedMedication med7 = pmb.parseString("amlodipine M/R 5mg bd").build();

		ParsedMedication med8 = pmb.parseString("simvastatin").build();
		ParsedMedication med9 = pmb.parseString("simvastatin 40mg od").build();
		ParsedMedication med10 = pmb.parseString("atorvastatin 40mg od").build();

		ParsedMedication med11 = pmb.parseString("simvastatin 40mg od PO").build();

		ParsedMedication med12 = pmb.parseString("paracetamol 1g po qds").build();
		ParsedMedication med13 = pmb.parseString("acetaminophen 1g po qds").build();
		ParsedMedication med14 = pmb.parseString("acetamnophen 1g po qds").build();

		ParsedMedication med15 = pmb.parseString("amlodipine 5.0mg od").build();
		ParsedMedication med16 = pmb.parseString("amlodipine 5.2mg od").build();
		ParsedMedication med17 = pmb.parseString("amlodipine 5.00mg od").build();

		ParsedMedication med18 = pmb.parseString("paracetamol 1000mg po qds").build();

		System.out.println(med1);
		System.out.println(med2);

		assertTrue(med1.equals(med2));
		assertTrue(med2.equals(med1));
		assertFalse(med1.equals(med3));
		assertFalse(med1.equals(null));
		assertTrue(med3.equals(med4));
		assertTrue(med5.equals(med1));
		assertFalse(med6.equals(med1));
		assertFalse(med7.equals(med1));
		assertFalse(med8.equals(med9));
		assertEquals(med8.route(), Route.ORAL);
		assertNull(med8.dose());
		assertFalse(med9.equals(med10));
		assertTrue(med11.equals(med9));
		assertEquals(med12.conceptId(), med13.conceptId());
		assertFalse(med12.equals(med14));
		assertFalse(med1.equals(null));
		assertTrue(med1.equals(med1));
		assertEquals(med1.hashCode(), med1.hashCode());
		assertEquals(med12.hashCode(), med13.hashCode());
		assertFalse(med13.hashCode() != med14.hashCode());
		assertEquals(med1, med15);
		assertEquals(med1.hashCode(), med15.hashCode());
		assertFalse(med1.equals(med16));
		assertTrue(med1.equals(med15));
		assertEquals(med1, med17);
		assertEquals(med12, med18);
		assertEquals(med12.hashCode(), med18.hashCode());


		HashSet<ParsedMedication> set = new HashSet<>();
		set.add(med1);
		assertTrue(set.contains(med1));
		assertFalse(set.contains(med3));
		assertTrue(set.contains(med5));
		assertTrue(set.contains(med15));
		assertTrue(set.size() == 1);
		set.add(med5);
		assertTrue(set.size() == 1);
		set.add(med3);
		assertEquals(set.size(), 2);

		/*
		 * While it doesn't matter if the hashcodes are equal for different drugs,
		 * this test helped in identifying our hashcode algorithm returned zero every time!
		 */
		assertFalse(med1.hashCode() == med3.hashCode());
		assertFalse(med11.hashCode() == med12.hashCode());
		assertFalse(med1.hashCode() == 0);
	}

	@Test
	public void testSpecialEquality() throws CorruptIndexException, IOException, ParseException {
		;
		ParsedMedicationBuilder pmb = new ParsedMedicationBuilder();
		pmb.setDrugName("acetaminophen");
		pmb.setDose(new BigDecimal(1));
		pmb.setUnits(Units.GRAM);
		pmb.setAsRequired(true);
		pmb.setFrequency(Frequency.FOUR_TIMES_DAILY);
		ParsedMedication m1 = pmb.build();			// doesn't have a concept identifier
		pmb.setMedicationConceptId(90332006L);		// add a concept id acetaminophen
		ParsedMedication m2 = pmb.build();			// does have a concept identifier
		assertEquals(m1, m2);

		ParsedMedication m3 = pmb.parseString("paracetamol 1g PO qds PRN").build();
		ParsedMedication m4 = pmb.parseString("paracetamol product 1g PO qds PRN").build();
		assertEquals(m1, m3);
		assertEquals(m2, m4);
		assertEquals(m1, m4);		// should be equal as concepts match
		assertEquals(m3,m4);		// should be equal as concepts match
		assertEquals(m3.hashCode(), m4.hashCode());
	}

	@Test
	public void testCopying() throws CorruptIndexException, IOException, ParseException {
		;
		ParsedMedicationBuilder pmb = new ParsedMedicationBuilder();
		ParsedMedication med1 = pmb.parseString("amlodipine 5mg od").build();
		ParsedMedication med2 = pmb.parseString("amlodipine   5mg 1/day").build();
		ParsedMedication med3 = pmb.parseString("amlodipine 5mg 2/day").build();
		ParsedMedication med4 = pmb.parseString("salbutamol 2p qds inh PRN").build();
		ParsedMedication med5 = pmb.fromParsedMedication(med1).build();
		ParsedMedication med6 = pmb.fromParsedMedication(med3).build();
		ParsedMedication med7 = pmb.fromParsedMedication(med4).build();
		assertEquals(med1, med5);
		assertEquals(med3, med6);
		assertEquals(med4, med7);

	}

	@Test
	public void testRoundtrippingToString() throws CorruptIndexException, IOException, ParseException {
		_testRoundtrip("Simvastatin 40mg od PO");
		_testRoundtrip("amlodipine PO 5mg od");
		_testRoundtrip("amlodipine PO 5.000mg od");
		_testRoundtrip("paracetamol 1g qds PRN");
	}

	void _testRoundtrip(String medicationString) throws CorruptIndexException, IOException, ParseException {
		ParsedMedicationBuilder pmb = new ParsedMedicationBuilder();
		ParsedMedication med1 = pmb.parseString(medicationString).build();
		ParsedMedication med2 = pmb.parseString(med1.userPresentableDescription()).build();
		assertEquals(med1, med2);

	}




	@Test
	public void testSplitting() {
		String n1 = "amlodipine 5mg od\nsimvastatin 40mg od";
		assertEquals(n1.split("\n").length, 2);
		assertEquals("amlodipine 5mg".split("\n").length, 1);
	}

	@Test
	public void testPartialNames() throws CorruptIndexException, IOException, ParseException {
		ParsedMedicationBuilder pmb = new ParsedMedicationBuilder();
		ParsedMedication med1 = pmb.parseString("amlodipin").build();
		assertEquals(Long.valueOf(108537001L), med1.conceptId());		// amlodipine

		ParsedMedication med2 = pmb.parseString("levetir").build();
		assertTrue(116076009L == med2.conceptId());
		
		ParsedMedication med3 = pmb.parseString("sine CR").build();
		assertEquals(Long.valueOf(9341401000001101L), med3.conceptId());
	}


	@Test
	public void testNotes() throws CorruptIndexException, IOException, ParseException {
		ParsedMedicationBuilder pmb = new ParsedMedicationBuilder();
		ParsedMedication med1 = pmb.parseString("levetiracetam 500mg bd start at 250mg od increase by 250mg every two weeks").build();
		System.out.println(med1);
	}
	
	private void _printParsed(String title, List<ParsedMedication> parsed) {
		for (ParsedMedication med : parsed) {
			System.out.println(title + ": " + med + " " + med.conceptId());
		}
	}

}
