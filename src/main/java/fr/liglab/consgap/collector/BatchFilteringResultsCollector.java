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
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;

public class BatchFilteringResultsCollector extends ResultsCollector {

	private List<int[]> collectedSeq;
	private FilteringTree filteringTree;
	private int nbCollected;
	private int collectSinceBatch;
	private final int interBatchDelay;
	private boolean batchInProgress;

	// in this class, we build tree and check from the last item in the sequence
	// to detect collision in prefix first as it gives more pruning
	public BatchFilteringResultsCollector(int interBatchDelay) {
		this.collectedSeq = new ArrayList<>();
		this.nbCollected = 0;
		this.filteringTree = null;
		this.collectSinceBatch = 0;
		this.interBatchDelay = interBatchDelay;
		this.batchInProgress = false;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see fr.liglab.consgap.internals.ResultsCollector#collect(int[], int)
	 */
	@Override
	public EmergingStatus collect(int[] sequence, int expansionItem) {
		int[] fullSeq = new int[sequence.length + 1];
		System.arraycopy(sequence, 0, fullSeq, 1, sequence.length);
		fullSeq[0] = expansionItem;
		FilteringTree tree = this.filteringTree;
		if (tree != null) {
			if (tree.checkSeqContainsMinimal(fullSeq)) {
				return EmergingStatus.EMERGING_WITH_EXPANSION;
			}
		}
		List<int[]> batchSeq = null;
		synchronized (this) {
			collectedSeq.add(fullSeq);
			this.nbCollected++;
			this.collectSinceBatch++;
			if (this.collectSinceBatch >= this.interBatchDelay) {
				if (!this.batchInProgress) {
					this.batchInProgress = true;
					batchSeq = this.collectedSeq;
					this.collectedSeq = new ArrayList<int[]>();
					this.collectSinceBatch = 0;
				}
			}
		}
		if (batchSeq != null) {
			FilteringTree newTree = new FilteringTree();
			List<int[]> filtered = getNonRedundant(newTree, batchSeq);
			this.filteringTree = newTree;
			synchronized (this) {
				this.collectedSeq.addAll(filtered);
				this.batchInProgress = false;
			}
		}
		return EmergingStatus.NEW_EMERGING;
	}

	@Override
	public EmergingStatus hasPotential(int[] sequence, int expansionItem) {
		FilteringTree tree = this.filteringTree;
		if (tree != null) {
			int checkRes = tree.checkSeqContainsMinimal(sequence, expansionItem);
			if (checkRes == 0) {
				return EmergingStatus.EMERGING_WITH_EXPANSION;
			} else if (checkRes > 0) {
				return EmergingStatus.EMERGING_WITHOUT_EXPANSION;
			}
		}
		return EmergingStatus.NO_EMERGING_SUBSET;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see fr.liglab.consgap.internals.ResultsCollector#getNbCollected()
	 */
	@Override
	public int getNbCollected() {
		return this.nbCollected++;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see fr.liglab.consgap.internals.ResultsCollector#getNonRedundant()
	 */
	@Override
	public List<String[]> getNonRedundant() {
		List<int[]> filtered = getNonRedundant(new FilteringTree(), this.collectedSeq);
		List<String[]> output = new ArrayList<String[]>(filtered.size() + this.emergingItems.size());
		Iterator<String> iter = this.emergingItems.iterator();
		while (iter.hasNext()) {
			output.add(new String[] { iter.next() });
		}
		for (int[] s : filtered) {
			String[] rebased = new String[s.length];
			for (int i = 0; i < s.length; i++) {
				rebased[i] = this.rebasing[s[i]];
			}
			output.add(rebased);
		}
		return output;
	}

	private static List<int[]> getNonRedundant(FilteringTree filteringTree, List<int[]> sequences) {
		// sort sequences by size
		Collections.sort(sequences, new Comparator<int[]>() {

			@Override
			public int compare(int[] o1, int[] o2) {
				int diffSize = o1.length - o2.length;
				return diffSize;
			}
		});
		List<int[]> nonRedundant = new ArrayList<>();
		for (int[] seq : sequences) {
			if (!filteringTree.checkSeqContainsMinimal(seq)) {
				nonRedundant.add(seq);
				// insert in tree
				filteringTree.insert(seq);
			}
		}
		return nonRedundant;
	}

	@Override
	public void setPrefixFilter(PrefixCollector prefixFilter) {
		throw new UnsupportedOperationException();
	}
}
