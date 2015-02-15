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

package fr.liglab.consgap.collector;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class OrderedResultsCollector extends ResultsCollector {
	// gets results in order of length, shorter first
	private final List<String[]> collectedSeq;
	private final FilteringTree filteringTree;

	// in this class, we build tree and check from the last item in the sequence
	// to detect collision in prefix first as it gives more pruning
	public OrderedResultsCollector() {
		this.collectedSeq = new ArrayList<>(10000);
		this.filteringTree = new FilteringTree();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see fr.liglab.consgap.internals.ResultsCollector#collect(int[], int)
	 */
	@Override
	public EmergingStatus collect(int[] sequence, int expansionItem) {
		int[] fullSeq = null;
		synchronized (this.filteringTree) {
			int checkRes = this.filteringTree.checkSeqContainsMinimal(sequence, expansionItem);
			if (checkRes == 1) {
				return EmergingStatus.EMERGING_WITHOUT_EXPANSION;
			} else if (checkRes == 0) {
				return EmergingStatus.EMERGING_WITH_EXPANSION;
			} else {
				fullSeq = new int[sequence.length + 1];
				System.arraycopy(sequence, 0, fullSeq, 1, sequence.length);
				fullSeq[0] = expansionItem;
				this.filteringTree.insert(fullSeq);
			}
		}
		String[] rebased = new String[fullSeq.length];
		for (int i = 0; i < fullSeq.length; i++) {
			rebased[i] = this.rebasing[fullSeq[i]];
		}
		synchronized (this.collectedSeq) {
			this.collectedSeq.add(rebased);
		}
		return EmergingStatus.NEW_EMERGING;
	}

	@Override
	public synchronized EmergingStatus hasPotential(int[] sequence, int expansionItem) {
		int checkRes = this.filteringTree.checkSeqContainsMinimal(sequence, expansionItem);
		if (checkRes < 0) {
			return EmergingStatus.NO_EMERGING_SUBSET;
		} else if (checkRes == 0) {
			return EmergingStatus.EMERGING_WITH_EXPANSION;
		} else {
			return EmergingStatus.EMERGING_WITHOUT_EXPANSION;
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see fr.liglab.consgap.internals.ResultsCollector#getNbCollected()
	 */
	@Override
	public int getNbCollected() {
		return this.collectedSeq.size();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see fr.liglab.consgap.internals.ResultsCollector#getNonRedundant()
	 */
	@Override
	public List<String[]> getNonRedundant() {
		List<String[]> filtered = this.collectedSeq;
		Iterator<String> iter = this.emergingItems.iterator();
		while (iter.hasNext()) {
			filtered.add(new String[] { iter.next() });
		}
		return filtered;
	}

	@Override
	public void setPrefixFilter(PrefixCollector prefixFilter) {
		throw new UnsupportedOperationException();
	}
}
