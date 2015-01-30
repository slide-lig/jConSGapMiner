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
	private final TreeNode filteringTree;

	// in this class, we build tree and check from the last item in the sequence
	// to detect collision in prefix first as it gives more pruning
	public OrderedResultsCollector() {
		this.collectedSeq = new ArrayList<>();
		this.filteringTree = new TreeNode();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see fr.liglab.consgap.internals.ResultsCollector#collect(int[], int)
	 */
	@Override
	public synchronized EmergingStatus collect(int[] sequence, int expansionItem) {
		int[] fullSeq = new int[sequence.length + 1];
		System.arraycopy(sequence, 0, fullSeq, 1, sequence.length);
		fullSeq[0] = expansionItem;
		int lastPos = recursiveSubsetCheck(this.filteringTree, fullSeq, fullSeq.length - 1);
		if (lastPos >= 0) {
			if (lastPos == 0) {
				return EmergingStatus.EMERGING_WITH_EXPANSION;
			} else {
				return EmergingStatus.EMERGING_WITHOUT_EXPANSION;
			}
		} else {
			insertIntoTree(this.filteringTree, fullSeq);
			String[] rebased = new String[fullSeq.length];
			for (int i = 0; i < fullSeq.length; i++) {
				rebased[i] = this.rebasing[fullSeq[i]];
			}
			this.collectedSeq.add(rebased);
			return EmergingStatus.NEW_EMERGING;
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

	// checks from left to right
	static private int recursiveSubsetCheck(TreeNode currentNode, int[] seq, int from) {
		for (int i = from; i >= 0; i--) {
			TreeNode nextNode = currentNode.get(seq[i]);
			if (nextNode != null) {
				// if it's a leaf, we're done
				if (nextNode.isEmpty()) {
					return i;
				} else {
					int subSetCheck = recursiveSubsetCheck(nextNode, seq, i - 1);
					if (subSetCheck >= 0) {
						return subSetCheck;
					}
				}
			}
		}
		return -1;
	}

	static private void insertIntoTree(TreeNode rootNode, int[] seq) {
		TreeNode currentNode = rootNode;
		for (int i = seq.length - 1; i >= 0; i--) {
			int item = seq[i];
			TreeNode nextNode = currentNode.get(item);
			if (nextNode == null) {
				nextNode = new TreeNode();
				currentNode.put(item, nextNode);
			}
			currentNode = nextNode;
		}
	}
}
