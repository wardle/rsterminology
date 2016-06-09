package com.eldrix.terminology.snomedct;

/**
 * Implementation of the Verhoeff Dihedral check digit algorithm.
 * @see http://en.wikibooks.org/wiki/Algorithm_Implementation/Checksums/Verhoeff_Algorithm
 * @see http://en.wikipedia.org/wiki/Verhoeff_algorithm
 * @author Mark Wardle
 *
 */
public class VerhoeffDihedral {
	// The multiplication table
	static int[][] d  = new int[][] {
		{0, 1, 2, 3, 4, 5, 6, 7, 8, 9}, 
		{1, 2, 3, 4, 0, 6, 7, 8, 9, 5}, 
		{2, 3, 4, 0, 1, 7, 8, 9, 5, 6}, 
		{3, 4, 0, 1, 2, 8, 9, 5, 6, 7}, 
		{4, 0, 1, 2, 3, 9, 5, 6, 7, 8}, 
		{5, 9, 8, 7, 6, 0, 4, 3, 2, 1}, 
		{6, 5, 9, 8, 7, 1, 0, 4, 3, 2}, 
		{7, 6, 5, 9, 8, 2, 1, 0, 4, 3}, 
		{8, 7, 6, 5, 9, 3, 2, 1, 0, 4}, 
		{9, 8, 7, 6, 5, 4, 3, 2, 1, 0}
	};

	// The permutation table
	static int[][] p = new int[][] {
		{0, 1, 2, 3, 4, 5, 6, 7, 8, 9}, 
		{1, 5, 7, 6, 2, 8, 3, 0, 9, 4}, 
		{5, 8, 0, 3, 7, 9, 6, 1, 4, 2}, 
		{8, 9, 1, 6, 0, 4, 3, 5, 2, 7}, 
		{9, 4, 5, 3, 1, 2, 6, 8, 7, 0}, 
		{4, 2, 8, 6, 5, 7, 3, 9, 0, 1}, 
		{2, 7, 9, 3, 8, 0, 6, 4, 1, 5}, 
		{7, 0, 4, 6, 9, 1, 3, 2, 5, 8}
	};

	// The inverse table
	static int[] inv = {0, 4, 3, 2, 1, 5, 6, 7, 8, 9};


	/**
	 * Generate a Verhoeff digit for a given number. 
	 */
	public static String generateVerhoeff(String num){
		int c = 0;
		int[] myArray = stringToReversedIntArray(num);
		for(int i = 0; i < myArray.length; i++) {
			c = d[c][p[((i + 1) % 8)] [myArray[i]]];
		}
		return Integer.toString(inv[c]);
	}
	
	/**
	 * Validate number according to Verhoeff algorithm.
	 * This assumes the check digit is the last digit.
	 * @param num
	 * @return
	 */
	public static boolean validateVerhoeff(String num){
		int c = 0;
		int[] myArray = stringToReversedIntArray(num);
		for (int i = 0; i < myArray.length; i++) {
			c = d[c][p[(i % 8)][myArray[i]]];
		}
		return (c == 0);
	}

	/**
	 * Validate number according to Verhoeff algorithm.
	 * This assumes the check digit is the last digit.
	 * @param num
	 * @return
	 */
	public static boolean validateVerhoeff(long num) {
		return validateVerhoeff(Long.toString(num));
	}
	
	/**
	 * Convert a string to a reversed integer array.
	 */
	private static int[] stringToReversedIntArray(String num){
		int[] myArray = new int[num.length()];
		for(int i = 0; i < num.length(); i++) {
			myArray[i] = Integer.parseInt(num.substring(i, i + 1));                                     
		}
		myArray = reverseIntArray(myArray);
		return myArray;
	}

	/**
	 * Reverse an int array
	 */
	private static int[] reverseIntArray(int[] myArray) {
		int[] reversed = new int[myArray.length];
		for(int i = 0; i < myArray.length ; i++) {
			reversed[i] = myArray[myArray.length - (i + 1)];                     
		}
		return reversed;
	}
}
