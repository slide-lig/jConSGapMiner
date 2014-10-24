/*
	This file is part of jConSGapMiner - see https://github.com/slide-lig/jConSGapMiner
	
	Copyright 2014 Vincent Leroy, Université Joseph Fourier and CNRS

	Licensed under the Apache License, Version 2.0 (the "License");
	you may not use this file except in compliance with the License.
	You may obtain a copy of the License at

	 http://www.apache.org/licenses/LICENSE-2.0
	 
	or see the LICENSE.txt file joined with this program.

	Unless required by applicable law or agreed to in writing, software
	distributed under the License is distributed on an "AS IS" BASIS,
	WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
	See the License for the specific language governing permissions and
	limitations under the License.
 */

package fr.liglab.consgap.internals;

import fr.liglab.consgap.internals.ResultsCollector.EmergingStatus;
import gnu.trove.iterator.TIntIntIterator;
import gnu.trove.map.TIntIntMap;
import gnu.trove.map.TIntObjectMap;
import gnu.trove.map.hash.TIntIntHashMap;
import gnu.trove.map.hash.TIntObjectHashMap;
import gnu.trove.procedure.TIntObjectProcedure;
import gnu.trove.procedure.TIntProcedure;
import gnu.trove.set.TIntSet;
import gnu.trove.set.hash.TIntHashSet;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Comparator;

public class Dataset {
	// private BitSet[] currentItemsetPresencePositive;
	// same array but OR of itself shifted g times
	private final BitSet[] currentItemsetPresencePositiveShifted;
	// private final BitSet[] currentItemsetPresenceNegative;
	// same array but OR of itself shifted g times
	private final BitSet[] currentItemsetPresenceNegativeShifted;
	private final TIntObjectMap<BitSet[]> itemPresenceMapPositive;
	private final TIntObjectMap<BitSet[]> itemPresenceMapNegative;
	private final int posFreqLowerBound;// >=
	private final int negFreqUpperBound;// <=
	private final int gapConstraint;
	private final int[] sequence;
	private final ResultsCollector resultsCollector;

