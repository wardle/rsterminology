package com.eldrix.terminology.medicine;


import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;

public class Medication {

	public enum Frequency {
		PER_HOUR("/hour", "/hr", "/h","every-hour") {
			@Override
			BigDecimal equivalentDailyDose(BigDecimal dose) {
				return dose.multiply(new BigDecimal(24));
			}
		},
		TWELVE_TIMES_DAILY("12/day", "12/d","twelve-times-daily") {
			@Override
			BigDecimal equivalentDailyDose(BigDecimal dose) {
				return dose.multiply(new BigDecimal(12));
			}
		},
		TEN_TIMES_DAILY("10/day", "10/d","ten-times-daily") {
			@Override
			BigDecimal equivalentDailyDose(BigDecimal dose) {
				return dose.multiply(new BigDecimal(10));
			}
		},
		NINE_TIMES_DAILY("9/day", "9/d","nine-times-daily") {
			@Override
			BigDecimal equivalentDailyDose(BigDecimal dose) {
				return dose.multiply(new BigDecimal(9));
			}
		},
		EIGHT_TIMES_DAILY("8/day", "8/d","eight-times-daily") {
			@Override
			BigDecimal equivalentDailyDose(BigDecimal dose) {
				return dose.multiply(new BigDecimal(8));
			}
		},
		SEVEN_TIMES_DAILY("7/day", "7/d","seven-times-daily") {
			@Override
			BigDecimal equivalentDailyDose(BigDecimal dose) {
				return dose.multiply(new BigDecimal(7));
			}
		},
		SIX_TIMES_DAILY("6/day", "6/d","six-times-daily") {
			@Override
			BigDecimal equivalentDailyDose(BigDecimal dose) {
				return dose.multiply(new BigDecimal(6));
			}
		},
		FIVE_TIMES_DAILY("5/day", "5/d","five-times-daily") {
			@Override
			BigDecimal equivalentDailyDose(BigDecimal dose) {
				return dose.multiply(new BigDecimal(5));
			}
		},
		FOUR_TIMES_DAILY("qds", "4/day", "4/d", "four-times-daily") {
			@Override
			BigDecimal equivalentDailyDose(BigDecimal dose) {
				return dose.multiply(new BigDecimal(4));
			}
		},
		THREE_TIMES_DAILY("tds", "tid", "3/day", "3/d", "three-times-daily") {
			@Override
			BigDecimal equivalentDailyDose(BigDecimal dose) {
				return dose.multiply(new BigDecimal(3));
			}
		},
		TWICE_DAILY("bd", "bid", "2/day", "2/d","two-times-daily") {
			@Override
			BigDecimal equivalentDailyDose(BigDecimal dose) {
				return dose.multiply(new BigDecimal(2));
			}
		},
		ONCE_DAILY("od", "1/day", "1/d","one-time-daily") {
			@Override
			BigDecimal equivalentDailyDose(BigDecimal dose) {
				return dose;
			}
		},
		ALTERNATE_DAYS("altdays", "alt", "alternate-days") {
			@Override
			BigDecimal equivalentDailyDose(BigDecimal dose) {
				return dose.divide(new BigDecimal(2), 4, RoundingMode.UP);
			}
		},
		ONCE_WEEKLY("/week", "/w", "/wk", "1/w", "once-every-week") {
			@Override
			BigDecimal equivalentDailyDose(BigDecimal dose) {
				return dose.divide(new BigDecimal(7), 4, RoundingMode.UP);
			}
		},
		ONCE_TWO_WEEKLY("/2weeks", "/2w", "/2wk", "once-every-two-weeks") {
			@Override
			BigDecimal equivalentDailyDose(BigDecimal dose) {
				return dose.divide(new BigDecimal(14), 4, RoundingMode.UP);
			}
		},
		ONCE_MONTHLY("/month", "/m", "/mo", "1/m", "once-every-month") {
			@Override
			BigDecimal equivalentDailyDose(BigDecimal dose) {
				return dose.divide(new BigDecimal(30), 1, RoundingMode.UP);
			}
		},
		ONCE_TWO_MONTHLY("/2months", "/2m", "/2mo", "once-every-two-months") {
			@Override
			BigDecimal equivalentDailyDose(BigDecimal dose) {
				return dose.divide(new BigDecimal(60), 1, RoundingMode.UP);
			}
		},
		ONCE_THREE_MONTHLY("/3months", "/3m", "/3mo", "once-every-three-months") {
			@Override
			BigDecimal equivalentDailyDose(BigDecimal dose) {
				return dose.divide(new BigDecimal(90), 1, RoundingMode.UP);
			}
		},
		ONCE_YEARLY("/year", "/y", "/yr", "once-every-year") {
			@Override
			BigDecimal equivalentDailyDose(BigDecimal dose) {
				return dose.divide(new BigDecimal(365), 1, RoundingMode.UP);
			}
		};
		private final String[] _names;
		@SuppressWarnings("serial")
		private static final HashMap<String, Medication.Frequency> _frequencies = new HashMap<String, Medication.Frequency>() {
			{
				for (Medication.Frequency freq : Medication.Frequency.values()) {
					for (String name : freq.names()) {
						put(name, freq);
					}
				}
			}
		};

