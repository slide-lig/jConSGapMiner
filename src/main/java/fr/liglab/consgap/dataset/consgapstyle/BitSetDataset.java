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

package fr.liglab.consgap.dataset.consgapstyle;

import java.io.IOException;
import java.util.BitSet;

import fr.liglab.consgap.collector.ResultsCollector;
import gnu.trove.map.TIntObjectMap;

final public class BitSetDataset extends ATidBasedDataset<BitSet> {
	// same array but OR of itself shifted g times
	private final BitSet[] currentSeqPresencePositiveShifted;
	// same array but OR of itself shifted g times
	private final BitSet[] currentSeqPresenceNegativeShifted;

	public BitSetDataset(ResultsCollector collector, String positiveDataset, String negativeDataset,
			int posFreqLowerBound, int negFreqUpperBound, int gapConstraint) throws IOException {
		super(collector, positiveDataset, negativeDataset, posFreqLowerBound, negFreqUpperBound, gapConstraint);
		this.currentSeqPresencePositiveShifted = null;
		this.currentSeqPresenceNegativeShifted = null;
	}

	protected BitSetDataset(BitSetDataset parentDataset, int expansionItem, BitSet[] expandedPosPositionsCompacted,
			BitSet[] expandedNegPositionsCompacted, TIntObjectMap<BitSet[]> newItemPresenceMapPositive,
			TIntObjectMap<BitSet[]> newItemPresenceMapNegative) {
		super(parentDataset, expansionItem, expandedPosPositionsCompacted, expandedNegPositionsCompacted,
				newItemPresenceMapPositive, newItemPresenceMapNegative);
		this.currentSeqPresencePositiveShifted = new BitSet[expandedPosPositionsCompacted.length];
		for (int i = 0; i < expandedPosPositionsCompacted.length; i++) {
			this.currentSeqPresencePositiveShifted[i] = expandedPosPositionsCompacted[i].get(1,
					expandedPosPositionsCompacted[i].length());
			for (int g = 0; g < this.getGapConstraint(); g++) {
				if (2 + g < expandedPosPositionsCompacted[i].length()) {
					this.currentSeqPresencePositiveShifted[i].or(expandedPosPositionsCompacted[i].get(2 + g,
							expandedPosPositionsCompacted[i].length()));
				}
			}
		}
		this.currentSeqPresenceNegativeShifted = new BitSet[expandedNegPositionsCompacted.length];
		for (int i = 0; i < expandedNegPositionsCompacted.length; i++) {
			this.currentSeqPresenceNegativeShifted[i] = expandedNegPositionsCompacted[i].get(1,
					expandedNegPositionsCompacted[i].length());
			for (int g = 0; g < this.getGapConstraint(); g++) {
				if (2 + g < expandedNegPositionsCompacted[i].length()) {
					this.currentSeqPresenceNegativeShifted[i].or(expandedNegPositionsCompacted[i].get(2 + g,
							expandedNegPositionsCompacted[i].length()));
				}
			}
		}
	}

	@Override
	protected BitSet initEmptyStructure() {
		return new BitSet();
	}

	@Override
	protected BitSet[] initStructureArray(int size) {
		return new BitSet[size];
	}

	@Override
	protected void addOccurence(int pos, BitSet struct) {
		struct.set(pos);
	}

	@Override
	protected BitSetDataset inistantiateDataset(int expansionItem, BitSet[] expandedPosPositionsCompacted,
			BitSet[] expandedNegPositionsCompacted, TIntObjectMap<BitSet[]> newItemPresenceMapPositive,
			TIntObjectMap<BitSet[]> newItemPresenceMapNegative) {
		return new BitSetDataset(this, expansionItem, expandedPosPositionsCompacted, expandedNegPositionsCompacted,
				newItemPresenceMapPositive, newItemPresenceMapNegative);
	}

	@Override
	protected BitSet findMatchingPosition(int transIndex, boolean positive, BitSet extensionItemPos) {
		BitSet seqPos;
		if (positive) {
			seqPos = this.currentSeqPresencePositiveShifted[transIndex];
		} else {
			seqPos = this.currentSeqPresenceNegativeShifted[transIndex];
		}
		BitSet match = (BitSet) seqPos.clone();
		match.and(extensionItemPos);
		if (match.isEmpty()) {
			return null;
		} else {
			return match;
		}
	}

	@Override
	protected int findLastOccurence(BitSet seqPos) {
		return seqPos.previousSetBit(seqPos.length() - 1);
	}

	@Override
	protected boolean hasOccurenceBefore(BitSet pos, int lim) {
		return pos.previousSetBit(lim - 1) >= 0;
	}

}