	public Dataset(String positiveDataset, String negativeDataset, int posFreqLowerBound, int negFreqUpperBound,
			int gapConstraint) throws IOException {
		this.posFreqLowerBound = posFreqLowerBound;
		this.negFreqUpperBound = negFreqUpperBound;
		this.gapConstraint = gapConstraint;
		this.sequence = new int[] {};
		this.currentItemsetPresencePositiveShifted = null;
		this.currentItemsetPresenceNegativeShifted = null;
		final TIntIntMap posFreqCounting = new TIntIntHashMap();
		int nbPositiveTransactions = 0;
		BufferedReader br = new BufferedReader(new FileReader(positiveDataset));
		String line;
		while ((line = br.readLine()) != null) {
			if (!line.isEmpty()) {
				nbPositiveTransactions++;
				String[] sp = line.split("\\s+");
				TIntSet uniqueItems = new TIntHashSet();
				for (String item : sp) {
					uniqueItems.add(Integer.parseInt(item));
				}
				uniqueItems.forEach(new TIntProcedure() {

					@Override
					public boolean execute(int item) {
						posFreqCounting.adjustOrPutValue(item, 1, 1);
						return true;
					}
				});
			}
		}
		br.close();
		// eliminate items that are infrequent in the positive dataset
		TIntIntIterator iter = posFreqCounting.iterator();
		while (iter.hasNext()) {
			iter.advance();
			if (iter.value() < this.posFreqLowerBound) {
				iter.remove();
			}
		}
		// compute frequencies in the negative dataset
		final TIntIntMap negFreqCounting = new TIntIntHashMap();
		int nbNegativeTransactions = 0;
		br = new BufferedReader(new FileReader(negativeDataset));
		while ((line = br.readLine()) != null) {
			if (!line.isEmpty()) {
				nbNegativeTransactions++;
				String[] sp = line.split("\\s+");
				TIntSet uniqueItems = new TIntHashSet();
				for (String item : sp) {
					uniqueItems.add(Integer.parseInt(item));
				}
				uniqueItems.forEach(new TIntProcedure() {

					@Override
					public boolean execute(int item) {
						negFreqCounting.adjustOrPutValue(item, 1, 1);
						return true;
					}
				});
			}
		}
		br.close();
		// eliminate items that are infrequent in the negative dataset or in the
		// positive dataset
		iter = negFreqCounting.iterator();
		while (iter.hasNext()) {
			iter.advance();
			if (iter.value() <= this.negFreqUpperBound || !posFreqCounting.containsKey(iter.key())) {
				iter.remove();
			}
		}
		// now do a rebasing using negative dataset support then positive
		// dataset support, we want to prioritize items with low values
		Integer[] keptItems = new Integer[posFreqCounting.size()];
		iter = posFreqCounting.iterator();
		int writePos = 0;
		while (iter.hasNext()) {
			iter.advance();
			keptItems[writePos] = iter.key();
			writePos++;
		}
		Arrays.sort(keptItems, new Comparator<Integer>() {

			@Override
			public int compare(Integer o1, Integer o2) {
				int negSupportDiff = negFreqCounting.get(o1) - negFreqCounting.get(o2);
				if (negSupportDiff != 0) {
					return negSupportDiff;
				} else {
					int posSupportDiff = posFreqCounting.get(o1) - posFreqCounting.get(o2);
					if (posSupportDiff != 0) {
						return posSupportDiff;
					} else {
						return o1.compareTo(o2);
					}
				}
			}
		});
		TIntIntMap itemsRenaming = new TIntIntHashMap();
		int[] rebasing = new int[keptItems.length];
		for (int i = 0; i < keptItems.length; i++) {
			itemsRenaming.put(keptItems[i], i);
			rebasing[i] = keptItems[i];
		}

		// we have all of our items and their new names, read datasets one last
		// time and make BitSets
		this.itemPresenceMapPositive = new TIntObjectHashMap<BitSet[]>(posFreqCounting.size());
		br = new BufferedReader(new FileReader(positiveDataset));
		int lineNumber = 0;
		while ((line = br.readLine()) != null) {
			if (!line.isEmpty()) {
				String[] sp = line.split("\\s+");
				for (int i = 0; i < sp.length; i++) {
					int itemOldId = Integer.parseInt(sp[i]);
					if (posFreqCounting.containsKey(itemOldId)) {
						int item = itemsRenaming.get(itemOldId);
						BitSet[] bsArray = this.itemPresenceMapPositive.get(item);
						if (bsArray == null) {
							bsArray = new BitSet[nbPositiveTransactions];
							this.itemPresenceMapPositive.put(item, bsArray);
						}
						if (bsArray[lineNumber] == null) {
							bsArray[lineNumber] = new BitSet(i + 1);
						}
						bsArray[lineNumber].set(i);
					}
				}
				lineNumber++;
			}
		}
		br.close();

		this.itemPresenceMapNegative = new TIntObjectHashMap<BitSet[]>(negFreqCounting.size());
		br = new BufferedReader(new FileReader(negativeDataset));
		lineNumber = 0;
		while ((line = br.readLine()) != null) {
			if (!line.isEmpty()) {
				String[] sp = line.split("\\s+");
				for (int i = 0; i < sp.length; i++) {
					int itemOldId = Integer.parseInt(sp[i]);
					if (negFreqCounting.containsKey(itemOldId)) {
						int item = itemsRenaming.get(itemOldId);
						BitSet[] bsArray = this.itemPresenceMapNegative.get(item);
						if (bsArray == null) {
							bsArray = new BitSet[nbNegativeTransactions];
							this.itemPresenceMapNegative.put(item, bsArray);
						}
						if (bsArray[lineNumber] == null) {
							bsArray[lineNumber] = new BitSet(i + 1);
						}
						bsArray[lineNumber].set(i);
					}
				}
				lineNumber++;
			}
		}
		br.close();
		this.resultsCollector = new ResultsCollector(rebasing);
	}