		Frequency(String... names) {
			if (names.length == 0) {
				throw new IllegalStateException(
						"Each medication frequency must have at least one name");
			}
			_names = names;
		}

		public String title() {
			return _names[0];
		}

		public String[] names() {
			return _names;
		}

		public static Frequency frequencyWithName(String name) {
			return _frequencies.get(name);
		}

		abstract BigDecimal equivalentDailyDose(BigDecimal dose);
	}

	public enum Units {
		MICROGRAM(new String[] { "mcg" }, new BigDecimal("0.00001")),
		MILLIGRAM(new String[] { "mg" }, new BigDecimal("0.001")),
		MILLILITRES(new String[] { "ml" }, new BigDecimal("0.001")),
		GRAM(new String[] { "g", "gram" }, BigDecimal.ONE),
		UNITS(new String[] { "units", "u" }, BigDecimal.ONE),
		TABLETS(new String[] { "tablets", "tab", "t" }, BigDecimal.ONE),
		PUFFS(new String[] { "puffs", "puff", "p" }, BigDecimal.ONE),
		NONE(new String[] { "" }, BigDecimal.ONE);
		public final BigDecimal conversion;
		private final String[] _abbreviations;
		private static final HashMap<String, Units> _lookup = new HashMap<String, Units>() {
			{
				for (Units unit : Units.values()) {
					for (String abbrev : unit.abbreviations()) {
						if (abbrev.length() > 0) {
							put(abbrev, unit);
						}
					}
				}
			}
		};

		Units(String[] abbrev, BigDecimal convert) {
			_abbreviations = abbrev;
			conversion = convert;
		}

		public String abbreviation() {
			return _abbreviations[0];
		}

		public String[] abbreviations() {
			return _abbreviations;
		}

		public static Units unitForAbbreviation(String abbreviation) {
			return _lookup.get(abbreviation);
		}
	}

	public enum ReasonForStopping {
		NOT_APPLICABLE, LACK_OF_EFFICACY, ADVERSE_EVENT, PLANNING_PREGNANCY, PREGNANCY, CHANGE_OF_DOSE, RECORDED_IN_ERROR,
	}

	public enum Location {
		NOT_APPLICABLE, HOMECARE, HOSPITAL
	}

	public enum Route {
		ORAL("PO"), INTRAVENOUS("iv"), SUBCUTANEOUS("sc"), INTRAMUSCULAR("im"), INTRATHECAL(
				"intrathecal"), INHALED("inh"), TOPICAL("top");
		final String _abbreviation;
		final static HashMap<String, Route> _routes = new HashMap<String, Route>() {
			{
				for (Route route : Route.values()) {
					put(route.abbreviation().toUpperCase(), route);
				}
			}
		};

		Route(String abbreviation) {
			_abbreviation = abbreviation;
		}

		public static Route routeForAbbreviation(String abbreviation) {
			return _routes.get(abbreviation.toUpperCase());
		}
		public String abbreviation() {
			return _abbreviation;
		}
	}
}
