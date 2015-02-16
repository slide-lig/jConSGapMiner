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

package fr.liglab.consgap.executor;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;

import fr.liglab.consgap.ConfStats;
import fr.liglab.consgap.dataset.Dataset;
import gnu.trove.set.TIntSet;
import gnu.trove.set.hash.TIntHashSet;

public class MiningStep {
	final private Dataset dataset;
	final private AtomicInteger extensionsIndex;
	final private int[] extensions;
	final private boolean isRoot;
	final private TIntSet deniedExtensionsParent;
	final private TIntSet deniedExtensionsChildren;

	public MiningStep(Dataset dataset, boolean isRoot) {
		this(dataset, isRoot, null);
	}

	public MiningStep(Dataset dataset, boolean isRoot, TIntSet deniedExtensionsParent) {
		this.isRoot = isRoot;
		this.dataset = dataset;
		this.extensions = dataset.getExtensions();
		Arrays.sort(this.extensions);
		this.extensionsIndex = new AtomicInteger();
		if (ConfStats.pruneSiblingsEmerging) {
			this.deniedExtensionsParent = deniedExtensionsParent;
			if (deniedExtensionsParent == null) {
				this.deniedExtensionsChildren = new TIntHashSet();
			} else {
				synchronized (deniedExtensionsParent) {
					this.deniedExtensionsChildren = new TIntHashSet(deniedExtensionsParent);
				}
			}
		} else {
			this.deniedExtensionsChildren = null;
			this.deniedExtensionsParent = null;
		}
	}

	public MiningStep next() {
		Dataset[] expansionOutput = new Dataset[1];
		for (int index = this.extensionsIndex.getAndIncrement(); index < extensions.length; index = this.extensionsIndex
				.getAndIncrement()) {
			final int extension = extensions[index];
			if (ConfStats.pruneSiblingsEmerging && this.deniedExtensionsParent != null) {
				synchronized (this.deniedExtensionsParent) {
					if (this.deniedExtensionsParent.contains(extension)) {
						ConfStats.incEmergingSiblingsPruning();
						continue;
					}
				}
			}
			ConfStats.incExpand();
			if (isRoot) {
				ConfStats.incSeedItemStarted();
			}
			expansionOutput[0] = null;
			switch (this.dataset.expand(extension, expansionOutput)) {
			case BACKSCAN:
				break;
			case DEAD_END:
				break;
			case EMERGING:
				if (ConfStats.pruneSiblingsEmerging) {
					synchronized (this.deniedExtensionsChildren) {
						this.deniedExtensionsChildren.add(extension);
					}
				}
				break;
			case EMERGING_PARENT:
				return null;
			case INFREQUENT:
				break;
			case OK:
				return new MiningStep(expansionOutput[0], false, this.deniedExtensionsChildren);
			default:
				System.err.println("not supposed to be here, expansion status unknown");
			}
		}
		return null;
	}

	public int getLevel() {
		return this.dataset.getSequence().length;
	}

	@Override
	public String toString() {
		return Arrays.toString(this.dataset.getSequence()) + " " + this.extensionsIndex.get() + "/"
				+ this.extensions.length;
	}
}
