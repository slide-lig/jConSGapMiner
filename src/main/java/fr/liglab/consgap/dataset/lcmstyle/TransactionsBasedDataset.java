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

package fr.liglab.consgap.dataset.lcmstyle;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import fr.liglab.consgap.Main;
import fr.liglab.consgap.collector.ResultsCollector;
import fr.liglab.consgap.collector.ResultsCollector.EmergingStatus;
import fr.liglab.consgap.dataset.Dataset;
import gnu.trove.iterator.TIntIntIterator;
import gnu.trove.iterator.TIntIterator;
import gnu.trove.iterator.TObjectIntIterator;
import gnu.trove.list.TIntList;
import gnu.trove.list.array.TIntArrayList;
import gnu.trove.map.TIntIntMap;
import gnu.trove.map.TIntObjectMap;
import gnu.trove.map.TObjectIntMap;
import gnu.trove.map.hash.TIntIntHashMap;
import gnu.trove.map.hash.TIntObjectHashMap;
import gnu.trove.map.hash.TObjectIntHashMap;
import gnu.trove.procedure.TIntObjectProcedure;
import gnu.trove.procedure.TIntProcedure;
import gnu.trove.set.TIntSet;
import gnu.trove.set.hash.TIntHashSet;

public class TransactionsBasedDataset implements Dataset {
	// keep in mind that we build from right to left, originally because of
	// bitset shift
	protected final List<List<PositionAndProvenance>> currentSeqPresencePositive;
	protected final List<List<PositionAndProvenance>> currentSeqPresenceNegative;
	protected final List<int[]> positiveTransactions;
	protected final List<int[]> negativeTransactions;
	protected final TIntObjectMap<TIntList[]> itemPresenceMapPositive;
	private final TIntObjectMap<TIntList[]> itemPresenceMapNegative;
	protected final int posFreqLowerBound;// >=
	private final int negFreqUpperBound;// <=
	private final int gapConstraint;
	private final int[] sequence;
	private final ResultsCollector resultsCollector;
	private final int[] possibleExtensions;
	protected final int[] originalPosTransactionsMapping;
	protected final int[] originalNegTransactionsMapping;

