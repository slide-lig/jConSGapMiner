package fr.liglab.consgap.collector;

import fr.liglab.consgap.dataset.lcmstyle.TransactionsBasedDataset;
import gnu.trove.iterator.TIntIterator;
import gnu.trove.set.TIntSet;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class PostFilteringPrefixCollector implements PrefixCollector {
	final private List<PrefixInfo> collectedPrefix;
	private PrefixTreeNode rootNode;

	public PostFilteringPrefixCollector() {
		this.collectedPrefix = new ArrayList<>(100000);
	}

	@Override
	public void collectPrefix(int[] sequence, int extension, TIntSet prefix) {
//		if (prefix
//				.contains(TransactionsBasedDataset.interestingPattern[TransactionsBasedDataset.interestingPattern.length - 1])) {
//			System.err.println(prefix + " " + Arrays.toString(sequence) + " " + extension);
//		}
//		if (prefix
//				.contains(TransactionsBasedDataset.interestingPattern[TransactionsBasedDataset.interestingPattern.length - 2])) {
//			System.err.println(prefix + " " + Arrays.toString(sequence) + " " + extension);
//		}
//		if (prefix
//				.contains(TransactionsBasedDataset.interestingPattern[TransactionsBasedDataset.interestingPattern.length - 3])) {
//			System.err.println(prefix + " " + Arrays.toString(sequence) + " " + extension);
//		}
//		if (prefix
//				.contains(TransactionsBasedDataset.interestingPattern[TransactionsBasedDataset.interestingPattern.length - 4])) {
//			System.err.println(prefix + " " + Arrays.toString(sequence) + " " + extension);
//		}
//		if (prefix
//				.contains(TransactionsBasedDataset.interestingPattern[TransactionsBasedDataset.interestingPattern.length - 5])) {
//			System.err.println(prefix + " " + Arrays.toString(sequence) + " " + extension);
//		}
		synchronized (this) {
			this.collectedPrefix.add(new PrefixInfo(sequence, extension, prefix));
		}
	}

	public void filter(int[] sequence) {
//		if (Arrays.equals(sequence, TransactionsBasedDataset.interestingPattern)) {
//			System.out.println("got you");
//		}
		if (this.rootNode == null) {
			this.buildFilteringTree();
		}
		int firstKept = this.prefixCheck(sequence);
		if (firstKept != sequence.length - 1) {
			sequence[sequence.length - 1] = -firstKept;
		}
	}

	private void buildFilteringTree() {
		this.rootNode = new PrefixTreeNode();
		this.rootNode = new PrefixTreeNode();
		for (PrefixInfo prefInfo : collectedPrefix) {
			insertIntoTree(prefInfo.getSeq(), prefInfo.getExtension(), prefInfo.getPrefix());
		}
	}

	private void insertIntoTree(final int[] seq, final int extension, final TIntSet prefix) {
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
			{
				PrefixTreeNode nextNode = currentNode.get(extension);
				if (nextNode == null) {
					nextNode = new PrefixTreeNode();
					currentNode.put(extension, nextNode);
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

	private static class PrefixInfo {
		private final int[] seq;
		private final int extension;
		private final TIntSet prefix;

		public PrefixInfo(int[] seq, int extension, TIntSet prefix) {
			super();
			this.seq = seq;
			this.extension = extension;
			this.prefix = prefix;
		}

		protected final int[] getSeq() {
			return seq;
		}

		protected final int getExtension() {
			return extension;
		}

		protected final TIntSet getPrefix() {
			return prefix;
		}

	}
}
