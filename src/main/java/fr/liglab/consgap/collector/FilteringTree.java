package fr.liglab.consgap.collector;

import gnu.trove.map.hash.TIntObjectHashMap;

import java.util.Arrays;

public class FilteringTree {
	private TreeNode root;

	public FilteringTree() {
		this.root = new TreeNode();
	}

	public void insert(int[] seq) {
		int end = seq.length;
		if (seq[seq.length - 1] < 0) {
			end += seq[seq.length - 1];
		}
		TreeNode currentNode = this.root;
		for (int i = 0; i < end; i++) {
			int item = seq[i];
			TreeNode nextNode = currentNode.get(item);
			if (nextNode == null) {
				nextNode = new TreeNode();
				currentNode.put(item, nextNode);
				if (i != end - 1) {
					nextNode.seqFragment = new int[end - (i + 1)];
					System.arraycopy(seq, i + 1, nextNode.seqFragment, 0, end - (i + 1));
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

	public boolean checkSeqContainsMinimal(int[] seq) {
		if (recursiveSubsetCheck(this.root, seq, 0)) {
			return true;
		}
		return false;
	}

	public int checkSeqContainsMinimal(int[] seq, int expansion) {
		if (recursiveSubsetCheck(this.root, seq, 0)) {
			return 1;
		}
		TreeNode nextNode = this.root.get(expansion);
		if (nextNode != null) {
			if (nextNode.seqFragment == null) {
				if (recursiveSubsetCheck(nextNode, seq, 0)) {
					return 0;
				}
			} else {
				int seqFragIndex = 0;
				for (int j = 0; j < seq.length; j++) {
					if (nextNode.seqFragment[seqFragIndex] == seq[j]) {
						seqFragIndex++;
						if (seqFragIndex == nextNode.seqFragment.length) {
							if (nextNode.isEmpty()) {
								return 0;
							} else if (recursiveSubsetCheck(nextNode, seq, j + 1)) {
								return 0;
							} else {
								break;
							}
						}
					}
				}
			}
		}
		return -1;
	}

	// if contains a minimal, then first item has to be in
	static private boolean recursiveSubsetCheck(TreeNode currentNode, int[] seq, int from) {
		for (int i = from; i < seq.length; i++) {
			if (seq[i] < 0) {
				break;
			}
			TreeNode nextNode = currentNode.get(seq[i]);
			if (nextNode != null) {
				if (nextNode.seqFragment != null) {
					int seqFragIndex = 0;
					for (int j = i + 1; j < seq.length; j++) {
						if (seq[j] < 0) {
							break;
						}
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

	private static class TreeNode extends TIntObjectHashMap<TreeNode> {
		public int[] seqFragment;

		@Override
		public String toString() {
			return "TreeNode [super=" + super.toString() + " seqFragment=" + Arrays.toString(seqFragment) + "]";
		}
	}
}
