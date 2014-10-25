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

package fr.liglab.consgap.internals.intlist;

import java.io.IOException;

import fr.liglab.consgap.internals.ADataset;
import fr.liglab.consgap.internals.ResultsCollector;
import gnu.trove.iterator.TIntIterator;
import gnu.trove.list.TIntList;
import gnu.trove.list.array.TIntArrayList;
import gnu.trove.map.TIntObjectMap;

final public class ListDataset extends ADataset<TIntList> {

	public ListDataset(ResultsCollector collector, String positiveDataset, String negativeDataset,
			int posFreqLowerBound, int negFreqUpperBound, int gapConstraint) throws IOException {
		super(collector, positiveDataset, negativeDataset, posFreqLowerBound, negFreqUpperBound, gapConstraint);
	}

	protected ListDataset(ListDataset parentDataset, int expansionItem, TIntList[] expandedPosPositionsCompacted,
			TIntList[] expandedNegPositionsCompacted, TIntObjectMap<TIntList[]> newItemPresenceMapPositive,
			TIntObjectMap<TIntList[]> newItemPresenceMapNegative) {
		super(parentDataset, expansionItem, expandedPosPositionsCompacted, expandedNegPositionsCompacted,
				newItemPresenceMapPositive, newItemPresenceMapNegative);
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
	protected ADataset<TIntList> inistantiateDataset(int expansionItem, TIntList[] expandedPosPositionsCompacted,
			TIntList[] expandedNegPositionsCompacted, TIntObjectMap<TIntList[]> newItemPresenceMapPositive,
			TIntObjectMap<TIntList[]> newItemPresenceMapNegative) {
		return new ListDataset(this, expansionItem, expandedPosPositionsCompacted, expandedNegPositionsCompacted,
				newItemPresenceMapPositive, newItemPresenceMapNegative);
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
