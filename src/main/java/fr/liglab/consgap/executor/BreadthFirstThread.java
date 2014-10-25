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

import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;

class BreadthFirstThread extends Thread {
	private MiningStep runningJob;
	private ConcurrentLinkedQueue<MiningStep> toDoJobs;
	private final int id;
	private final List<BreadthFirstThread> threads;
	private final BreadthFirstExecutor executor;

	// private int level = 0;

	public BreadthFirstThread(final int id, BreadthFirstExecutor executor, List<BreadthFirstThread> threads) {
		super("MiningThread" + id);
		this.threads = threads;
		this.id = id;
		this.executor = executor;
		this.toDoJobs = new ConcurrentLinkedQueue<MiningStep>();
	}

	@Override
	public long getId() {
		return this.id;
	}

	@Override
	public void run() {
		ConcurrentLinkedQueue<MiningStep> nextJobs = new ConcurrentLinkedQueue<MiningStep>();
		while (true) {
			while ((this.runningJob = this.toDoJobs.poll()) != null) {
				MiningStep next;
				while ((next = this.runningJob.next()) != null) {
					nextJobs.add(next);
				}
			}
			this.runningJob = null;
			// we're stealing now
			for (BreadthFirstThread t : this.threads) {
				if (t != this) {
					while ((this.runningJob = t.toDoJobs.poll()) != null) {
						MiningStep next;
						while ((next = this.runningJob.next()) != null) {
							nextJobs.add(next);
						}
					}
					this.runningJob = t.runningJob;
					if (this.runningJob != null) {
						MiningStep next;
						while ((next = this.runningJob.next()) != null) {
							nextJobs.add(next);
						}
						this.runningJob = null;
					}
				}
			}
			// now there is nothing left to be done at this step
			synchronized (this.executor) {
				if (this.executor.vote(!nextJobs.isEmpty())) {
					try {
						this.executor.wait();
					} catch (InterruptedException e) {
						e.printStackTrace();
						return;
					}
				}
			}
			if (!this.executor.isRunning()) {
				// System.err.println(this + " exiting");
				return;
			} else {
				ConcurrentLinkedQueue<MiningStep> swap;
				swap = this.toDoJobs;
				this.toDoJobs = nextJobs;
				nextJobs = swap;
				// this.level++;
				synchronized (this.executor) {
					if (this.executor.vote(true)) {
						try {
							this.executor.wait();
						} catch (InterruptedException e) {
							e.printStackTrace();
							return;
						}
					} else {
						// System.err.println("starting level " + this.level);
					}
				}
			}
		}

	}

	public void init(MiningStep initState) {
		this.toDoJobs.add(initState);
	}
}
