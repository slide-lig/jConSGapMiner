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

package fr.liglab.consgap.dataset;

import fr.liglab.consgap.collector.ResultsCollector;

public interface Dataset {

	public static enum ExpandStatus {
		EMERGING_PARENT, EMERGING, INFREQUENT, DEAD_END, BACKSCAN, OK
	}

	public ResultsCollector getResultsCollector();

	public int[] getExtensions();

	public ExpandStatus expand(final int expansionItem, Dataset[] expandedDataset);

	public int[] getSequence();
}