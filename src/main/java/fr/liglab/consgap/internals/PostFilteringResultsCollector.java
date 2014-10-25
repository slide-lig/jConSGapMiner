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

import gnu.trove.iterator.TIntIterator;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class PostFilteringResultsCollector extends ResultsCollector {

	final private List<int[]> collectedSeq;

	public PostFilteringResultsCollector() {
		this.collectedSeq = new ArrayList<>();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see fr.liglab.consgap.internals.ResultsCollector#collect(int[], int)
	 */
	@Override
	public EmergingStatus collect(int[] sequence, int expansionItem) {
		// System.err.println("collecting " + expansionItem + " " +
		// Arrays.toString(sequence));
		int[] fullSeq = new int[sequence.length + 1];
		System.arraycopy(sequence, 0, fullSeq, 1, sequence.length);
		fullSeq[0] = expansionItem;
		synchronized (this) {
			collectedSeq.add(fullSeq);
		}
		return EmergingStatus.NEW_EMERGING;
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
	public List<int[]> getNonRedundant() {
		// sort sequences by size and then lexico
		Collections.sort(collectedSeq, new Comparator<int[]>() {

			@Override
			public int compare(int[] o1, int[] o2) {
				int diffSize = o1.length - o2.length;
				if (diffSize != 0) {
					return diffSize;
				} else {
					for (int i = 0; i < o1.length; i++) {
						if (o1[i] != o2[i]) {
							return o1[i] - o2[i];
						}
					}
					return 0;
				}
			}
		});
		List<int[]> nonRedundant = new ArrayList<>();
		TIntIterator iter = this.emergingItems.iterator();
		while (iter.hasNext()) {
			nonRedundant.add(new int[] { iter.next() });
		}
		TreeNode rootNode = new TreeNode();
		for (int[] seq : collectedSeq) {
			if (!recursiveSubsetCheck(rootNode, seq, 0)) {
				int[] rebasedSeq = new int[seq.length];
				for (int i = 0; i < rebasedSeq.length; i++) {
					rebasedSeq[i] = this.rebasing[seq[i]];
				}
				nonRedundant.add(rebasedSeq);
				// insert in tree
				insertIntoTree(rootNode, seq);
			}
		}
		return nonRedundant;
	}

	static private boolean recursiveSubsetCheck(TreeNode currentNode, int[] seq, int from) {
		for (int i = from; i < seq.length; i++) {
			TreeNode nextNode = currentNode.get(seq[i]);
			if (nextNode != null) {
				// if it's a leaf, we're done
				if (nextNode.isEmpty()) {
					return true;
				} else {
					if (recursiveSubsetCheck(nextNode, seq, i + 1)) {
						return true;
					}
				}
			}
		}
		return false;
	}

	static private void insertIntoTree(TreeNode rootNode, int[] seq) {
		TreeNode currentNode = rootNode;
		for (int item : seq) {
			TreeNode nextNode = currentNode.get(item);
			if (nextNode == null) {
				nextNode = new TreeNode();
				currentNode.put(item, nextNode);
			}
			currentNode = nextNode;
		}
	}

}
