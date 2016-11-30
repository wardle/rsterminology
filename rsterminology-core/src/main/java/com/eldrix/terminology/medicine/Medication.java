package com.eldrix.terminology.medicine;


import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;

public class Medication {

	public enum Frequency {
		PER_HOUR(286551008L, "/hour", "/hr", "/h","every-hour") {
			@Override
			BigDecimal equivalentDailyDose(BigDecimal dose) {
				return dose.multiply(new BigDecimal(24));
			}
		},
		TWELVE_TIMES_DAILY(396106003L, "12/day", "12/d","twelve-times-daily") {
			@Override
			BigDecimal equivalentDailyDose(BigDecimal dose) {
				return dose.multiply(new BigDecimal(12));
			}
		},
		TEN_TIMES_DAILY(396105004L, "10/day", "10/d","ten-times-daily") {
			@Override
			BigDecimal equivalentDailyDose(BigDecimal dose) {
				return dose.multiply(new BigDecimal(10));
			}
		},
		NINE_TIMES_DAILY(396115005L, "9/day", "9/d","nine-times-daily") {
			@Override
			BigDecimal equivalentDailyDose(BigDecimal dose) {
				return dose.multiply(new BigDecimal(9));
			}
		},
		EIGHT_TIMES_DAILY(307443002L, "8/day", "8/d","eight-times-daily") {
			@Override
			BigDecimal equivalentDailyDose(BigDecimal dose) {
				return dose.multiply(new BigDecimal(8));
			}
		},
		SEVEN_TIMES_DAILY(307442007L, "7/day", "7/d","seven-times-daily") {
			@Override
			BigDecimal equivalentDailyDose(BigDecimal dose) {
				return dose.multiply(new BigDecimal(7));
			}
		},
		SIX_TIMES_DAILY(307441000L, "6/day", "6/d","six-times-daily") {
			@Override
			BigDecimal equivalentDailyDose(BigDecimal dose) {
				return dose.multiply(new BigDecimal(6));
			}
		},
		FIVE_TIMES_DAILY(307440004L, "5/day", "5/d","five-times-daily") {
			@Override
			BigDecimal equivalentDailyDose(BigDecimal dose) {
				return dose.multiply(new BigDecimal(5));
			}
		},
		FOUR_TIMES_DAILY(307439001L, "qds", "4/day", "4/d", "four-times-daily") {
			@Override
			BigDecimal equivalentDailyDose(BigDecimal dose) {
				return dose.multiply(new BigDecimal(4));
			}
		},
		THREE_TIMES_DAILY(229798009L, "tds", "tid", "3/day", "3/d", "three-times-daily") {
			@Override
			BigDecimal equivalentDailyDose(BigDecimal dose) {
				return dose.multiply(new BigDecimal(3));
			}
		},
		TWICE_DAILY(229799001L, "bd", "bid", "2/day", "2/d","twice-daily", "two-times-daily") {
			@Override
			BigDecimal equivalentDailyDose(BigDecimal dose) {
				return dose.multiply(new BigDecimal(2));
			}
		},
		ONCE_DAILY(229797004L, "od", "1/day", "1/d","once-daily", "one-time-daily") {
			@Override
			BigDecimal equivalentDailyDose(BigDecimal dose) {
				return dose;
			}
		},
		ALTERNATE_DAYS(225760004L, "altdays", "alt", "alternate-days") {
			@Override
			BigDecimal equivalentDailyDose(BigDecimal dose) {
				return dose.divide(new BigDecimal(2), 4, RoundingMode.UP);
			}
		},
		ONCE_WEEKLY(225769003L, "/week", "/w", "/wk", "1/w", "once-every-week") {
			@Override
			BigDecimal equivalentDailyDose(BigDecimal dose) {
				return dose.divide(new BigDecimal(7), 4, RoundingMode.UP);
			}
		},
		ONCE_TWO_WEEKLY(20050000L, "/2weeks", "/2w", "/2wk", "once-every-two-weeks") {
			@Override
			BigDecimal equivalentDailyDose(BigDecimal dose) {
				return dose.divide(new BigDecimal(14), 4, RoundingMode.UP);
			}
		},
		ONCE_MONTHLY(307450003L, "/month", "/m", "/mo", "1/m", "once-every-month") {
			@Override
			BigDecimal equivalentDailyDose(BigDecimal dose) {
				return dose.divide(new BigDecimal(30), 1, RoundingMode.UP);
			}
		},
		ONCE_TWO_MONTHLY(445547001L, "/2months", "/2m", "/2mo", "once-every-two-months") {
			@Override
			BigDecimal equivalentDailyDose(BigDecimal dose) {
				return dose.divide(new BigDecimal(60), 1, RoundingMode.UP);
			}
		},
		ONCE_THREE_MONTHLY(396129006L, "/3months", "/3m", "/3mo", "once-every-three-months") {
			@Override
			BigDecimal equivalentDailyDose(BigDecimal dose) {
				return dose.divide(new BigDecimal(90), 1, RoundingMode.UP);
			}
		},
		ONCE_YEARLY(307455008L, "/year", "/y", "/yr", "once-every-year") {
			@Override
			BigDecimal equivalentDailyDose(BigDecimal dose) {
				return dose.divide(new BigDecimal(365), 1, RoundingMode.UP);
			}
		};
		private final long _conceptId;
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

