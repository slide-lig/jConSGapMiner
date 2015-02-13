package fr.liglab.consgap.collector;

import gnu.trove.map.hash.TIntObjectHashMap;
import gnu.trove.set.TIntSet;

public interface PrefixCollector {
	public void collectPrefix(int[] sequence, int extension, TIntSet prefix);

	public void filter(int[] sequence);

	public static class PrefixTreeNode extends TIntObjectHashMap<PrefixTreeNode> {
	}
}
