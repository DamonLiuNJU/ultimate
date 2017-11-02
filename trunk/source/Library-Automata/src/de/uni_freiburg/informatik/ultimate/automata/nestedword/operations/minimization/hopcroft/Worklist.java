/*
 * Copyright (C) 2017 Christian Schilling (schillic@informatik.uni-freiburg.de)
 * Copyright (C) 2017 University of Freiburg
 *
 * This file is part of the ULTIMATE Automata Library.
 *
 * The ULTIMATE Automata Library is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * The ULTIMATE Automata Library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with the ULTIMATE Automata Library. If not, see <http://www.gnu.org/licenses/>.
 *
 * Additional permission under GNU GPL version 3 section 7:
 * If you modify the ULTIMATE Automata Library, or any covered work, by linking
 * or combining it with Eclipse RCP (or a modified version of Eclipse RCP),
 * containing parts covered by the terms of the Eclipse Public License, the
 * licensors of the ULTIMATE Automata Library grant you additional permission
 * to convey the resulting work.
 */
package de.uni_freiburg.informatik.ultimate.automata.nestedword.operations.minimization.hopcroft;

import java.util.Collection;
import java.util.Comparator;
import java.util.PriorityQueue;

public class Worklist<STATE> {
	private final PriorityQueue<Partition<STATE>.Block> mQueue;

	public Worklist(final int initialCapacity) {
		mQueue = new PriorityQueue<>(initialCapacity,new BlockComparator());
	}

	public boolean isEmpty() {
		return mQueue.isEmpty();
	}

	public void add(final Partition<STATE>.Block block) {
		assert !mQueue.contains(block);
		mQueue.add(block);
	}

	public Collection<STATE> poll() {
		return mQueue.poll();
	}

	@Override
	public String toString() {
		final StringBuilder builder = new StringBuilder();
		builder.append("<<");
		String append = "";
		for (final Partition<STATE>.Block block : mQueue) {
			builder.append(append);
			append = ", ";
			builder.append(block);
		}
		builder.append(">>");
		return builder.toString();
	}

	private class BlockComparator implements Comparator<Partition<STATE>.Block> {
		@Override
		public int compare(final Partition<STATE>.Block b1, final Partition<STATE>.Block b2) {
			return b1.size() - b2.size();
		}
	}
}
