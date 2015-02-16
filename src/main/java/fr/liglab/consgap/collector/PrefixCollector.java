package fr.liglab.consgap.collector;

import gnu.trove.map.hash.TIntObjectHashMap;
import gnu.trove.set.TIntSet;

public interface PrefixCollector {
	public void collectPrefix(int[] sequence, TIntSet prefix);

	public int filter(int[] sequence);

	public static class PrefixTreeNode extends TIntObjectHashMap<PrefixTreeNode> {
	}
}