		Frequency(long conceptId, String... names) {
			if (names.length == 0) {
				throw new IllegalStateException(
						"Each medication frequency must have at least one name");
			}
			_conceptId = conceptId;
			_names = names;
		}

		public String title() {
			return _names[0];
		}

		public long conceptId() {
			return _conceptId;
		}

		public String[] names() {
			return _names;
		}

		public static Frequency frequencyWithName(String name) {
			return _frequencies.get(name);
		}

		abstract BigDecimal equivalentDailyDose(BigDecimal dose);
	}

	public enum PrescribingType {
		DOSE_BASED,
		PRODUCT_BASED;
	}

	public enum Units {
		MICROGRAM(PrescribingType.DOSE_BASED, 258685003L, new String[] { "mcg" }, new BigDecimal("0.00001")),
		MILLIGRAM(PrescribingType.DOSE_BASED, 258684004L, new String[] { "mg" }, new BigDecimal("0.001")),
		MILLILITRES(PrescribingType.PRODUCT_BASED, 258773002L, new String[] { "ml" }, new BigDecimal("0.001")),
		GRAM(PrescribingType.DOSE_BASED, 258682000L, new String[] { "g", "gram" }, BigDecimal.ONE),
		UNITS(PrescribingType.PRODUCT_BASED, 408102007L, new String[] { "units", "u" }, BigDecimal.ONE),
		TABLETS(PrescribingType.PRODUCT_BASED, 385055001L, new String[] { "tablets", "tab", "t" }, BigDecimal.ONE),
		PUFFS(PrescribingType.PRODUCT_BASED, 415215001L, new String[] { "puffs", "puff", "p" }, BigDecimal.ONE),
		NONE(PrescribingType.PRODUCT_BASED, 408102007L, new String[] { "" }, BigDecimal.ONE);

		private final PrescribingType _prescribingType;
		private final long _conceptId;
		private final String[] _abbreviations;
		public final BigDecimal conversion;
		private static final HashMap<String, Units> _lookup;
		static {
			_lookup = new HashMap<String, Units>(); 
			for (Units unit : Units.values()) {
				for (String abbrev : unit.abbreviations()) {
					if (abbrev.length() > 0) {
						_lookup.put(abbrev, unit);
					}
				}
			}
		};

		Units(PrescribingType prescribingType, long conceptId, String[] abbrev, BigDecimal convert) {
			_prescribingType = prescribingType;
			_conceptId = conceptId;
			_abbreviations = abbrev;
			conversion = convert;
		}

		public PrescribingType prescribingType() {
			return _prescribingType;
		}

		public long conceptId() {
			return _conceptId;
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
		ORAL(26643006L, "PO"), 
		INTRAVENOUS(47625008L, "iv"), 
		SUBCUTANEOUS(34206005L, "sc"), 
		INTRAMUSCULAR(78421000L, "im"), 
		INTRATHECAL(72607000L, "intrathecal"), 
		INHALED(2764101000001108L, "inh"),
		TOPICAL(2762601000001108L, "top");
		final long _conceptId;
		final String _abbreviation;
		final static HashMap<String, Route> _routes;
		static {
			_routes = new HashMap<String, Route>(); 
			for (Route route : Route.values()) {
				_routes.put(route.abbreviation().toUpperCase(), route);
			}
		}

		Route(long conceptId, String abbreviation) {
			_conceptId = conceptId;
			_abbreviation = abbreviation;
		}

		public static Route routeForAbbreviation(String abbreviation) {
			return _routes.get(abbreviation.toUpperCase());
		}
		public long conceptId() {
			return _conceptId;
		}
		public String abbreviation() {
			return _abbreviation;
		}
	}
}
