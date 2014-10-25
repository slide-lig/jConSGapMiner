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

package fr.liglab.consgap.dataset;

import fr.liglab.consgap.collector.ResultsCollector;
import fr.liglab.consgap.collector.ResultsCollector.EmergingStatus;
import gnu.trove.TIntCollection;
import gnu.trove.iterator.TIntIntIterator;
import gnu.trove.list.array.TIntArrayList;
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
import java.util.Comparator;

abstract class ADataset<S> implements Dataset {
	protected final S[] currentSeqPresencePositive;
	protected final S[] currentSeqPresenceNegative;
	private final TIntObjectMap<S[]> itemPresenceMapPositive;
	private final TIntObjectMap<S[]> itemPresenceMapNegative;
	private final int posFreqLowerBound;// >=
	private final int negFreqUpperBound;// <=
	private final int gapConstraint;
	private final int[] sequence;
	private final ResultsCollector resultsCollector;

	public ADataset(ResultsCollector collector, String positiveDataset, String negativeDataset, int posFreqLowerBound,
			int negFreqUpperBound, int gapConstraint) throws IOException {
		this.posFreqLowerBound = posFreqLowerBound;
		this.negFreqUpperBound = negFreqUpperBound;
		this.gapConstraint = gapConstraint;
		this.sequence = new int[] {};
		this.currentSeqPresencePositive = null;
		this.currentSeqPresenceNegative = null;
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
		// get items that are emerging by themselves at the same time
		iter = negFreqCounting.iterator();
		while (iter.hasNext()) {
			iter.advance();
			if (!posFreqCounting.containsKey(iter.key()) || iter.value() <= this.negFreqUpperBound) {
				iter.remove();
			}
		}
		TIntCollection emergingItems = new TIntArrayList(posFreqCounting.keySet());
		emergingItems.removeAll(negFreqCounting.keySet());
		posFreqCounting.keySet().removeAll(emergingItems);
		// now do a rebasing using negative dataset support then positive
		// dataset support, we want to prioritize items with low values
		// we have to go through Integer because of custom sort
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
		this.itemPresenceMapPositive = new TIntObjectHashMap<S[]>(posFreqCounting.size());
		br = new BufferedReader(new FileReader(positiveDataset));
		int lineNumber = 0;
		while ((line = br.readLine()) != null) {
			if (!line.isEmpty()) {
				String[] sp = line.split("\\s+");
				for (int i = 0; i < sp.length; i++) {
					int itemOldId = Integer.parseInt(sp[i]);
					if (posFreqCounting.containsKey(itemOldId)) {
						int item = itemsRenaming.get(itemOldId);
						S[] bsArray = this.itemPresenceMapPositive.get(item);
						if (bsArray == null) {
							bsArray = this.initStructureArray(nbPositiveTransactions);
							this.itemPresenceMapPositive.put(item, bsArray);
						}
						if (bsArray[lineNumber] == null) {
							bsArray[lineNumber] = this.initEmptyStructure();
						}
						this.addOccurence(i, bsArray[lineNumber]);
					}
				}
				lineNumber++;
			}
		}
		br.close();

		this.itemPresenceMapNegative = new TIntObjectHashMap<S[]>(negFreqCounting.size());
		br = new BufferedReader(new FileReader(negativeDataset));
		lineNumber = 0;
		while ((line = br.readLine()) != null) {
			if (!line.isEmpty()) {
				String[] sp = line.split("\\s+");
				for (int i = 0; i < sp.length; i++) {
					int itemOldId = Integer.parseInt(sp[i]);
					if (negFreqCounting.containsKey(itemOldId)) {
						int item = itemsRenaming.get(itemOldId);
						S[] bsArray = this.itemPresenceMapNegative.get(item);
						if (bsArray == null) {
							bsArray = this.initStructureArray(nbNegativeTransactions);
							this.itemPresenceMapNegative.put(item, bsArray);
						}
						if (bsArray[lineNumber] == null) {
							bsArray[lineNumber] = this.initEmptyStructure();
						}
						this.addOccurence(i, bsArray[lineNumber]);
					}
				}
				lineNumber++;
			}
		}
		br.close();
		this.resultsCollector = collector;
		this.resultsCollector.setRebasing(rebasing);
		this.resultsCollector.setEmergingItems(emergingItems);
	}

