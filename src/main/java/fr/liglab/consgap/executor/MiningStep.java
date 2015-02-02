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
import java.util.concurrent.atomic.AtomicLong;

import fr.liglab.consgap.dataset.Dataset;
import fr.liglab.consgap.dataset.Dataset.DeadEndException;
import fr.liglab.consgap.dataset.Dataset.EmergingExpansionException;
import fr.liglab.consgap.dataset.Dataset.EmergingParentException;
import fr.liglab.consgap.dataset.Dataset.InfrequentException;

public class MiningStep {
	static final public AtomicLong loopCounts = new AtomicLong();
	final private Dataset dataset;
	final private AtomicInteger extensionsIndex;
	final private int[] extensions;

	public MiningStep(Dataset dataset) {
		this.dataset = dataset;
		this.extensions = dataset.getExtensions();
		Arrays.sort(this.extensions);
		this.extensionsIndex = new AtomicInteger();
	}

	public MiningStep next() {
		for (int index = this.extensionsIndex.getAndIncrement(); index < extensions.length; index = this.extensionsIndex
				.getAndIncrement()) {
			loopCounts.incrementAndGet();
			final int extension = extensions[index];
			Dataset extDataset = null;
			try {
				extDataset = this.dataset.expand(extension);
			} catch (EmergingParentException e) {
				return null;
			} catch (EmergingExpansionException | DeadEndException | InfrequentException e) {
			}
			if (extDataset != null) {
				// if (extDataset.getSequence().length > 3) {
				// mineInThread(extDataset);
				// } else {
				return new MiningStep(extDataset);
				// }
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

	private static void mineInThread(Dataset dataset) {
		final int[] extensions = dataset.getExtensions();
		Arrays.sort(extensions);
		for (int index = 0; index < extensions.length; index++) {
			loopCounts.incrementAndGet();
			final int extension = extensions[index];
			Dataset extDataset = null;
			try {
				extDataset = dataset.expand(extension);
			} catch (EmergingParentException e) {
				return;
			} catch (EmergingExpansionException | DeadEndException | InfrequentException e) {
			}
			if (extDataset != null) {
				mineInThread(extDataset);
			}
		}
	}
}
