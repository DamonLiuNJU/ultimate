/*
 * Copyright (C) 2017 Alexander Nutz (nutz@informatik.uni-freiburg.de)
 * Copyright (C) 2017 University of Freiburg
 *
 * This file is part of the ULTIMATE Util Library.
 *
 * The ULTIMATE Util Library is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * The ULTIMATE Util Library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with the ULTIMATE Util Library. If not, see <http://www.gnu.org/licenses/>.
 *
 * Additional permission under GNU GPL version 3 section 7:
 * If you modify the ULTIMATE Util Library, or any covered work, by linking
 * or combining it with Eclipse RCP (or a modified version of Eclipse RCP),
 * containing parts covered by the terms of the Eclipse Public License, the
 * licensors of the ULTIMATE Util Library grant you additional permission
 * to convey the resulting work.
 */
package de.uni_freiburg.informatik.ultimate.util.datastructures.poset;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.HashSet;
import java.util.Map.Entry;
import java.util.Set;

import de.uni_freiburg.informatik.ultimate.util.datastructures.UnionFind;
import de.uni_freiburg.informatik.ultimate.util.datastructures.poset.IPartialComparator.ComparisonResult;
import de.uni_freiburg.informatik.ultimate.util.datastructures.relation.HashRelation;
import de.uni_freiburg.informatik.ultimate.util.statistics.BenchmarkWithCounters;

public class PartialOrderCache<E> {

	private final IPartialComparator<E> mComparator;

	private final HashRelation<E, E> mStrictlySmaller;

	private final HashRelation<E, E> mNotStrictlySmaller;

	private final UnionFind<E> mEquivalences;

	private final Set<E> mMaximalElements;

	private final boolean mBenchmarkMode;
	private BenchmarkWithCounters mBenchmark;

	public PartialOrderCache(final IPartialComparator<E> comparator) {
		mComparator = comparator;
		mStrictlySmaller = new HashRelation<>();
		mNotStrictlySmaller = new HashRelation<>();
		mEquivalences = new UnionFind<>();
		mMaximalElements = new HashSet<>();

		mBenchmarkMode = true;
		if (mBenchmarkMode) {
			mBenchmark = new BenchmarkWithCounters();
			mBenchmark.registerCountersAndWatches(PocBmNames.getNames());
		} else {
			mBenchmark = null;
		}

	}

	public E addElement(final E elemToAdd) {
		bmStart(PocBmNames.ELEMENTS_ADDED);
		E rep = mEquivalences.find(elemToAdd);
		if (rep != null) {
			// we already know this element, return the representative
			bmEnd(PocBmNames.ELEMENTS_ADDED);
			return rep;
		}
		// elemToAdd element is new
		rep = mEquivalences.findAndConstructEquivalenceClassIfNeeded(elemToAdd);
		assert rep == elemToAdd;
		mMaximalElements.add(rep);

		for (final E otherRep : new ArrayList<>(mEquivalences.getAllRepresentatives())) {
			if (otherRep == rep) {
				// nothing to do
				continue;
			}
			bmStart(PocBmNames.ORDER_REQUESTS_MADE);
			final ComparisonResult comparisonResult = mComparator.compare(elemToAdd, otherRep);
			bmEnd(PocBmNames.ORDER_REQUESTS_MADE);
			switch (comparisonResult) {
			case EQUAL:
				mEquivalences.union(rep, otherRep);
				final E newRep = mEquivalences.find(rep);
				if (newRep == rep) {
					// representative has changed
					assert mEquivalences.find(otherRep) == rep;
					mMaximalElements.remove(otherRep);
					mStrictlySmaller.replaceDomainElement(otherRep, newRep);
					mStrictlySmaller.replaceRangeElement(otherRep, newRep);
				} else {
					// representative is the old one but we have already made some entries into the data structures
					mMaximalElements.remove(rep);
					mStrictlySmaller.replaceDomainElement(rep, newRep);
					mStrictlySmaller.replaceRangeElement(rep, newRep);
				}
				assert sanityCheck();
				bmEnd(PocBmNames.ELEMENTS_ADDED);
				return newRep;
			case STRICTLY_SMALLER:
				addStrictlySmaller(elemToAdd, otherRep);
				break;
			case STRICTLY_GREATER:
				addStrictlySmaller(otherRep, elemToAdd);
				break;
			case INCOMPARABLE:
				mNotStrictlySmaller.addPair(elemToAdd, otherRep);
				mNotStrictlySmaller.addPair(otherRep, elemToAdd);
				break;
			}
		}

		bmEnd(PocBmNames.ELEMENTS_ADDED);
		assert sanityCheck();
		return rep;
	}

	private void addStrictlySmaller(final E smaller, final E greater) {
		assert mEquivalences.find(smaller) == smaller;
		assert mEquivalences.find(greater) == greater;

		final E smallerRep = mEquivalences.find(smaller);
		final E greaterRep = mEquivalences.find(greater);

		mStrictlySmaller.addPair(smallerRep, greaterRep);
		mNotStrictlySmaller.addPair(greaterRep, smallerRep);
		assert assertStrictlySmaller(smallerRep, greaterRep);

		mMaximalElements.remove(smallerRep);

		assert sanityCheck();
	}