	protected abstract S initEmptyStructure();

	protected abstract S[] initStructureArray(int size);

	protected abstract void addOccurence(int pos, S struct);

	protected ADataset(ADataset<S> parentDataset, int expansionItem, S[] expandedPosPositionsCompacted,
			S[] expandedNegPositionsCompacted, TIntObjectMap<S[]> newItemPresenceMapPositive,
			TIntObjectMap<S[]> newItemPresenceMapNegative) {
		this.posFreqLowerBound = parentDataset.posFreqLowerBound;
		this.negFreqUpperBound = parentDataset.negFreqUpperBound;
		this.gapConstraint = parentDataset.gapConstraint;
		this.sequence = new int[parentDataset.sequence.length + 1];
		System.arraycopy(parentDataset.sequence, 0, this.sequence, 1, parentDataset.sequence.length);
		this.sequence[0] = expansionItem;
		this.resultsCollector = parentDataset.resultsCollector;
		this.itemPresenceMapPositive = newItemPresenceMapPositive;
		this.itemPresenceMapNegative = newItemPresenceMapNegative;
		this.currentSeqPresencePositive = expandedPosPositionsCompacted;
		this.currentSeqPresenceNegative = expandedNegPositionsCompacted;
	}

	@Override
	final public int[] getExtensions() {
		return itemPresenceMapPositive.keys();
	}

