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

import fr.liglab.consgap.collector.ResultsCollector;
import gnu.trove.iterator.TIntIntIterator;
import gnu.trove.iterator.TIntIterator;
import gnu.trove.list.TIntList;
import gnu.trove.list.array.TIntArrayList;
import gnu.trove.map.TIntIntMap;
import gnu.trove.map.TIntObjectMap;
import gnu.trove.map.hash.TIntIntHashMap;
import gnu.trove.set.TIntSet;
import gnu.trove.set.hash.TIntHashSet;

import java.io.IOException;

final public class TransBasedListDataset extends ATransactionsBasedDataset<TIntList> {

	public TransBasedListDataset(ResultsCollector collector, String positiveDataset, String negativeDataset,
			int posFreqLowerBound, int negFreqUpperBound, int gapConstraint) throws IOException {
		super(collector, positiveDataset, negativeDataset, posFreqLowerBound, negFreqUpperBound, gapConstraint);
	}

	protected TransBasedListDataset(TransBasedListDataset parentDataset, int expansionItem,
			TIntList[] expandedPosPositionsCompacted, TIntList[] expandedNegPositionsCompacted,
			TIntObjectMap<TIntList[]> newItemPresenceMapPositive, TIntObjectMap<TIntList[]> newItemPresenceMapNegative,
			int[] expandedPosTransactionsMapping) {
		super(parentDataset, expansionItem, expandedPosPositionsCompacted, expandedNegPositionsCompacted,
				newItemPresenceMapPositive, newItemPresenceMapNegative, expandedPosTransactionsMapping);
	}

	@Override
	protected TIntList findMatchingPosition(int transIndex, boolean positive, TIntList extensionItemPos) {
		TIntList seqPos;
		if (positive) {
			seqPos = this.currentSeqPresencePositive[transIndex];
		} else {
			seqPos = this.currentSeqPresenceNegative[transIndex];
		}
		TIntList res = null;
		TIntIterator currentSeqIter = seqPos.iterator();
		TIntIterator expansionIterator = extensionItemPos.iterator();
		int validAreaEnd = currentSeqIter.next();// non inclusive
		int validAreaStart = validAreaEnd - 1 - this.getGapConstraint();// inclusive
		while (validAreaStart != Integer.MIN_VALUE && expansionIterator.hasNext()) {
			int expansionPos = expansionIterator.next();
			while (expansionPos >= validAreaEnd) {
				if (currentSeqIter.hasNext()) {
					validAreaEnd = currentSeqIter.next();
					validAreaStart = validAreaEnd - 1 - this.getGapConstraint();
				} else {
					validAreaStart = Integer.MIN_VALUE;
					break;
				}
			}
			if (validAreaStart != Integer.MIN_VALUE && expansionPos >= validAreaStart) {
				if (res == null) {
					res = new TIntArrayList();
				}
				res.add(expansionPos);
			}
		}
		return res;
	}

	@Override
	protected int[] computePossibleExtensions() {
		TIntSet extInTrans = new TIntHashSet();
		TIntIntMap occCount = new TIntIntHashMap();
		for (int i = 0; i < this.currentSeqPresencePositive.length; i++) {
			int[] transaction = this.positiveTransactions.get(this.originalPosTransactionsMapping[i]);
			TIntIterator currentSeqIter = this.currentSeqPresencePositive[i].iterator();
			int validAreaStart;
			int validAreaEnd = 0;
			while (currentSeqIter.hasNext()) {
				int oldEnd = validAreaEnd;
				validAreaEnd = currentSeqIter.next();// non inclusive
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

	@Override
	protected TIntList initEmptyStructure() {
		return new TIntArrayList();
	}

	@Override
	protected TIntList[] initStructureArray(int size) {
		return new TIntList[size];
	}

	@Override
	protected void addOccurence(int pos, TIntList struct) {
		struct.add(pos);
	}

	@Override
	protected TransBasedListDataset inistantiateDataset(int expansionItem, TIntList[] expandedPosPositionsCompacted,
			TIntList[] expandedNegPositionsCompacted, TIntObjectMap<TIntList[]> newItemPresenceMapPositive,
			TIntObjectMap<TIntList[]> newItemPresenceMapNegative, int[] expandedPosTransactionsMapping) {
		return new TransBasedListDataset(this, expansionItem, expandedPosPositionsCompacted,
				expandedNegPositionsCompacted, newItemPresenceMapPositive, newItemPresenceMapNegative,
				expandedPosTransactionsMapping);
	}

	@Override
	protected int findLastOccurence(TIntList seqPos) {
		return seqPos.get(seqPos.size() - 1);
	}

	@Override
	protected boolean hasOccurenceBefore(TIntList pos, int lim) {
		return pos.get(0) < lim;
	}

}