	public boolean lowerEqual(final E elem1, final E elem2) {
		bmStart(PocBmNames.ORDER_REQUESTS_ANSWERED);
		if (elem1 == elem2) {
			return true;
		}
		assert sanityCheck();
		final E rep1 = addElement(elem1);
		final E rep2 = addElement(elem2);
		if (rep1 == rep2) {
			// elements are equal
			bmEnd(PocBmNames.ORDER_REQUESTS_ANSWERED);
			return true;
		}
		/*
		 * elements are not equal
		 * We need to test if there is a path through the graph mStrictlySmaller from rep1 to rep2.
		 */
		final boolean result = strictlySmaller(rep1, rep2);
		bmEnd(PocBmNames.ORDER_REQUESTS_ANSWERED);
		return result;
	}

	protected boolean strictlySmaller(final E rep1, final E rep2) {
		if (mStrictlySmaller.containsPair(rep1, rep2)) {
			return true;
		}
		if (mNotStrictlySmaller.containsPair(rep1, rep2)) {
			return false;
		}
		final Deque<E> worklist = new ArrayDeque<>();
		worklist.add(rep1);
		while (!worklist.isEmpty()) {
			final E current = worklist.pop();

			if (current == rep2 && current != rep1) {
				/*
				 * found a path
				 * update the map (caching the transitive closure information)
				 * return true
				 */
				mStrictlySmaller.addPair(rep1, rep2);
				assert assertStrictlySmaller(rep1, rep2);
				assert sanityCheck();
				return true;
			}
			worklist.addAll(mStrictlySmaller.getImage(current));
		}
		// found no path --> not smaller
		mNotStrictlySmaller.addPair(rep1, rep2);
		return false;
	}

//	public boolean greaterEqual(final E elem1, final E elem2) {
//		throw new UnsupportedOperationException("not yet implemented");
//	}

	/**
	 * Get the maximal elements from to the given list (or elements equivalent to those)
	 *
	 * @param elements
	 * @return
	 */
	public Set<E> getMaximalRepresentatives(final Collection<E> elements) {
		final Set<E> reps = new HashSet<>();
		for (final E el : elements) {
			reps.add(addElement(el));
		}

		final Set<E> result = new HashSet<>(reps);

		for (final E rep1 : reps) {
			for (final E rep2 : reps) {
				if (strictlySmaller(rep1, rep2)) {
					result.remove(rep1);
				}
			}
		}

		return result;
	}

	/**
	 * Get overall maximal elements in the map (modulo being equal/only representatives)
	 *
	 * @return
	 */
	public Set<E> getMaximalRepresentatives() {
		return mMaximalElements;
	}

	boolean assertStrictlySmaller(final E elem1, final E elem2) {
		// order must be correct
		if (mComparator.compare(elem1, elem2) != ComparisonResult.STRICTLY_SMALLER) {
			final ComparisonResult compres = mComparator.compare(elem1, elem2);
			assert false;
			return false;
		}
		return true;
	}

	boolean sanityCheck() {
		/*
		 * the sets mMinimalElemnts and mMaximalElements may only contain representatives
		 */
		for (final E e : mMaximalElements) {
			if (mEquivalences.find(e) != e) {
				final E find = mEquivalences.find(e);
				assert false;
				return false;
			}
		}

		for (final Entry<E, E> en : mStrictlySmaller) {
			// key must be a representative
			if (mEquivalences.find(en.getKey()) != en.getKey()) {
				final E find = mEquivalences.find(en.getKey());
				assert false;
				return false;
			}
			// value must be a representative
			if (mEquivalences.find(en.getValue()) != en.getValue()) {
				final E find = mEquivalences.find(en.getValue());
				assert false;
				return false;
			}

		}
		return true;
	}

	public boolean knowsElement(final E elem) {
		return mEquivalences.find(elem) != null;
	}

	private static enum PocBmNames {

		ORDER_REQUESTS_MADE, ORDER_REQUESTS_ANSWERED, ELEMENTS_ADDED;

		static String[] getNames() {
			final String[] result = new String[values().length];
			for (int i = 0; i < values().length; i++) {
				result[i] = values()[i].name();
			}
			return result;
		}
	}

	public boolean hasBenchmark() {
		return mBenchmarkMode;
	}

	public BenchmarkWithCounters getBenchmark() {
		if (!hasBenchmark()) {
			throw new IllegalStateException();
		}
		return mBenchmark;
	}

	private void bmStart(final PocBmNames watch) {
		if (!mBenchmarkMode) {
			return;
		}
//		bmStartOverall();
		mBenchmark.incrementCounter(watch.name());
		mBenchmark.unpauseWatch(watch.name());
	}

	private void bmEnd(final PocBmNames watch) {
		if (!mBenchmarkMode) {
			return;
		}
//		bmEndOverall();
		mBenchmark.pauseWatch(watch.name());
	}

}