	public Dataset(Dataset parentDataset, int expansionItem, BitSet[] expandedPosPositionsCompacted,
			BitSet[] expandedNegPositionsCompacted, TIntObjectMap<BitSet[]> newItemPresenceMapPositive,
			TIntObjectMap<BitSet[]> newItemPresenceMapNegative) {
		this.posFreqLowerBound = parentDataset.posFreqLowerBound;
		this.negFreqUpperBound = parentDataset.negFreqUpperBound;
		this.gapConstraint = parentDataset.gapConstraint;
		this.sequence = new int[parentDataset.sequence.length + 1];
		System.arraycopy(parentDataset.sequence, 0, this.sequence, 1, parentDataset.sequence.length);
		this.sequence[0] = expansionItem;
		this.resultsCollector = parentDataset.resultsCollector;
		this.itemPresenceMapPositive = newItemPresenceMapPositive;
		this.itemPresenceMapNegative = newItemPresenceMapNegative;
		this.currentItemsetPresencePositiveShifted = new BitSet[expandedPosPositionsCompacted.length];
		for (int i = 0; i < expandedPosPositionsCompacted.length; i++) {
			// we can only easily shift left, meaning that we build our
			// sequences from the end
			// System.out.println("trans " + i);
			this.currentItemsetPresencePositiveShifted[i] = expandedPosPositionsCompacted[i].get(1,
					expandedPosPositionsCompacted[i].length());
			// System.out.println("base " +
			// this.currentItemsetPresencePositiveShifted[i]);
			for (int g = 0; g < this.gapConstraint; g++) {
				if (2 + g < expandedPosPositionsCompacted[i].length()) {
					this.currentItemsetPresencePositiveShifted[i].or(expandedPosPositionsCompacted[i].get(2 + g,
							expandedPosPositionsCompacted[i].length()));
				}
				// System.out.println("oring with "
				// + expandedPosPositionsCompacted[i].get(1 + g,
				// expandedPosPositionsCompacted[i].length()));
				// System.out.println("now " +
				// this.currentItemsetPresencePositiveShifted[i]);
			}
		}
		// System.out.println("constructing " + Arrays.toString(this.sequence));
		// if (Arrays.equals(this.sequence, new int[] { 2, 0 })) {
		// }
		this.currentItemsetPresenceNegativeShifted = new BitSet[expandedNegPositionsCompacted.length];
		for (int i = 0; i < expandedNegPositionsCompacted.length; i++) {
			this.currentItemsetPresenceNegativeShifted[i] = expandedNegPositionsCompacted[i].get(1,
					expandedNegPositionsCompacted[i].length());
			for (int g = 0; g < this.gapConstraint; g++) {
				if (2 + g < expandedNegPositionsCompacted[i].length()) {
					this.currentItemsetPresenceNegativeShifted[i].or(expandedNegPositionsCompacted[i].get(2 + g,
							expandedNegPositionsCompacted[i].length()));
				}
			}
		}
	}

	public void recursivelyExpand() {
		int[] expansionItems = itemPresenceMapPositive.keys();
		Arrays.sort(expansionItems);
		TIntSet deniedSiblingsExtensions = new TIntHashSet();
		for (int i = 0; i < expansionItems.length; i++) {
			// System.out.println("expanding " + Arrays.toString(this.sequence)
			// + " with " + expansionItems[i]);
			Dataset extDataset = null;
			try {
				extDataset = this.expand(expansionItems[i], deniedSiblingsExtensions);
			} catch (EmergingParentException e) {
				// only in multithreaded cases, because otherwise we do depth
				// first
				// System.out.println(e.getClass().getCanonicalName());
				return;
			} catch (EmergingExpansionException e) {
				// System.out.println(e.getClass().getCanonicalName());
				deniedSiblingsExtensions.add(expansionItems[i]);
			} catch (DeadEndException | InfrequentException e) {
				// if we didn't check for locations (custom optimizations), this
				// would be a case of denied siblings
				// System.out.println(e.getClass().getCanonicalName());
			}
			if (extDataset != null) {
				extDataset.recursivelyExpand();
			}
		}
	}

