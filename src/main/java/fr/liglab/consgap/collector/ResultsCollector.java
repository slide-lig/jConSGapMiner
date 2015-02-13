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

import gnu.trove.map.hash.TIntObjectHashMap;

import java.util.Collection;
import java.util.List;

public abstract class ResultsCollector {
	String[] rebasing;
	Collection<String> emergingItems;

	public void setRebasing(String[] rebasing) {
		this.rebasing = rebasing;
	}

	public void setEmergingItems(Collection<String> emergingItems) {
		this.emergingItems = emergingItems;
	}

	public abstract EmergingStatus collect(int[] sequence, int expansionItem);

	public abstract int getNbCollected();

	public abstract List<String[]> getNonRedundant();

	public abstract void setPrefixFilter(PrefixCollector prefixFilter);

	public abstract EmergingStatus hasPotential(int[] sequence, int expansionItem);

	public static enum EmergingStatus {
		NEW_EMERGING, EMERGING_WITHOUT_EXPANSION, EMERGING_WITH_EXPANSION, NO_EMERGING_SUBSET
	}

	public static class TreeNode extends TIntObjectHashMap<TreeNode> {
	}
}