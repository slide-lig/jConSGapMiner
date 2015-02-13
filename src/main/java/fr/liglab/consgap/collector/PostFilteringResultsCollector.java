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

public class PostFilteringResultsCollector extends ResultsCollector {

	final private List<int[]> collectedSeq;
	private PrefixCollector prefixFilter;

	public PostFilteringResultsCollector() {
		this.collectedSeq = new ArrayList<>(1000000);
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

	@Override
	public EmergingStatus hasPotential(int[] sequence, int expansionItem) {
		return EmergingStatus.NO_EMERGING_SUBSET;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see fr.liglab.consgap.internals.ResultsCollector#getNonRedundant()
	 */
	@Override
	public List<String[]> getNonRedundant() {
		List<String[]> nonRedundant = new ArrayList<>();
		Iterator<String> iter = this.emergingItems.iterator();
		while (iter.hasNext()) {
			nonRedundant.add(new String[] { iter.next() });
		}
		if (this.prefixFilter != null) {
			for (int[] seq : collectedSeq) {
				this.prefixFilter.filter(seq);
			}
			Collections.sort(collectedSeq, new Comparator<int[]>() {

				@Override
				public int compare(int[] o1, int[] o2) {
					int s1 = o1.length;
					if (o1[o1.length - 1] < 0) {
						s1 = -o1[o1.length - 1] + 1;
					}
					int s2 = o2.length;
					if (o2[o2.length - 1] < 0) {
						s2 = -o2[o2.length - 1] + 1;
					}
					return s1 - s2;
				}
			});
			TreeNode rootNode = new TreeNode();
			for (int[] seq : collectedSeq) {
				int end = seq.length;
				if (seq[seq.length - 1] < 0) {
					end = -seq[seq.length - 1] + 1;
				}
				if (!recursiveSubsetCheck(rootNode, seq, 0, end)) {
					String[] rebasedSeq = new String[end];
					for (int i = 0; i < end; i++) {
						rebasedSeq[i] = this.rebasing[seq[i]];
					}
					nonRedundant.add(rebasedSeq);
					// insert in tree
					insertIntoTree(rootNode, seq, end);
				}
			}
		} else {
			// not consistent with equals but who cares
			Collections.sort(collectedSeq, new Comparator<int[]>() {

				@Override
				public int compare(int[] o1, int[] o2) {
					return o1.length - o2.length;
				}
			});
			TreeNode rootNode = new TreeNode();
			for (int[] seq : collectedSeq) {
				if (!recursiveSubsetCheck(rootNode, seq)) {
					String[] rebasedSeq = new String[seq.length];
					for (int i = 0; i < rebasedSeq.length; i++) {
						rebasedSeq[i] = this.rebasing[seq[i]];
					}
					nonRedundant.add(rebasedSeq);
					// insert in tree
					insertIntoTree(rootNode, seq);
				}
			}
		}
		return nonRedundant;
	}

	static private boolean recursiveSubsetCheck(TreeNode currentNode, int[] seq, int from, int to) {
		for (int i = from; i < to; i++) {
			TreeNode nextNode = currentNode.get(seq[i]);
			if (nextNode != null) {
				// if it's a leaf, we're done
				if (nextNode.isEmpty()) {
					return true;
				} else {
					if (recursiveSubsetCheck(nextNode, seq, i + 1, to)) {
						return true;
					}
				}
			}
		}
		return false;
	}

	static private boolean recursiveSubsetCheck(TreeNode currentNode, int[] seq) {
		return recursiveSubsetCheck(currentNode, seq, 0, seq.length);
	}

	static private boolean recursiveSubsetCheck(TreeNode currentNode, int[] seq, int from) {
		return recursiveSubsetCheck(currentNode, seq, from, seq.length);
	}

	static private void insertIntoTree(TreeNode rootNode, int[] seq) {
		insertIntoTree(rootNode, seq, seq.length);
	}

	static private void insertIntoTree(TreeNode rootNode, int[] seq, int end) {
		TreeNode currentNode = rootNode;
		for (int i = 0; i < end; i++) {
			int item = seq[i];
			TreeNode nextNode = currentNode.get(item);
			if (nextNode == null) {
				nextNode = new TreeNode();
				currentNode.put(item, nextNode);
			}
			currentNode = nextNode;
		}
	}

	@Override
	public void setPrefixFilter(PrefixCollector prefixFilter) {
		this.prefixFilter = prefixFilter;
	}

}
