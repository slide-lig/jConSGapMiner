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
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;

import fr.liglab.consgap.dataset.lcmstyle.TransactionsBasedDataset;

public class BatchFilteringResultsCollector extends ResultsCollector {

	private List<int[]> collectedSeq;
	private TreeNode filteringTree;
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
		TreeNode root = this.filteringTree;
		if (root != null) {
			if (checkEmergingContainsMinimal(root, fullSeq)) {
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
			TreeNode newRoot = new TreeNode();
			List<int[]> filtered = getNonRedundant(newRoot, batchSeq);
			this.filteringTree = newRoot;
			synchronized (this) {
				this.collectedSeq.addAll(filtered);
				this.batchInProgress = false;
			}
		}
		return EmergingStatus.NEW_EMERGING;
	}

	@Override
	public EmergingStatus hasPotential(int[] sequence, int expansionItem) {
		TreeNode root = this.filteringTree;
		if (root != null) {
			int checkRes = checkSeqContainsMinimal(root, sequence, expansionItem);
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
		List<int[]> filtered = getNonRedundant(new TreeNode(), this.collectedSeq);
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

	private static List<int[]> getNonRedundant(TreeNode rootNode, List<int[]> sequences) {
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
			if (!checkEmergingContainsMinimal(rootNode, seq)) {
				nonRedundant.add(seq);
				// insert in tree
				insertIntoTree(rootNode, seq);
			}
		}
		return nonRedundant;
	}

	static private int checkSeqContainsMinimal(TreeNode rootNode, int[] seq, int expansion) {
		for (int i = 0; i < seq.length; i++) {
			if (recursiveSubsetCheck(rootNode, seq, i)) {
				return 1;
			}
		}
		TreeNode t = rootNode.get(expansion);
		if (t != null) {
			if (recursiveSubsetCheck(t, seq, 0)) {
				return 0;
			}
		}
		return -1;
	}

	static private boolean checkEmergingContainsMinimal(TreeNode rootNode, int[] seq) {
		for (int i = 0; i < seq.length; i++) {
			if (recursiveSubsetCheck(rootNode, seq, i)) {
				return true;
			}
		}
		return false;
	}

	// if contains a minimal, then first item has to be in
	static private boolean recursiveSubsetCheck(TreeNode currentNode, int[] seq, int from) {
		for (int i = from; i < seq.length; i++) {
			TreeNode nextNode = currentNode.get(seq[i]);
			if (nextNode != null) {
				if (nextNode.seqFragment != null) {
					int seqFragIndex = 0;
					for (int j = i + 1; j < seq.length; j++) {
						if (nextNode.seqFragment[seqFragIndex] == seq[j]) {
							seqFragIndex++;
							if (seqFragIndex == nextNode.seqFragment.length) {
								if (nextNode.isEmpty()) {
									return true;
								} else if (recursiveSubsetCheck(nextNode, seq, j + 1)) {
									return true;
								} else {
									break;
								}
							}
						}
					}
				} else {
					// if it's a leaf, we're done
					if (nextNode.isEmpty()) {
						return true;
					} else if (recursiveSubsetCheck(nextNode, seq, i + 1)) {
						return true;
					}
				}
			}
		}
		return false;
	}

	static private void insertIntoTree(TreeNode rootNode, int[] seq) {
		TreeNode currentNode = rootNode;
		for (int i = 0; i < seq.length; i++) {
			int item = seq[i];
			TreeNode nextNode = currentNode.get(item);
			if (nextNode == null) {
				nextNode = new TreeNode();
				currentNode.put(item, nextNode);
				if (i != seq.length - 1) {
					nextNode.seqFragment = new int[seq.length - (i + 1)];
					System.arraycopy(seq, i + 1, nextNode.seqFragment, 0, seq.length - (i + 1));
				}
				return;
			} else if (nextNode.seqFragment != null) {
				int diffDelta = -1;
				for (int j = 0; j < nextNode.seqFragment.length; j++) {
					if (nextNode.seqFragment[j] != seq[i + j + 1]) {
						diffDelta = j;
						break;
					}
				}
				if (diffDelta == -1) {
					i += nextNode.seqFragment.length;
					currentNode = nextNode;
				} else {
					TreeNode replacementNode = new TreeNode();
					currentNode.put(item, replacementNode);
					TreeNode replacedNode = nextNode;
					if (diffDelta == 0) {
						replacementNode.put(replacedNode.seqFragment[0], replacedNode);
						if (replacedNode.seqFragment.length == 1) {
							replacedNode.seqFragment = null;
						} else {
							int[] seqFrag = new int[replacedNode.seqFragment.length - 1];
							System.arraycopy(replacedNode.seqFragment, 1, seqFrag, 0,
									replacedNode.seqFragment.length - 1);
							replacedNode.seqFragment = seqFrag;
						}
						currentNode = replacementNode;
					} else {
						replacementNode.put(replacedNode.seqFragment[diffDelta], replacedNode);
						replacementNode.seqFragment = new int[diffDelta];
						System.arraycopy(replacedNode.seqFragment, 0, replacementNode.seqFragment, 0, diffDelta);
						if (replacedNode.seqFragment.length == diffDelta + 1) {
							replacedNode.seqFragment = null;
						} else {
							int[] seqFrag = new int[replacedNode.seqFragment.length - diffDelta - 1];
							System.arraycopy(replacedNode.seqFragment, diffDelta + 1, seqFrag, 0,
									replacedNode.seqFragment.length - diffDelta - 1);
							replacedNode.seqFragment = seqFrag;
						}
						currentNode = replacementNode;
						i += diffDelta;
					}
				}
			} else {
				currentNode = nextNode;
			}
		}
	}

	@Override
	public void setPrefixFilter(PrefixCollector prefixFilter) {
		throw new UnsupportedOperationException();
	}

	public static void main(String[] args) {
		TreeNode root = new TreeNode();
		insertIntoTree(root, new int[] { 1, 2, 3, 4 });
		System.out.println(root);
		insertIntoTree(root, new int[] { 1, 2, 6, 7 });
		System.out.println(root);
	}
}
