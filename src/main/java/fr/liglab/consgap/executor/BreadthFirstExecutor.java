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

import java.util.ArrayList;
import java.util.List;

import fr.liglab.consgap.dataset.Dataset;

public class BreadthFirstExecutor implements MiningExecutor {
	private final int nbThreads;
	private int voteCount;
	private boolean running;

	public BreadthFirstExecutor(int nbThreads) {
		super();
		this.nbThreads = nbThreads;
		this.voteCount = 0;
	}

	@Override
	public void mine(Dataset d) {
		MiningStep initState = new MiningStep(d,true);
		List<BreadthFirstThread> threads = new ArrayList<BreadthFirstThread>(this.nbThreads);
		for (int id = 0; id < this.nbThreads; id++) {
			threads.add(new BreadthFirstThread(id, this, threads));
		}
		this.running = true;
		for (BreadthFirstThread t : threads) {
			t.init(initState);
			t.start();
		}
		for (BreadthFirstThread t : threads) {
			try {
				t.join();
			} catch (InterruptedException e) {
				throw new RuntimeException(e);
			}
		}
	}

	public boolean vote(boolean run) {
		if (this.voteCount == 0) {
			this.running = run;
		} else {
			this.running = this.running || run;
		}
		this.voteCount++;
		if (this.voteCount == this.nbThreads) {
			this.voteCount = 0;
			this.notifyAll();
			return false;
		} else {
			return true;
		}
	}

	protected final boolean isRunning() {
		return running;
	}

}
