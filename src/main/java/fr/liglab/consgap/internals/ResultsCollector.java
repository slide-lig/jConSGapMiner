package fr.liglab.consgap.internals;

import gnu.trove.TIntCollection;
import gnu.trove.map.hash.TIntObjectHashMap;

import java.util.List;

public abstract class ResultsCollector {
	int[] rebasing;
	TIntCollection emergingItems;

	public void setRebasing(int[] rebasing) {
		this.rebasing = rebasing;
	}

	public void setEmergingItems(TIntCollection emergingItems) {
		this.emergingItems = emergingItems;
	}

	public abstract EmergingStatus collect(int[] sequence, int expansionItem);

	public abstract int getNbCollected();

	public abstract List<int[]> getNonRedundant();

	public static enum EmergingStatus {
		NEW_EMERGING, EMERGING_WITHOUT_EXPANSION, EMERGING_WITH_EXPANSION, NO_EMERGING_SUBSET
	}

	public static class TreeNode extends TIntObjectHashMap<TreeNode> {
	}
}