	@Override
	final public ADataset<S> expand(final int expansionItem, final TIntSet deniedSiblingsExtensions)
			throws EmergingParentException, EmergingExpansionException, InfrequentException, DeadEndException {
		// compute support count in positive dataset
		final S[] expansionItemPosPositions = this.itemPresenceMapPositive.get(expansionItem);
		final S[] expandedPosPositions = this.initStructureArray(expansionItemPosPositions.length);
		// for custom optimization, not in original algorithm
		final int[] expandedPosLastPosition = new int[expansionItemPosPositions.length];
		int posSupport = expansionItemPosPositions.length;
		for (int i = 0; i < expansionItemPosPositions.length; i++) {
			if (expansionItemPosPositions[i] != null) {
				if (currentSeqPresencePositive != null) {
					expandedPosPositions[i] = this.findMatchingPosition(i, true, expansionItemPosPositions[i]);
				} else {
					// starter item, sequence currently empty
					expandedPosPositions[i] = expansionItemPosPositions[i];
				}
				if (expandedPosPositions[i] == null) {
					posSupport--;
					if (posSupport < posFreqLowerBound) {
						// support of expansion in positive will be too low
						throw new InfrequentException();
					}
				} else {
					expandedPosLastPosition[i] = this.findLastOccurence(expandedPosPositions[i]);
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
		final S[] expansionItemNegPositions = this.itemPresenceMapNegative.get(expansionItem);
		int negSupport = 0;
		boolean emerging = false;
		S[] expandedNegPositions = null;
		int[] expandedNegLastPosition = null;
		if (expansionItemNegPositions == null) {
			emerging = true;
			negSupport = 0;
		} else {
			negSupport = expansionItemNegPositions.length;
			expandedNegPositions = this.initStructureArray(expansionItemNegPositions.length);
			// for custom optimization, not in original algorithm
			expandedNegLastPosition = new int[expansionItemNegPositions.length];
			for (int i = 0; !emerging && i < expansionItemNegPositions.length; i++) {
				if (expansionItemNegPositions[i] != null) {
					if (currentSeqPresenceNegative != null) {
						expandedNegPositions[i] = this.findMatchingPosition(i, false, expansionItemNegPositions[i]);
					} else {
						// starter item, sequence currently empty
						expandedNegPositions[i] = expansionItemNegPositions[i];
					}
					if (expandedNegPositions[i] == null) {
						negSupport--;
						if (negSupport <= negFreqUpperBound) {
							// support of expansion in positive will be too low
							emerging = true;
						}
					} else {
						expandedNegLastPosition[i] = this.findLastOccurence(expandedNegPositions[i]);
					}
				} else {
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
			es = this.resultsCollector.collect(this.sequence, expansionItem);
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
		final TIntObjectMap<S[]> newItemPresenceMapPositive = new TIntObjectHashMap<S[]>(itemPresenceMapPositive.size());
		final int finalPosSupport = posSupport;
		this.itemPresenceMapPositive.forEachEntry(new TIntObjectProcedure<S[]>() {

			@Override
			public boolean execute(int k, S[] v) {
				S[] newKPresence = initStructureArray(finalPosSupport);
				int writeIndex = 0;
				int kSupport = 0;
				for (int i = 0; i < expandedPosPositions.length; i++) {
					if (expandedPosPositions[i] != null) {
						if (v[i] != null && hasOccurenceBefore(v[i], expandedPosLastPosition[i])) {
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
				return true;
			}

		});

		synchronized (deniedSiblingsExtensions) {
			newItemPresenceMapPositive.keySet().removeAll(deniedSiblingsExtensions);
		}

		// if there are potential future expansions
		if (!newItemPresenceMapPositive.isEmpty()) {
			// we prepare the new presence list of items in negative
			final TIntObjectMap<S[]> newItemPresenceMapNegative = new TIntObjectHashMap<S[]>(
					itemPresenceMapNegative.size());
			final int finalNegSupport = negSupport;
			final S[] finalExpandedNegPositions = expandedNegPositions;
			final int[] finalExpandedNegLastPosition = expandedNegLastPosition;
			this.itemPresenceMapNegative.forEachEntry(new TIntObjectProcedure<S[]>() {

				@Override
				public boolean execute(int k, S[] v) {
					S[] newKPresence = initStructureArray(finalNegSupport);
					int writeIndex = 0;
					int kSupport = 0;
					for (int i = 0; i < finalExpandedNegPositions.length; i++) {
						if (finalExpandedNegPositions[i] != null) {
							if (v[i] != null && hasOccurenceBefore(v[i], finalExpandedNegLastPosition[i])) {
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
					return true;
				}

			});

			synchronized (deniedSiblingsExtensions) {
				newItemPresenceMapNegative.keySet().removeAll(deniedSiblingsExtensions);
			}

			// we can now shift expanded positions to fill the null entries
			final S[] expandedPosPositionsCompacted = this.initStructureArray(finalPosSupport);
			int writeIndex = 0;
			for (int i = 0; i < expandedPosPositions.length; i++) {
				if (expandedPosPositions[i] != null) {
					expandedPosPositionsCompacted[writeIndex] = expandedPosPositions[i];
					writeIndex++;
				}
			}
			final S[] expandedNegPositionsCompacted = this.initStructureArray(finalNegSupport);
			writeIndex = 0;
			for (int i = 0; i < expandedNegPositions.length; i++) {
				if (expandedNegPositions[i] != null) {
					expandedNegPositionsCompacted[writeIndex] = expandedNegPositions[i];
					writeIndex++;
				}
			}

			// we have all we need, instantiate dataset
			return this.inistantiateDataset(expansionItem, expandedPosPositionsCompacted,
					expandedNegPositionsCompacted, newItemPresenceMapPositive, newItemPresenceMapNegative);
		} else {
			throw new DeadEndException();
		}
	}

	protected abstract ADataset<S> inistantiateDataset(int expansionItem, S[] expandedPosPositionsCompacted,
			S[] expandedNegPositionsCompacted, TIntObjectMap<S[]> newItemPresenceMapPositive,
			TIntObjectMap<S[]> newItemPresenceMapNegative);

	protected abstract S findMatchingPosition(int transIndex, boolean positive, S extensionItemPos);

	protected abstract int findLastOccurence(S seqPos);

	protected abstract boolean hasOccurenceBefore(S pos, int lim);// lim
																	// exclusive

	public final ResultsCollector getResultsCollector() {
		return resultsCollector;
	}

	protected final int getGapConstraint() {
		return gapConstraint;
	}

	@Override
	final public int[] getSequence() {
		return this.sequence;
	}
}
