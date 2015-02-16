package fr.liglab.consgap.collector;

import gnu.trove.iterator.TIntIterator;
import gnu.trove.set.TIntSet;

public class PostFilteringPrefixCollector implements PrefixCollector {
	private final PrefixTreeNode rootNode;

	public PostFilteringPrefixCollector() {
		this.rootNode = new PrefixTreeNode();
	}

	@Override
	public void collectPrefix(int[] sequence, TIntSet prefix) {
		synchronized (this) {
			insertIntoTree(sequence, prefix);
		}
	}

	// returns how many items should be ignored from the end of the seq
	public int filter(int[] sequence) {
		int firstKept = this.prefixCheck(sequence);
		return (sequence.length - 1 - firstKept);
	}

	private void insertIntoTree(final int[] seq, final TIntSet prefix) {
		TIntIterator prefixIterator = prefix.iterator();
		while (prefixIterator.hasNext()) {
			PrefixTreeNode currentNode = this.rootNode;
			{
				final int prefixItem = prefixIterator.next();
				PrefixTreeNode nextNode = currentNode.get(prefixItem);
				if (nextNode == null) {
					nextNode = new PrefixTreeNode();
					currentNode.put(prefixItem, nextNode);
				}
				currentNode = nextNode;
			}
			for (int i = seq.length - 1; i >= 0; i--) {
				final int item = seq[i];
				PrefixTreeNode nextNode = currentNode.get(item);
				if (nextNode == null) {
					nextNode = new PrefixTreeNode();
					currentNode.put(item, nextNode);
				}
				currentNode = nextNode;
			}
		}
	}

	// returns the first index we keep
	private int prefixCheck(int[] seq) {
		// >0 because we ignore last item as emergence test before backspace
		// test
		for (int i = seq.length - 1; i > 0; i--) {
			PrefixTreeNode currentNode = this.rootNode.get(seq[i]);
			boolean matched = false;
			if (currentNode != null) {
				for (int j = i - 1; !matched && j >= 0; j--) {
					PrefixTreeNode nextNode = currentNode.get(seq[j]);
					if (nextNode == null) {
						return i;
					} else if (nextNode.isEmpty()) {
						matched = true;
					} else {
						currentNode = nextNode;
					}
				}
			}
			if (!matched) {
				return i;
			}
		}
		return seq.length;
	}
}