	public TransactionsBasedDataset(ResultsCollector collector, String positiveDataset, String negativeDataset,
			int posFreqLowerBound, int negFreqUpperBound, int gapConstraint) throws IOException {
		this.posFreqLowerBound = posFreqLowerBound;
		this.negFreqUpperBound = negFreqUpperBound;
		this.gapConstraint = gapConstraint;
		this.sequence = new int[] {};
		this.currentSeqPresencePositive = null;
		this.currentSeqPresenceNegative = null;
		final TObjectIntMap<String> posFreqCounting = new TObjectIntHashMap<String>();
		int nbPositiveTransactions = 0;
		BufferedReader br = new BufferedReader(new FileReader(positiveDataset));
		String line;
		while ((line = br.readLine()) != null) {
			if (!line.isEmpty()) {
				nbPositiveTransactions++;
				String[] sp = line.split(Main.separator);
				Set<String> uniqueItems = new HashSet<String>();
				for (String item : sp) {
					uniqueItems.add(item);
				}
				for (String item : uniqueItems) {
					posFreqCounting.adjustOrPutValue(item, 1, 1);
				}
			}
		}
		br.close();
		// eliminate items that are infrequent in the positive dataset
		TObjectIntIterator<String> iter = posFreqCounting.iterator();
		while (iter.hasNext()) {
			iter.advance();
			if (iter.value() < this.posFreqLowerBound) {
				iter.remove();
			}
		}
		// compute frequencies in the negative dataset
		final TObjectIntMap<String> negFreqCounting = new TObjectIntHashMap<String>();
		int nbNegativeTransactions = 0;
		br = new BufferedReader(new FileReader(negativeDataset));
		while ((line = br.readLine()) != null) {
			if (!line.isEmpty()) {
				nbNegativeTransactions++;
				String[] sp = line.split(Main.separator);
				Set<String> uniqueItems = new HashSet<String>();
				for (String item : sp) {
					uniqueItems.add(item);
				}
				for (String item : uniqueItems) {
					negFreqCounting.adjustOrPutValue(item, 1, 1);
				}
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
		Collection<String> emergingItems = new ArrayList<String>(posFreqCounting.keySet());
		emergingItems.removeAll(negFreqCounting.keySet());
		posFreqCounting.keySet().removeAll(emergingItems);
		// now do a rebasing using negative dataset support then positive
		// dataset support, we want to prioritize items with low values
		// we have to go through Integer because of custom sort
		String[] keptItems = new String[posFreqCounting.size()];
		iter = posFreqCounting.iterator();
		int writePos = 0;
		while (iter.hasNext()) {
			iter.advance();
			keptItems[writePos] = iter.key();
			writePos++;
		}
		Arrays.sort(keptItems, new Comparator<String>() {

			@Override
			public int compare(String o1, String o2) {
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
		TObjectIntMap<String> itemsRenaming = new TObjectIntHashMap<String>();
		String[] rebasing = new String[keptItems.length];
		for (int i = 0; i < keptItems.length; i++) {
			itemsRenaming.put(keptItems[i], i);
			rebasing[i] = keptItems[i];
		}

		// we have all of our items and their new names, read datasets one last
		// time and make BitSets
		this.positiveTransactions = new ArrayList<int[]>(nbPositiveTransactions);
		TIntList transactionBuffer = new TIntArrayList(10000);
		this.itemPresenceMapPositive = new TIntObjectHashMap<>(posFreqCounting.size());
		br = new BufferedReader(new FileReader(positiveDataset));
		int lineNumber = 0;
		while ((line = br.readLine()) != null) {
			if (!line.isEmpty()) {
				String[] sp = line.split(Main.separator);
				for (int i = 0; i < sp.length; i++) {
					String itemOldId = sp[i];
					if (posFreqCounting.containsKey(itemOldId)) {
						int item = itemsRenaming.get(itemOldId);
						transactionBuffer.add(item);
						TIntList[] bsArray = this.itemPresenceMapPositive.get(item);
						if (bsArray == null) {
							bsArray = new TIntList[nbPositiveTransactions];
							this.itemPresenceMapPositive.put(item, bsArray);
						}
						if (bsArray[lineNumber] == null) {
							bsArray[lineNumber] = new TIntArrayList();
						}
						bsArray[lineNumber].add(i);
					} else {
						transactionBuffer.add(-1);
					}
				}
				this.positiveTransactions.add(transactionBuffer.toArray());
				transactionBuffer.clear();
				lineNumber++;
			}
		}
		br.close();
		this.negativeTransactions = new ArrayList<int[]>(nbNegativeTransactions);
		this.itemPresenceMapNegative = new TIntObjectHashMap<>(negFreqCounting.size());
		br = new BufferedReader(new FileReader(negativeDataset));
		lineNumber = 0;
		while ((line = br.readLine()) != null) {
			if (!line.isEmpty()) {
				String[] sp = line.split(Main.separator);
				for (int i = 0; i < sp.length; i++) {
					String itemOldId = sp[i];
					if (negFreqCounting.containsKey(itemOldId)) {
						int item = itemsRenaming.get(itemOldId);
						transactionBuffer.add(item);
						TIntList[] bsArray = this.itemPresenceMapNegative.get(item);
						if (bsArray == null) {
							bsArray = new TIntList[nbNegativeTransactions];
							this.itemPresenceMapNegative.put(item, bsArray);
						}
						if (bsArray[lineNumber] == null) {
							bsArray[lineNumber] = new TIntArrayList();
						}
						bsArray[lineNumber].add(i);
					}
				}
				this.negativeTransactions.add(transactionBuffer.toArray());
				transactionBuffer.clear();
				lineNumber++;
			}
		}
		br.close();
		this.possibleExtensions = this.itemPresenceMapPositive.keys();
		this.originalPosTransactionsMapping = null;
		this.originalNegTransactionsMapping = null;
		this.resultsCollector = collector;
		this.resultsCollector.setRebasing(rebasing);
		this.resultsCollector.setEmergingItems(emergingItems);
		System.err.println(this.itemPresenceMapPositive.size() + " frequent non emerging items in dataset");
	}

	protected TransactionsBasedDataset(TransactionsBasedDataset parentDataset, int expansionItem,
			List<List<PositionAndProvenance>> expandedPosPositionsCompacted,
			List<List<PositionAndProvenance>> expandedNegPositionsCompacted,
			TIntObjectMap<TIntList[]> newItemPresenceMapPositive, TIntObjectMap<TIntList[]> newItemPresenceMapNegative,
			int[] expandedPosTransactionsMapping, int[] expandedNegTransactionsMapping) {
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
		this.positiveTransactions = parentDataset.positiveTransactions;
		this.negativeTransactions = parentDataset.negativeTransactions;
		this.originalPosTransactionsMapping = expandedPosTransactionsMapping;
		this.originalNegTransactionsMapping = expandedNegTransactionsMapping;
		this.possibleExtensions = this.computePossibleExtensions();
	}

	@Override
	final public int[] getExtensions() {
		return this.possibleExtensions;
	}

	@Override
	final public TransactionsBasedDataset expand(final int expansionItem) throws EmergingParentException,
			EmergingExpansionException, InfrequentException, DeadEndException, BackScanException {
		// compute support count in positive dataset
		final TIntList[] expansionItemPosPositions = this.itemPresenceMapPositive.get(expansionItem);
		final List<List<PositionAndProvenance>> expandedPosPositions = new ArrayList<List<PositionAndProvenance>>(
				expansionItemPosPositions.length);
		// for custom optimization, not in original algorithm
		final int[] expandedPosLastPosition = new int[expansionItemPosPositions.length];
		int posSupport = expansionItemPosPositions.length;
		for (int i = 0; i < expansionItemPosPositions.length; i++) {
			if (expansionItemPosPositions[i] != null) {
				if (currentSeqPresencePositive != null) {
					expandedPosPositions.add(this.findMatchingPosition(i, true, expansionItemPosPositions[i]));
				} else {
					// starter item, sequence currently empty
					final List<PositionAndProvenance> lp = new ArrayList<>(expansionItemPosPositions[i].size());
					expandedPosPositions.add(lp);
					expansionItemPosPositions[i].forEach(new TIntProcedure() {

						@Override
						public boolean execute(int p) {
							lp.add(new PositionAndProvenance(p));
							return true;
						}
					});
				}
				if (expandedPosPositions.get(i) == null) {
					posSupport--;
					if (posSupport < posFreqLowerBound) {
						// support of expansion in positive will be too low
						throw new InfrequentException();
					}
				} else {
					expandedPosLastPosition[i] = this.findLastOccurence(expandedPosPositions.get(i));
				}
			} else {
				posSupport--;
				if (posSupport < posFreqLowerBound) {
					// support of expansion in positive will be too low
					// not supposed to occure since we already selected
					// extension items based on support
					throw new InfrequentException();
				}
				expandedPosPositions.add(null);
			}
		}
		// if we reach this point, the expanded sequence is frequent in the
		// positive dataset
		// we now compute the support in the negative dataset
		final TIntList[] expansionItemNegPositions = this.itemPresenceMapNegative.get(expansionItem);
		int negSupport = 0;
		boolean emerging = false;
		List<List<PositionAndProvenance>> expandedNegPositions = null;
		int[] expandedNegLastPosition = null;
		if (expansionItemNegPositions == null) {
			emerging = true;
			negSupport = 0;
		} else {
			negSupport = expansionItemNegPositions.length;
			expandedNegPositions = new ArrayList<>(expansionItemNegPositions.length);
			// for custom optimization, not in original algorithm
			expandedNegLastPosition = new int[expansionItemNegPositions.length];
			for (int i = 0; !emerging && i < expansionItemNegPositions.length; i++) {
				if (expansionItemNegPositions[i] != null) {
					if (currentSeqPresenceNegative != null) {
						expandedNegPositions.add(this.findMatchingPosition(i, false, expansionItemNegPositions[i]));
					} else {
						// starter item, sequence currently empty
						final List<PositionAndProvenance> lp = new ArrayList<>(expansionItemNegPositions[i].size());
						expandedNegPositions.add(lp);
						expansionItemNegPositions[i].forEach(new TIntProcedure() {

							@Override
							public boolean execute(int p) {
								lp.add(new PositionAndProvenance(p));
								return true;
							}
						});
					}
					if (expandedNegPositions.get(i) == null) {
						negSupport--;
						if (negSupport <= negFreqUpperBound) {
							// support of expansion in positive will be too low
							emerging = true;
						}
					} else {
						expandedNegLastPosition[i] = this.findLastOccurence(expandedNegPositions.get(i));
					}
				} else {
					negSupport--;
					if (negSupport <= negFreqUpperBound) {
						// support of expansion in positive will be too low
						emerging = true;
					}
					expandedNegPositions.add(null);
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

		if (this.checkBackscan(expandedPosPositions, expandedNegPositions)) {
			throw new BackScanException();
		}

		// if we reach this point we should have expandedNegPositions and
		// expandedNegFirstPosition != null

		// now prepare the new presence list of items in positive, eliminate the
		// ones that
		// are not frequent anymore, and note the ones that could be in closure
		// final TIntList potentialClosure = new TIntArrayList();
		final TIntObjectMap<TIntList[]> newItemPresenceMapPositive = new TIntObjectHashMap<>(
				itemPresenceMapPositive.size());
		final int finalPosSupport = posSupport;
		this.itemPresenceMapPositive.forEachEntry(new TIntObjectProcedure<TIntList[]>() {

			@Override
			public boolean execute(int k, TIntList[] v) {
				TIntList[] newKPresence = new TIntList[finalPosSupport];
				int writeIndex = 0;
				int kSupport = 0;
				for (int i = 0; i < expandedPosPositions.size(); i++) {
					if (expandedPosPositions.get(i) != null) {
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

		// if there are potential future expansions
		if (!newItemPresenceMapPositive.isEmpty()) {
			// we prepare the new presence list of items in negative
			final TIntObjectMap<TIntList[]> newItemPresenceMapNegative = new TIntObjectHashMap<>(
					itemPresenceMapNegative.size());
			final int finalNegSupport = negSupport;
			final List<List<PositionAndProvenance>> finalExpandedNegPositions = expandedNegPositions;
			final int[] finalExpandedNegLastPosition = expandedNegLastPosition;
			this.itemPresenceMapNegative.forEachEntry(new TIntObjectProcedure<TIntList[]>() {

				@Override
				public boolean execute(int k, TIntList[] v) {
					TIntList[] newKPresence = new TIntList[finalNegSupport];
					int writeIndex = 0;
					int kSupport = 0;
					for (int i = 0; i < finalExpandedNegPositions.size(); i++) {
						if (finalExpandedNegPositions.get(i) != null) {
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

			// we can now shift expanded positions to fill the null entries
			final List<List<PositionAndProvenance>> expandedPosPositionsCompacted = new ArrayList<>(finalPosSupport);
			final int[] expandedPosTransactionsMapping = new int[finalPosSupport];
			int writeIndex = 0;
			for (int i = 0; i < expandedPosPositions.size(); i++) {
				if (expandedPosPositions.get(i) != null) {
					expandedPosPositionsCompacted.add(expandedPosPositions.get(i));
					if (this.originalPosTransactionsMapping == null) {
						expandedPosTransactionsMapping[writeIndex] = i;
					} else {
						expandedPosTransactionsMapping[writeIndex] = this.originalPosTransactionsMapping[i];
					}
					writeIndex++;
				}
			}
			final List<List<PositionAndProvenance>> expandedNegPositionsCompacted = new ArrayList<>(finalNegSupport);
			final int[] expandedNegTransactionsMapping = new int[finalNegSupport];
			writeIndex = 0;
			for (int i = 0; i < expandedNegPositions.size(); i++) {
				if (expandedNegPositions.get(i) != null) {
					expandedNegPositionsCompacted.add(expandedNegPositions.get(i));
					if (this.originalNegTransactionsMapping == null) {
						expandedNegTransactionsMapping[writeIndex] = i;
					} else {
						expandedNegTransactionsMapping[writeIndex] = this.originalNegTransactionsMapping[i];
					}
					writeIndex++;
				}
			}

			// we have all we need, instantiate dataset
			return this.inistantiateDataset(expansionItem, expandedPosPositionsCompacted,
					expandedNegPositionsCompacted, newItemPresenceMapPositive, newItemPresenceMapNegative,
					expandedPosTransactionsMapping, expandedNegTransactionsMapping);
		} else {
			throw new DeadEndException();
		}
	}

	protected List<PositionAndProvenance> findMatchingPosition(int transIndex, boolean positive,
			TIntList extensionItemPos) {
		List<PositionAndProvenance> seqPos;
		if (positive) {
			seqPos = this.currentSeqPresencePositive.get(transIndex);
		} else {
			seqPos = this.currentSeqPresenceNegative.get(transIndex);
		}
		List<PositionAndProvenance> res = null;
		int currentSeqIndex = 0;
		// Iterator<PositionAndProvenance> currentSeqIter = seqPos.iterator();
		TIntIterator expansionIterator = extensionItemPos.iterator();
		int validAreaEnd = seqPos.get(currentSeqIndex).getPosition();// non
																		// inclusive
		int validAreaStart = validAreaEnd - 1 - this.getGapConstraint();// inclusive
		currentSeqIndex++;
		while (validAreaStart != Integer.MIN_VALUE && expansionIterator.hasNext()) {
			int expansionPos = expansionIterator.next();
			while (expansionPos >= validAreaEnd) {
				if (currentSeqIndex < seqPos.size()) {
					validAreaEnd = seqPos.get(currentSeqIndex).getPosition();
					validAreaStart = validAreaEnd - 1 - this.getGapConstraint();
					currentSeqIndex++;
				} else {
					validAreaStart = Integer.MIN_VALUE;
					break;
				}
			}
			if (validAreaStart != Integer.MIN_VALUE && expansionPos >= validAreaStart) {
				if (res == null) {
					res = new ArrayList<>();
				}
				final int provenanceFirstIndex = currentSeqIndex - 1;
				int provenanceLastIndex = currentSeqIndex;
				for (provenanceLastIndex = currentSeqIndex; provenanceLastIndex < seqPos.size()
						&& seqPos.get(provenanceLastIndex).getPosition() > expansionPos; provenanceLastIndex++) {
				}
				res.add(new PositionAndProvenance(expansionPos, provenanceFirstIndex, provenanceLastIndex, seqPos));
			}
		}
		return res;
	}

	protected int[] computePossibleExtensions() {
		TIntSet extInTrans = new TIntHashSet();
		TIntIntMap occCount = new TIntIntHashMap();
		for (int i = 0; i < this.currentSeqPresencePositive.size(); i++) {
			int[] transaction = this.positiveTransactions.get(this.originalPosTransactionsMapping[i]);
			Iterator<PositionAndProvenance> currentSeqIter = this.currentSeqPresencePositive.get(i).iterator();
			int validAreaStart;
			int validAreaEnd = 0;
			while (currentSeqIter.hasNext()) {
				int oldEnd = validAreaEnd;
				validAreaEnd = currentSeqIter.next().getPosition();// non
																	// inclusive
				validAreaStart = Math.max(validAreaEnd - 1 - this.getGapConstraint(), oldEnd);// inclusive
				for (int j = validAreaStart; j < validAreaEnd; j++) {
					if (transaction[j] != -1 && this.itemPresenceMapPositive.containsKey(transaction[j])) {
						extInTrans.add(transaction[j]);
					}
				}
			}
			TIntIterator validExtInTrans = extInTrans.iterator();
			while (validExtInTrans.hasNext()) {
				occCount.adjustOrPutValue(validExtInTrans.next(), 1, 1);
			}
			extInTrans.clear();
		}
		TIntIntIterator occCountIter = occCount.iterator();
		while (occCountIter.hasNext()) {
			occCountIter.advance();
			if (occCountIter.value() < this.posFreqLowerBound) {
				occCountIter.remove();
			}
		}
		return occCount.keys();
	}

	protected TransactionsBasedDataset inistantiateDataset(int expansionItem,
			List<List<PositionAndProvenance>> expandedPosPositionsCompacted,
			List<List<PositionAndProvenance>> expandedNegPositionsCompacted,
			TIntObjectMap<TIntList[]> newItemPresenceMapPositive, TIntObjectMap<TIntList[]> newItemPresenceMapNegative,
			int[] expandedPosTransactionsMapping, int[] expandedNegTransactionsMapping) {
		return new TransactionsBasedDataset(this, expansionItem, expandedPosPositionsCompacted,
				expandedNegPositionsCompacted, newItemPresenceMapPositive, newItemPresenceMapNegative,
				expandedPosTransactionsMapping, expandedNegTransactionsMapping);
	}

	protected int findLastOccurence(List<PositionAndProvenance> seqPos) {
		return seqPos.get(seqPos.size() - 1).getPosition();
	}

	protected boolean hasOccurenceBefore(TIntList pos, int lim) {
		return pos.get(0) < lim;
	}

	final public int[] getSequence() {
		return this.sequence;
	}

	@Override
	public ResultsCollector getResultsCollector() {
		return this.resultsCollector;
	}

	public int getGapConstraint() {
		return this.gapConstraint;
	}

	// true if there is a backspace that is present in all occurrences of the
	// pattern
	private boolean checkBackscan(List<List<PositionAndProvenance>> expandedPosPositions,
			List<List<PositionAndProvenance>> expandedNegPositions) {
		TIntSet inter = null;
		List<TransIdAndInterval> prevItemPosOccurences = null;
		for (int i = 0; i < expandedPosPositions.size(); i++) {
			if (expandedPosPositions.get(i) != null) {
				prevItemPosOccurences = new ArrayList<>();
				TransIdAndInterval workInProgress = null;
				for (PositionAndProvenance p : expandedPosPositions.get(i)) {
					if (p.getProvenanceLastIndex() != -1) {
						if (workInProgress == null) {
							workInProgress = new TransIdAndInterval(i, p.getProvenanceFirstIndex(),
									p.getProvenanceLastIndex(), p.getCorrespondingList());
						} else {
							if (!workInProgress.merge(p)) {
								prevItemPosOccurences.add(workInProgress);
								workInProgress = new TransIdAndInterval(i, p.getProvenanceFirstIndex(),
										p.getProvenanceLastIndex(), p.getCorrespondingList());
							}
						}
					}
					if (inter == null) {
						inter = new TIntHashSet();
						int backspaceBound;
						int[] transaction;
						if (p.getProvenanceLastIndex() == -1) {
							transaction = this.positiveTransactions.get(i);
							backspaceBound = Math
									.min(p.getPosition() + this.getGapConstraint() + 1, transaction.length);
						} else {
							transaction = this.positiveTransactions.get(this.originalPosTransactionsMapping[i]);
							backspaceBound = Math.min(
									this.currentSeqPresencePositive.get(i).get(p.getProvenanceLastIndex() - 1)
											.getPosition(), transaction.length);
						}
						for (int pos = p.getPosition() + 1; pos < backspaceBound; pos++) {
							inter.add(transaction[pos]);
						}
					} else {
						if (!inter.isEmpty()) {
							TIntSet present = new TIntHashSet();
							int backspaceBound;
							int[] transaction;
							if (p.getProvenanceLastIndex() == -1) {
								backspaceBound = p.getPosition() + this.getGapConstraint() + 1;
								transaction = this.positiveTransactions.get(i);
							} else {
								backspaceBound = this.currentSeqPresencePositive.get(i)
										.get(p.getProvenanceLastIndex() - 1).getPosition();
								transaction = this.positiveTransactions.get(this.originalPosTransactionsMapping[i]);
							}
							for (int pos = p.getPosition() + 1; pos < backspaceBound; pos++) {
								present.add(transaction[pos]);
							}
							inter.retainAll(present);
						}
					}
				}
				if (workInProgress != null) {
					prevItemPosOccurences.add(workInProgress);
				}
			}
		}
		List<TransIdAndInterval> prevItemNegOccurences = null;
		for (int i = 0; i < expandedNegPositions.size(); i++) {
			if (expandedNegPositions.get(i) != null) {
				prevItemNegOccurences = new ArrayList<>();
				TransIdAndInterval workInProgress = null;
				for (PositionAndProvenance p : expandedNegPositions.get(i)) {
					if (p.getProvenanceLastIndex() != -1) {
						if (workInProgress == null) {
							workInProgress = new TransIdAndInterval(i, p.getProvenanceFirstIndex(),
									p.getProvenanceLastIndex(), p.getCorrespondingList());
						} else {
							if (!workInProgress.merge(p)) {
								prevItemNegOccurences.add(workInProgress);
								workInProgress = new TransIdAndInterval(i, p.getProvenanceFirstIndex(),
										p.getProvenanceLastIndex(), p.getCorrespondingList());
							}
						}
					}
					if (inter == null) {
						inter = new TIntHashSet();
						int backspaceBound;
						int[] transaction;
						if (p.getProvenanceLastIndex() == -1) {
							transaction = this.negativeTransactions.get(i);
							backspaceBound = Math
									.min(p.getPosition() + this.getGapConstraint() + 1, transaction.length);
						} else {
							transaction = this.negativeTransactions.get(this.originalNegTransactionsMapping[i]);
							backspaceBound = Math.min(
									this.currentSeqPresenceNegative.get(i).get(p.getProvenanceLastIndex() - 1)
											.getPosition(), transaction.length);
						}
						for (int pos = p.getPosition() + 1; pos < backspaceBound; pos++) {
							inter.add(transaction[pos]);
						}
					} else {
						if (!inter.isEmpty()) {
							TIntSet present = new TIntHashSet();
							int backspaceBound;
							int[] transaction;
							if (p.getProvenanceLastIndex() == -1) {
								transaction = this.negativeTransactions.get(i);
								backspaceBound = Math.min(p.getPosition() + this.getGapConstraint() + 1,
										transaction.length);

							} else {
								transaction = this.negativeTransactions.get(this.originalNegTransactionsMapping[i]);
								backspaceBound = Math.min(
										this.currentSeqPresenceNegative.get(i).get(p.getProvenanceLastIndex() - 1)
												.getPosition(), transaction.length);
							}
							for (int pos = p.getPosition() + 1; pos < backspaceBound; pos++) {
								present.add(transaction[pos]);
							}
							inter.retainAll(present);
						}
					}
				}
				if (workInProgress != null) {
					prevItemNegOccurences.add(workInProgress);
				}
			}
		}
		if (!inter.isEmpty()) {
			return true;
		} else if ((prevItemPosOccurences == null || prevItemPosOccurences.isEmpty())
				&& (prevItemNegOccurences == null || prevItemNegOccurences.isEmpty())) {
			return false;
		} else {
			return recCheckBackscan(prevItemPosOccurences, prevItemNegOccurences);
		}
	}

	private boolean recCheckBackscan(List<TransIdAndInterval> posOccurences, List<TransIdAndInterval> negOccurences) {
		TIntSet inter = null;
		List<TransIdAndInterval> prevItemPosOccurences = null;
		if (!posOccurences.isEmpty()) {
			prevItemPosOccurences = new ArrayList<>();
			TransIdAndInterval workInProgress = null;
			int transactionInProgress = -1;
			for (TransIdAndInterval tidInter : posOccurences) {
				for (PositionAndProvenance p : tidInter.getCorrespondingList().subList(
						tidInter.getProvenanceFirstIndex(), tidInter.getProvenanceLastIndex())) {
					if (p.getProvenanceLastIndex() != -1) {
						if (workInProgress == null) {
							workInProgress = new TransIdAndInterval(tidInter.getTransId(), p.getProvenanceFirstIndex(),
									p.getProvenanceLastIndex(), p.getCorrespondingList());
							transactionInProgress = tidInter.getTransId();
						} else {
							if (transactionInProgress != tidInter.getTransId() || !workInProgress.merge(p)) {
								prevItemPosOccurences.add(workInProgress);
								workInProgress = new TransIdAndInterval(tidInter.getTransId(),
										p.getProvenanceFirstIndex(), p.getProvenanceLastIndex(),
										p.getCorrespondingList());
								transactionInProgress = tidInter.getTransId();
							}
						}
					}
					if (inter == null) {
						inter = new TIntHashSet();
						int backspaceBound;
						int[] transaction;
						if (p.getProvenanceLastIndex() == -1) {
							transaction = this.positiveTransactions.get(tidInter.getTransId());
							backspaceBound = Math
									.min(p.getPosition() + this.getGapConstraint() + 1, transaction.length);
						} else {
							transaction = this.positiveTransactions.get(this.originalPosTransactionsMapping[tidInter
									.getTransId()]);
							backspaceBound = Math.min(p.getCorrespondingList().get(p.getProvenanceLastIndex() - 1)
									.getPosition(), transaction.length);
						}
						for (int pos = p.getPosition() + 1; pos < backspaceBound; pos++) {
							inter.add(transaction[pos]);
						}
					} else {
						if (!inter.isEmpty()) {
							TIntSet present = new TIntHashSet();
							int backspaceBound;
							int[] transaction;
							if (p.getProvenanceLastIndex() == -1) {
								transaction = this.positiveTransactions.get(tidInter.getTransId());
								backspaceBound = Math.min(p.getPosition() + this.getGapConstraint() + 1,
										transaction.length);
							} else {
								transaction = this.positiveTransactions
										.get(this.originalPosTransactionsMapping[tidInter.getTransId()]);
								backspaceBound = Math.min(p.getCorrespondingList().get(p.getProvenanceLastIndex() - 1)
										.getPosition(), transaction.length);
							}
							for (int pos = p.getPosition() + 1; pos < backspaceBound; pos++) {
								present.add(transaction[pos]);
							}
							inter.retainAll(present);
						}
					}
				}
			}
			if (workInProgress != null) {
				prevItemPosOccurences.add(workInProgress);
			}
		}
		List<TransIdAndInterval> prevItemNegOccurences = null;
		if (!negOccurences.isEmpty()) {
			prevItemNegOccurences = new ArrayList<>();
			TransIdAndInterval workInProgress = null;
			int transactionInProgress = -1;
			for (TransIdAndInterval tidInter : negOccurences) {
				for (PositionAndProvenance p : tidInter.getCorrespondingList().subList(
						tidInter.getProvenanceFirstIndex(), tidInter.getProvenanceLastIndex())) {
					if (p.getProvenanceLastIndex() != -1) {
						if (workInProgress == null) {
							workInProgress = new TransIdAndInterval(tidInter.getTransId(), p.getProvenanceFirstIndex(),
									p.getProvenanceLastIndex(), p.getCorrespondingList());
							transactionInProgress = tidInter.getTransId();
						} else {
							if (transactionInProgress != tidInter.getTransId() || !workInProgress.merge(p)) {
								prevItemNegOccurences.add(workInProgress);
								workInProgress = new TransIdAndInterval(tidInter.getTransId(),
										p.getProvenanceFirstIndex(), p.getProvenanceLastIndex(),
										p.getCorrespondingList());
								transactionInProgress = tidInter.getTransId();
							}
						}
					}
					if (inter == null) {
						inter = new TIntHashSet();
						int backspaceBound;
						int[] transaction;
						if (p.getProvenanceLastIndex() == -1) {
							transaction = this.negativeTransactions.get(tidInter.getTransId());
							backspaceBound = Math
									.min(p.getPosition() + this.getGapConstraint() + 1, transaction.length);
						} else {
							transaction = this.negativeTransactions.get(this.originalNegTransactionsMapping[tidInter
									.getTransId()]);
							backspaceBound = Math.min(p.getCorrespondingList().get(p.getProvenanceLastIndex() - 1)
									.getPosition(), transaction.length);
						}
						for (int pos = p.getPosition() + 1; pos < backspaceBound; pos++) {
							inter.add(transaction[pos]);
						}
					} else {
						if (!inter.isEmpty()) {
							TIntSet present = new TIntHashSet();
							int backspaceBound;
							int[] transaction;
							if (p.getProvenanceLastIndex() == -1) {
								transaction = this.negativeTransactions.get(tidInter.getTransId());
								backspaceBound = Math.min(p.getPosition() + this.getGapConstraint() + 1,
										transaction.length);
							} else {

								transaction = this.negativeTransactions
										.get(this.originalNegTransactionsMapping[tidInter.getTransId()]);
								backspaceBound = Math.min(p.getCorrespondingList().get(p.getProvenanceLastIndex() - 1)
										.getPosition(), transaction.length);
							}
							for (int pos = p.getPosition() + 1; pos < backspaceBound; pos++) {
								present.add(transaction[pos]);
							}
							inter.retainAll(present);
						}
					}
				}
			}
			if (workInProgress != null) {
				prevItemNegOccurences.add(workInProgress);
			}
		}
		if (!inter.isEmpty()) {
			return true;
		} else if ((prevItemPosOccurences == null || prevItemPosOccurences.isEmpty())
				&& (prevItemNegOccurences == null || prevItemNegOccurences.isEmpty())) {
			return false;
		} else {
			return recCheckBackscan(prevItemPosOccurences, prevItemNegOccurences);
		}
	}
}
