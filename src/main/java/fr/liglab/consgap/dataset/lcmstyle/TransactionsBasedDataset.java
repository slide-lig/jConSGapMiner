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
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import fr.liglab.consgap.Main;
import fr.liglab.consgap.collector.PrefixCollector;
import fr.liglab.consgap.collector.ResultsCollector;
import fr.liglab.consgap.collector.ResultsCollector.EmergingStatus;
import fr.liglab.consgap.dataset.Dataset;
import gnu.trove.iterator.TIntIntIterator;
import gnu.trove.iterator.TIntIterator;
import gnu.trove.iterator.TObjectIntIterator;
import gnu.trove.list.TIntList;
import gnu.trove.list.array.TIntArrayList;
import gnu.trove.map.TIntIntMap;
import gnu.trove.map.TObjectIntMap;
import gnu.trove.map.hash.TIntIntHashMap;
import gnu.trove.map.hash.TObjectIntHashMap;
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
	protected final TIntList[][] itemPresenceMapPositive;
	private final TIntList[][] itemPresenceMapNegative;
	protected final int posFreqLowerBound;// >=
	private final int negFreqUpperBound;// <=
	private final int gapConstraint;
	private final int[] sequence;
	private final ResultsCollector resultsCollector;
	private final PrefixCollector prefixCollector;
	private final int[] possibleExtensions;
	protected final int[] originalPosTransactionsMapping;
	protected final int[] originalNegTransactionsMapping;

	// public static int[] interestingPattern;
	// public static int interestingExtension;
	// public static boolean isInteresting;

	// public static int[] interestingCase;
	// private static boolean trace = false;

	public TransactionsBasedDataset(ResultsCollector collector, PrefixCollector prefCollector, String positiveDataset,
			String negativeDataset, int posFreqLowerBound, int negFreqUpperBound, int gapConstraint) throws IOException {
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
			// itemsRenaming.put(keptItems[i], Integer.parseInt(keptItems[i]));
			itemsRenaming.put(keptItems[i], i);
			rebasing[i] = keptItems[i];
		}
		// we have all of our items and their new names, read datasets one last
		// time and make BitSets
		this.positiveTransactions = new ArrayList<int[]>(nbPositiveTransactions);
		TIntList transactionBuffer = new TIntArrayList(10000);
		this.itemPresenceMapPositive = new TIntList[keptItems.length][nbPositiveTransactions];
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
						TIntList[] bsArray = this.itemPresenceMapPositive[item];
						if (bsArray == null) {
							bsArray = new TIntList[nbPositiveTransactions];
							this.itemPresenceMapPositive[item] = bsArray;
						}
						if (bsArray[lineNumber] == null) {
							bsArray[lineNumber] = new TIntArrayList();
						}
						bsArray[lineNumber].insert(0, i);
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
		this.itemPresenceMapNegative = new TIntList[keptItems.length][nbNegativeTransactions];
		br = new BufferedReader(new FileReader(negativeDataset));
		lineNumber = 0;
		while ((line = br.readLine()) != null) {
			if (!line.isEmpty()) {
				String[] sp = line.split(Main.separator);
				for (int i = 0; i < sp.length; i++) {
					String itemOldId = sp[i];
					if (posFreqCounting.containsKey(itemOldId)) {
						int item = itemsRenaming.get(itemOldId);
						transactionBuffer.add(item);
						TIntList[] bsArray = this.itemPresenceMapNegative[item];
						if (bsArray == null) {
							bsArray = new TIntList[nbNegativeTransactions];
							this.itemPresenceMapNegative[item] = bsArray;
						}
						if (bsArray[lineNumber] == null) {
							bsArray[lineNumber] = new TIntArrayList();
						}
						bsArray[lineNumber].insert(0, i);
					} else {
						transactionBuffer.add(-1);
					}
				}
				this.negativeTransactions.add(transactionBuffer.toArray());
				transactionBuffer.clear();
				lineNumber++;
			}
		}
		br.close();
		this.possibleExtensions = new int[keptItems.length];
		for (int i = 0; i < possibleExtensions.length; i++) {
			possibleExtensions[i] = i;
		}
		this.originalPosTransactionsMapping = null;
		this.originalNegTransactionsMapping = null;
		this.resultsCollector = collector;
		this.prefixCollector = prefCollector;
		this.resultsCollector.setRebasing(rebasing);
		this.resultsCollector.setEmergingItems(emergingItems);
		if (this.prefixCollector != null) {
			this.resultsCollector.setPrefixFilter(this.prefixCollector);
		}
		System.out.println(possibleExtensions.length + " frequent non emerging items in dataset and "
				+ emergingItems.size() + " emerging items");
		// int[] originalInterestingPattern = { 7226, 7209 };
		// interestingPattern = new int[originalInterestingPattern.length];
		// for (int i = 0; i < originalInterestingPattern.length; i++) {
		// interestingPattern[i] = itemsRenaming.get("" +
		// originalInterestingPattern[i]);
		// }
		// interestingExtension = itemsRenaming.get("6688");
		// System.out.println("130 is " + rebasing[130]);
	}

	protected TransactionsBasedDataset(TransactionsBasedDataset parentDataset, int expansionItem,
			List<List<PositionAndProvenance>> expandedPosPositionsCompacted,
			List<List<PositionAndProvenance>> expandedNegPositionsCompacted, int[] expandedPosTransactionsMapping,
			int[] expandedNegTransactionsMapping) {
		this.posFreqLowerBound = parentDataset.posFreqLowerBound;
		this.negFreqUpperBound = parentDataset.negFreqUpperBound;
		this.gapConstraint = parentDataset.gapConstraint;
		this.sequence = new int[parentDataset.sequence.length + 1];
		System.arraycopy(parentDataset.sequence, 0, this.sequence, 1, parentDataset.sequence.length);
		this.sequence[0] = expansionItem;
		this.resultsCollector = parentDataset.resultsCollector;
		this.itemPresenceMapPositive = parentDataset.itemPresenceMapPositive;
		this.itemPresenceMapNegative = parentDataset.itemPresenceMapNegative;
		this.currentSeqPresencePositive = expandedPosPositionsCompacted;
		this.currentSeqPresenceNegative = expandedNegPositionsCompacted;
		this.positiveTransactions = parentDataset.positiveTransactions;
		this.negativeTransactions = parentDataset.negativeTransactions;
		this.originalPosTransactionsMapping = expandedPosTransactionsMapping;
		this.originalNegTransactionsMapping = expandedNegTransactionsMapping;
		this.possibleExtensions = this.computePossibleExtensions();
		this.prefixCollector = parentDataset.prefixCollector;
		// int shift = 11;
		// isInteresting = false;
		// if (this.sequence.length == interestingPattern.length - shift) {
		// isInteresting = true;
		// for (int i = 0; isInteresting && i < this.sequence.length; i++) {
		// if (this.sequence[i] != interestingPattern[i + shift]) {
		// isInteresting = false;
		// }
		// }
		// }
		// if (isInteresting) {
		// System.out.println("ho");
		// }
	}

	@Override
	final public int[] getExtensions() {
		return this.possibleExtensions;
	}

	@Override
	final public ExpandStatus expand(final int expansionItem, Dataset[] expandedDataset) {
		// isInteresting = false;
		// int shift = 1;
		// if (this.sequence.length == interestingPattern.length - shift) {
		// isInteresting = true;
		// for (int i = 0; isInteresting && i < this.sequence.length; i++) {
		// if (this.sequence[i] != interestingPattern[i + shift]) {
		// isInteresting = false;
		// }
		// }
		// }
		// if (isInteresting) {
		// if (shift == 0) {
		// isInteresting = expansionItem == interestingExtension;
		// } else {
		// isInteresting = expansionItem == interestingPattern[shift - 1];
		// }
		// }
		// if (isInteresting) {
		// System.out.println("hello");
		// }
		// compute support count in positive dataset
		final TIntList[] expansionItemPosPositions = this.itemPresenceMapPositive[expansionItem];
		List<List<PositionAndProvenance>> expandedPosPositions;
		int posSupport;
		if (this.currentSeqPresencePositive == null) {
			expandedPosPositions = new ArrayList<List<PositionAndProvenance>>(expansionItemPosPositions.length);
			posSupport = expansionItemPosPositions.length;
			for (TIntList positions : expansionItemPosPositions) {
				if (positions == null) {
					posSupport--;
					if (posSupport < posFreqLowerBound) {
						throw new RuntimeException("not supposed to happen anymore");
					}
					expandedPosPositions.add(null);
				} else {
					final List<PositionAndProvenance> lp = new ArrayList<>(positions.size());
					expandedPosPositions.add(lp);
					positions.forEach(new TIntProcedure() {

						@Override
						public boolean execute(int p) {
							lp.add(new PositionAndProvenance(p));
							return true;
						}
					});
				}
			}
		} else {
			expandedPosPositions = new ArrayList<List<PositionAndProvenance>>(this.currentSeqPresencePositive.size());
			posSupport = this.currentSeqPresencePositive.size();
			for (int i = 0; i < this.currentSeqPresencePositive.size(); i++) {
				List<PositionAndProvenance> matching = null;
				if (expansionItemPosPositions[this.originalPosTransactionsMapping[i]] != null) {
					matching = this.findMatchingPosition(i, true,
							expansionItemPosPositions[this.originalPosTransactionsMapping[i]]);
				}
				expandedPosPositions.add(matching);
				if (matching == null) {
					posSupport--;
					if (posSupport < posFreqLowerBound) {
						throw new RuntimeException("not supposed to happen anymore");
					}
				}
			}
		}
		// if we reach this point, the expanded sequence is frequent in the
		// positive dataset
		// we now compute the support in the negative dataset
		final TIntList[] expansionItemNegPositions = this.itemPresenceMapNegative[expansionItem];
		int negSupport = 0;
		boolean emerging = false;
		List<List<PositionAndProvenance>> expandedNegPositions;
		if (expansionItemNegPositions != null) {
			expandedNegPositions = new ArrayList<List<PositionAndProvenance>>(expansionItemNegPositions.length);
			if (this.currentSeqPresenceNegative == null) {
				negSupport = expansionItemNegPositions.length;
				for (TIntList positions : expansionItemNegPositions) {
					if (positions == null) {
						negSupport--;
						expandedNegPositions.add(null);
					} else {
						final List<PositionAndProvenance> lp = new ArrayList<>(positions.size());
						expandedNegPositions.add(lp);
						positions.forEach(new TIntProcedure() {

							@Override
							public boolean execute(int p) {
								lp.add(new PositionAndProvenance(p));
								return true;
							}
						});
					}
				}
				if (negSupport <= negFreqUpperBound) {
					throw new RuntimeException("singletons are not supposed to be emerging");
				}
			} else {
				expandedNegPositions = new ArrayList<List<PositionAndProvenance>>(
						this.currentSeqPresenceNegative.size());
				negSupport = this.currentSeqPresenceNegative.size();
				for (int i = 0; !emerging && i < this.currentSeqPresenceNegative.size(); i++) {
					List<PositionAndProvenance> matching = null;
					if (expansionItemNegPositions[this.originalNegTransactionsMapping[i]] != null) {
						matching = this.findMatchingPosition(i, false,
								expansionItemNegPositions[this.originalNegTransactionsMapping[i]]);
					}
					expandedNegPositions.add(matching);
					if (matching == null) {
						negSupport--;
						if (negSupport <= negFreqUpperBound) {
							emerging = true;
						}
					}
				}
			}
		} else {
			expandedNegPositions = Collections.emptyList();
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
			return ExpandStatus.EMERGING_PARENT;
		case EMERGING_WITH_EXPANSION:
			return ExpandStatus.EMERGING;
		case NEW_EMERGING:
			return ExpandStatus.EMERGING;
		case NO_EMERGING_SUBSET:
			break;
		default:
			break;
		}
		if (this.prefixCollector != null) {
			TIntSet prefix = this.checkBackscan(expandedPosPositions, expandedNegPositions);
			if (prefix != null) {
				this.prefixCollector.collectPrefix(this.sequence, expansionItem, prefix);
				return ExpandStatus.BACKSCAN;
			}
		}
		// if there are potential future expansions
		// we can now shift expanded positions to fill the null entries
		final List<List<PositionAndProvenance>> expandedPosPositionsCompacted = new ArrayList<>(posSupport);
		final int[] expandedPosTransactionsMapping = new int[posSupport];
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
		final List<List<PositionAndProvenance>> expandedNegPositionsCompacted = new ArrayList<>(negSupport);
		final int[] expandedNegTransactionsMapping = new int[negSupport];
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
		// check if this future expansion has potential
		switch (this.resultsCollector.hasPotential(this.sequence, expansionItem)) {
		case EMERGING_WITHOUT_EXPANSION:
			return ExpandStatus.EMERGING_PARENT;
		case EMERGING_WITH_EXPANSION:
			return ExpandStatus.DEAD_END;
		case NO_EMERGING_SUBSET:
			expandedDataset[0] = this.inistantiateDataset(expansionItem, expandedPosPositionsCompacted,
					expandedNegPositionsCompacted, expandedPosTransactionsMapping, expandedNegTransactionsMapping);
			return ExpandStatus.OK;
		default:
			throw new RuntimeException("not supposed to be here");
		}
	}

	protected List<PositionAndProvenance> findMatchingPosition(int transIndex, boolean positive,
			TIntList extensionItemPos) {
		if (extensionItemPos == null) {
			return null;
		}
		List<PositionAndProvenance> seqPos;
		if (positive) {
			seqPos = this.currentSeqPresencePositive.get(transIndex);
		} else {
			seqPos = this.currentSeqPresenceNegative.get(transIndex);
		}
		ArrayList<PositionAndProvenance> res = null;
		int currentSeqIndex = 0;
		// Iterator<PositionAndProvenance> currentSeqIter = seqPos.iterator();
		TIntIterator expansionIterator = extensionItemPos.iterator();
		int validAreaEnd = seqPos.get(currentSeqIndex).getPosition();// non
																		// inclusive
		int validAreaStart = validAreaEnd - 1 - this.getGapConstraint();// inclusive
		currentSeqIndex++;
		while (validAreaStart != Integer.MIN_VALUE && expansionIterator.hasNext()) {
			int expansionPos = expansionIterator.next();
			while (expansionPos < validAreaStart) {
				if (currentSeqIndex < seqPos.size()) {
					validAreaEnd = seqPos.get(currentSeqIndex).getPosition();
					validAreaStart = validAreaEnd - 1 - this.getGapConstraint();
					currentSeqIndex++;
				} else {
					validAreaStart = Integer.MIN_VALUE;
					break;
				}
			}
			if (validAreaStart != Integer.MIN_VALUE && expansionPos < validAreaEnd) {
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
		if (res != null) {
			res.trimToSize();
		}
		return res;
	}

	protected int[] computePossibleExtensions() {
		TIntSet extInTrans = new TIntHashSet();
		TIntIntMap occCount = new TIntIntHashMap();
		for (int i = 0; i < this.currentSeqPresencePositive.size(); i++) {
			int[] transaction = this.positiveTransactions.get(this.originalPosTransactionsMapping[i]);
			Iterator<PositionAndProvenance> currentSeqIter = this.currentSeqPresencePositive.get(i).iterator();
			int validAreaStart = Integer.MAX_VALUE;
			int validAreaEnd;
			while (currentSeqIter.hasNext()) {
				int oldStart = validAreaStart;
				validAreaEnd = currentSeqIter.next().getPosition();// non
																	// inclusive
				validAreaStart = Math.max(validAreaEnd - 1 - this.getGapConstraint(), 0);// inclusive
				validAreaEnd = Math.min(oldStart, validAreaEnd);
				for (int j = validAreaStart; j < validAreaEnd; j++) {
					if (transaction[j] != -1) {
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
			List<List<PositionAndProvenance>> expandedNegPositionsCompacted, int[] expandedPosTransactionsMapping,
			int[] expandedNegTransactionsMapping) {
		return new TransactionsBasedDataset(this, expansionItem, expandedPosPositionsCompacted,
				expandedNegPositionsCompacted, expandedPosTransactionsMapping, expandedNegTransactionsMapping);
	}

	protected int findLastOccurence(List<PositionAndProvenance> seqPos) {
		return seqPos.get(0).getPosition();
	}

	protected boolean hasOccurenceBefore(TIntList pos, int lim) {
		return pos.get(pos.size() - 1) < lim;
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

	private static TIntSet getTransactionBackSpace(int[] transaction, int position, int gap) {
		int backspaceBound = Math.min(position + gap + 2, transaction.length);
		int startPos = position + 1;
		TIntSet out = new TIntHashSet();
		for (int pos = startPos; pos < backspaceBound; pos++) {
			if (transaction[pos] != -1) {
				out.add(transaction[pos]);
			}
		}
		return out;
	}

	private TIntSet checkBackscan(List<List<PositionAndProvenance>> expandedPosPositions,
			List<List<PositionAndProvenance>> expandedNegPositions) {
		TIntSet inter = null;
		int[] lastIndexPerDepth = new int[this.sequence.length];
		for (int i = 0; i < expandedPosPositions.size(); i++) {
			List<PositionAndProvenance> lPos = expandedPosPositions.get(i);
			if (lPos != null) {
				Arrays.fill(lastIndexPerDepth, 0);
				int[] transaction;
				if (this.originalPosTransactionsMapping != null) {
					transaction = this.positiveTransactions.get(this.originalPosTransactionsMapping[i]);
				} else {
					transaction = this.positiveTransactions.get(i);
				}
				for (PositionAndProvenance pos : lPos) {
					inter = this.recCheckBackscan(pos, transaction, lastIndexPerDepth, 0, inter);
				}
				if (inter.isEmpty()) {
					return null;
				}
			}
		}
		for (int i = 0; i < expandedNegPositions.size(); i++) {
			List<PositionAndProvenance> lPos = expandedNegPositions.get(i);
			if (lPos != null) {
				Arrays.fill(lastIndexPerDepth, 0);
				int[] transaction;
				if (this.originalNegTransactionsMapping != null) {
					transaction = this.negativeTransactions.get(this.originalNegTransactionsMapping[i]);
				} else {
					transaction = this.negativeTransactions.get(i);
				}
				for (PositionAndProvenance pos : lPos) {
					inter = this.recCheckBackscan(pos, transaction, lastIndexPerDepth, 0, inter);
				}
				if (inter.isEmpty()) {
					return null;
				}
			}
		}
		return inter;
	}

	private TIntSet recCheckBackscan(PositionAndProvenance pos, int[] transaction, int[] lastIndexPerDepth, int depth,
			TIntSet inter) {
		if (pos.getProvenanceFirstIndex() == -1) {
			TIntSet content = getTransactionBackSpace(transaction, pos.getPosition(), this.getGapConstraint());
			if (inter == null) {
				return content;
			} else {
				inter.retainAll(content);
			}
		} else {
			for (int i = Math.max(lastIndexPerDepth[depth], pos.getProvenanceFirstIndex()); i < pos
					.getProvenanceLastIndex(); i++) {
				PositionAndProvenance newPos = pos.getCorrespondingList().get(i);
				inter = recCheckBackscan(newPos, transaction, lastIndexPerDepth, depth + 1, inter);
				if (inter.isEmpty()) {
					return inter;
				}
			}
			lastIndexPerDepth[depth] = pos.getProvenanceLastIndex();
		}
		return inter;
	}

	@Override
	public String toString() {
		return "TransactionsBasedDataset [sequence=" + Arrays.toString(sequence) + "]";
	}
}
