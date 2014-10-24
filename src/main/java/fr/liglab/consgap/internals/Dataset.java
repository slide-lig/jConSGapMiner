package fr.liglab.consgap.internals;

import gnu.trove.set.TIntSet;

public interface Dataset {

	public ResultsCollector getResultsCollector();

	public int[] getExtensions();

	public Dataset expand(final int expansionItem, final TIntSet deniedSiblingsExtensions)
			throws EmergingParentException, EmergingExpansionException, InfrequentException, DeadEndException;

	public int[] getSequence();

	public static class InfrequentException extends Exception {

		/**
		 * 
		 */
		private static final long serialVersionUID = 1L;
	};

	public static class DeadEndException extends Exception {

		/**
		 * 
		 */
		private static final long serialVersionUID = 1L;
	};

	public static class EmergingExpansionException extends Exception {

		/**
		 * 
		 */
		private static final long serialVersionUID = 1L;
	};

	public static class EmergingParentException extends Exception {

		/**
		 * 
		 */
		private static final long serialVersionUID = 1L;

	}
}