	private Dataset expand(final int expansionItem, final TIntSet deniedSiblingsExtensions)
			throws EmergingParentException, EmergingExpansionException, InfrequentException, DeadEndException {
		// compute support count in positive dataset
		final BitSet[] expansionItemPosPositions = this.itemPresenceMapPositive.get(expansionItem);
		final BitSet[] expandedPosPositions = new BitSet[expansionItemPosPositions.length];
		// for custom optimization, not in original algorithm
		final int[] expandedPosLastPosition = new int[expansionItemPosPositions.length];
		int posSupport = expansionItemPosPositions.length;
		for (int i = 0; i < expansionItemPosPositions.length; i++) {
			if (expansionItemPosPositions[i] != null) {
				boolean starter = false;
				if (currentItemsetPresencePositiveShifted != null) {
					expandedPosPositions[i] = (BitSet) currentItemsetPresencePositiveShifted[i].clone();
					expandedPosPositions[i].and(expansionItemPosPositions[i]);
				} else {
					// starter item, sequence currently empty
					expandedPosPositions[i] = expansionItemPosPositions[i];
					starter = true;
				}
				if (!starter && expandedPosPositions[i].isEmpty()) {
					expandedPosPositions[i] = null;
					posSupport--;
					if (posSupport < posFreqLowerBound) {
						// support of expansion in positive will be too low
						throw new InfrequentException();
					}
				} else {
					expandedPosLastPosition[i] = expandedPosPositions[i].previousSetBit(expandedPosPositions[i]
							.length() - 1);
				}
			} else {
				posSupport--;
				if (posSupport < posFreqLowerBound) {
					// support of expansion in positive will be too low
					throw new InfrequentException();
				}
			}
		}
		// if we reach this point, the expanded sequence is frequent in the
		// positive dataset
		// we now compute the support in the negative dataset
		final BitSet[] expansionItemNegPositions = this.itemPresenceMapNegative.get(expansionItem);
		int negSupport = 0;
		boolean emerging = false;
		BitSet[] expandedNegPositions = null;
		int[] expandedNegLastPosition = null;
		if (expansionItemNegPositions == null) {
			emerging = true;
			negSupport = 0;
		} else {
			negSupport = expansionItemNegPositions.length;
			expandedNegPositions = new BitSet[expansionItemNegPositions.length];
			// for custom optimization, not in original algorithm
			expandedNegLastPosition = new int[expansionItemNegPositions.length];
			for (int i = 0; !emerging && i < expansionItemNegPositions.length; i++) {
				if (expansionItemNegPositions[i] != null) {
					boolean starter = false;
					if (currentItemsetPresenceNegativeShifted != null) {
						expandedNegPositions[i] = (BitSet) currentItemsetPresenceNegativeShifted[i].clone();
						expandedNegPositions[i].and(expansionItemNegPositions[i]);
					} else {
						// starter item, sequence currently empty
						expandedNegPositions[i] = expansionItemNegPositions[i];
						starter = true;
					}
					// not using cardinality because it scans through the whole
					// BitSet, so it's more expensive
					if (!starter && expandedNegPositions[i].isEmpty()) {
						expandedNegPositions[i] = null;
						negSupport--;
						if (negSupport <= negFreqUpperBound) {
							// support of expansion in positive will be too low
							emerging = true;
						}
					} else {
						expandedNegLastPosition[i] = expandedNegPositions[i].previousSetBit(expandedNegPositions[i]
								.length() - 1);
					}
				} else {
					expandedNegPositions[i] = null;
					negSupport--;
					if (negSupport <= negFreqUpperBound) {
						// support of expansion in positive will be too low
						emerging = true;
					}
				}
			}
		}
		EmergingStatus es;
		// System.out.println("emerging " + emerging);
		if (emerging) {
			es = this.resultsCollector.collectEmerging(this.sequence, expansionItem);
		} else {
			es = EmergingStatus.NO_EMERGING_SUBSET;// this.resultsCollector.hasEmergingSubseq(this.sequence,
			// expansionItem);
		}

		switch (es) {
		case EMERGING_WITHOUT_EXPANSION:
			throw new EmergingParentException();
		case EMERGING_WITH_EXPANSION:
			throw new EmergingExpansionException();
		case NEW_EMERGING:
			throw new EmergingExpansionException();
		case NO_EMERGING_SUBSET:
			break;
		default:
			break;
		}
		// if we reach this point we should have expandedNegPositions and
		// expandedNegFirstPosition != null

		// now prepare the new presence list of items in positive, eliminate the
		// ones that
		// are not frequent anymore, and note the ones that could be in closure
		// final TIntList potentialClosure = new TIntArrayList();
		final TIntObjectMap<BitSet[]> newItemPresenceMapPositive = new TIntObjectHashMap<BitSet[]>(
				itemPresenceMapPositive.size());
		final int finalPosSupport = posSupport;
		this.itemPresenceMapPositive.forEachEntry(new TIntObjectProcedure<BitSet[]>() {

			@Override
			public boolean execute(int k, BitSet[] v) {
				if (!deniedSiblingsExtensions.contains(k)) {
					BitSet[] newKPresence = new BitSet[finalPosSupport];
					int writeIndex = 0;
					int kSupport = 0;
					for (int i = 0; i < expandedPosPositions.length; i++) {
						if (expandedPosPositions[i] != null) {
							if (v[i] != null && v[i].previousSetBit(expandedPosLastPosition[i] - 1) >= 0) {
								newKPresence[writeIndex] = v[i];
								if (newKPresence[writeIndex] != null) {
									kSupport++;
								}
							}
							writeIndex++;
						}
					}
					if (kSupport >= posFreqLowerBound) {
						newItemPresenceMapPositive.put(k, newKPresence);
					}
				}
				return true;
			}

		});
		// if there are potential future expansions
		if (!newItemPresenceMapPositive.isEmpty()) {
			// we prepare the new presence list of items in negative
			final TIntObjectMap<BitSet[]> newItemPresenceMapNegative = new TIntObjectHashMap<BitSet[]>(
					itemPresenceMapNegative.size());
			final int finalNegSupport = negSupport;
			final BitSet[] finalExpandedNegPositions = expandedNegPositions;
			final int[] finalExpandedNegLastPosition = expandedNegLastPosition;
			this.itemPresenceMapNegative.forEachEntry(new TIntObjectProcedure<BitSet[]>() {

				@Override
				public boolean execute(int k, BitSet[] v) {
					if (!deniedSiblingsExtensions.contains(k)) {
						BitSet[] newKPresence = new BitSet[finalNegSupport];
						int writeIndex = 0;
						int kSupport = 0;
						for (int i = 0; i < finalExpandedNegPositions.length; i++) {
							if (finalExpandedNegPositions[i] != null) {
								if (v[i] != null && v[i].previousSetBit(finalExpandedNegLastPosition[i] - 1) >= 0) {
									newKPresence[writeIndex] = v[i];
									if (newKPresence[writeIndex] != null) {
										kSupport++;
									}
								}
								writeIndex++;
							}
						}
						if (kSupport > negFreqUpperBound) {
							newItemPresenceMapNegative.put(k, newKPresence);
						}
					}
					return true;
				}

			});

			// we can now shift expanded positions to fill the null entries
			final BitSet[] expandedPosPositionsCompacted = new BitSet[finalPosSupport];
			int writeIndex = 0;
			for (int i = 0; i < expandedPosPositions.length; i++) {
				if (expandedPosPositions[i] != null) {
					expandedPosPositionsCompacted[writeIndex] = expandedPosPositions[i];
					writeIndex++;
				}
			}
			final BitSet[] expandedNegPositionsCompacted = new BitSet[finalNegSupport];
			writeIndex = 0;
			for (int i = 0; i < expandedNegPositions.length; i++) {
				if (expandedNegPositions[i] != null) {
					expandedNegPositionsCompacted[writeIndex] = expandedNegPositions[i];
					writeIndex++;
				}
			}
			// we have all we need, instantiate dataset
			return new Dataset(this, expansionItem, expandedPosPositionsCompacted, expandedNegPositionsCompacted,
					newItemPresenceMapPositive, newItemPresenceMapNegative);
		} else {
			throw new DeadEndException();
		}
	}

	public final ResultsCollector getResultsCollector() {
		return resultsCollector;
	}

	private static class InfrequentException extends Exception {

		/**
		 * 
		 */
		private static final long serialVersionUID = 1L;
	};

	private static class DeadEndException extends Exception {

		/**
		 * 
		 */
		private static final long serialVersionUID = 1L;
	};

	private static class EmergingExpansionException extends Exception {

		/**
		 * 
		 */
		private static final long serialVersionUID = 1L;
	};

	private static class EmergingParentException extends Exception {

		/**
		 * 
		 */
		private static final long serialVersionUID = 1L;

	}
}
