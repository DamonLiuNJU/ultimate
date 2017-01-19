/*
 * Copyright (C) 2015 Dirk Steinmetz
 * Copyright (C) 2015 University of Freiburg
 *
 * This file is part of the ULTIMATE TraceAbstraction plug-in.
 *
 * The ULTIMATE TraceAbstraction plug-in is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * The ULTIMATE TraceAbstraction plug-in is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with the ULTIMATE TraceAbstraction plug-in. If not, see <http://www.gnu.org/licenses/>.
 *
 * Additional permission under GNU GPL version 3 section 7:
 * If you modify the ULTIMATE TraceAbstraction plug-in, or any covered work, by linking
 * or combining it with Eclipse RCP (or a modified version of Eclipse RCP),
 * containing parts covered by the terms of the Eclipse Public License, the
 * licensors of the ULTIMATE TraceAbstraction plug-in grant you additional permission
 * to convey the resulting work.
 */
package de.uni_freiburg.informatik.ultimate.plugins.generator.traceabstraction.pathinvariants.internal;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;

import de.uni_freiburg.informatik.ultimate.logic.Script;
import de.uni_freiburg.informatik.ultimate.logic.Sort;
import de.uni_freiburg.informatik.ultimate.logic.Term;
import de.uni_freiburg.informatik.ultimate.logic.TermTransformer;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.boogie.Boogie2SMT;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.cfg.CfgSmtToolkit;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.smt.Substitution;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.smt.predicates.IPredicateUnifier;

/**
 * Abstract base for {@link IInvariantPatternProcessor}s using SMT.
 *
 * @param <IPT>
 *            Invariant Pattern Type: Type used for invariant patterns
 */
public abstract class AbstractSMTInvariantPatternProcessor<IPT> implements IInvariantPatternProcessor<IPT> {

	protected final IPredicateUnifier mPedicateUnifier;
	protected final CfgSmtToolkit mCsToolkit;

	/**
	 * Creates a new pattern processor based on SMT.
	 *
	 * @param predicateUnifier
	 *            predicate unifier to unify predicates with
	 * @param csToolkit
	 *            SMT manager to access {@link Boogie2SMT} through
	 */
	public AbstractSMTInvariantPatternProcessor(final IPredicateUnifier predicateUnifier,
			final CfgSmtToolkit csToolkit) {
		mPedicateUnifier = predicateUnifier;
		mCsToolkit = csToolkit;
	}

	/**
	 * Converts an invariant pattern type to a {@link Term} containing both program variables ({@link TermVariable}s)
	 * and pattern variables ( for example {@link ApplicationTerm}s on new function symbols).
	 *
	 * Names for pattern variables should be prefixed with an implementation-specific string and an underscore character
	 * to assert global uniqueness. See implementation for details on more specific naming conventions.
	 *
	 * See {@link #getConfigurationTransformer()} on how pattern variables are handled.
	 *
	 * @param pattern
	 *            pattern to convert
	 * @return converted term
	 */
	// protected abstract Term getTermForPattern(IPT pattern);

	/**
	 * Provides access to a {@link TermTransformer} replacing pattern variables within a term according to the current
	 * valid configuration ( {@link #hasValidConfiguration(java.util.Collection, int)}).
	 *
	 * If there is no current valid configuration, the behavior of this method is undefined.
	 *
	 * The transformer must be able to transform any pattern-term generated by {@link #getTermForPattern(Object)} into a
	 * {@link Term} representing the program states covered by the pattern under the given configuration.
	 *
	 * @see Substitution
	 * @return transformer replacing pattern variables
	 *
	 */
	protected abstract TermTransformer getConfigurationTransformer();

	/**
	 * {@inheritDoc}
	 */
	// @Override
	// public IPredicate applyConfiguration(IPT pattern) {
	// final TermTransformer transformer = getConfigurationTransformer();
	// final Term term = transformer.transform(getTermForPattern(pattern));
	// return predicateUnifier.getOrConstructPredicate(TermVarsProc
	// .computeTermVarsProc(term, csToolkit.getBoogie2Smt()));
	// }

	protected static Term constructZero(final Script script, final Sort sort) {
		if (sort.getRealSort().getName().equals("Int")) {
			return script.numeral(BigInteger.ZERO);
		} else if (sort.getRealSort().getName().equals("Real")) {
			return script.decimal(BigDecimal.ZERO);
		} else {
			throw new IllegalArgumentException("unsupported sort " + sort);
		}
	}

	/**
	 * Given two disjunctions a and b of conjunctions, this method calculates a new disjunction of conjunctions
	 * equivalent to a /\ b
	 *
	 * @param a
	 *            the first dnf
	 * @param b
	 *            the second dnf
	 * @param <E>
	 *            the type of the literals in the dnf
	 * @return a new dnf equivalent to a /\ b
	 */
	protected static <E> Collection<Collection<E>> expandConjunctionSingle(final Collection<Collection<E>> a,
			final Collection<Collection<E>> b) {
		final Collection<Collection<E>> result = new ArrayList<>();
		for (final Collection<E> aItem : a) {
			for (final Collection<E> bItem : b) {
				final Collection<E> resultItem = new ArrayList<>();
				resultItem.addAll(aItem);
				resultItem.addAll(bItem);
				result.add(resultItem);
			}
		}
		return result;
	}

	/**
	 * Calculates a DNF of the conjunction of an arbitrary set of DNFs.
	 *
	 * @param <E>
	 *            the type of the literals in the dnfs
	 *
	 * @param dnfs
	 *            DNFs to conjunct together
	 * @return DNF representing the conjunction of the DNFs provided, returns NULL if no DNFs were given.
	 */
	protected static <E> Collection<Collection<E>> expandConjunction(final Collection<Collection<E>>... dnfs) {
		boolean firstElement = true;
		Collection<Collection<E>> expandedDnf = null;
		for (final Collection<Collection<E>> currentDnf : dnfs) {
			if (firstElement) {
				expandedDnf = currentDnf;
				firstElement = false;
			} else {
				expandedDnf = expandConjunctionSingle(currentDnf, expandedDnf);
			}
		}
		return expandedDnf;
	}

	/**
	 * Transforms a cnf (given as two nested Collections of atoms (usually linear inequalites)) into dnf (given as two
	 * nested Collections of atoms (usually linear inequalites)).
	 *
	 * @param <E>
	 *            type of the atoms
	 *
	 * @param cnf
	 *            the collection of conjuncts consisting of disjuncts of linear inequalities
	 * @return a dnf (Collection of disjuncts consisting of conjuncts of linear inequalities), equivalent to the given
	 *         cnf
	 */
	protected static <E> Collection<Collection<E>> expandCnfToDnf(final Collection<Collection<E>> cnf) {
		if (cnf.isEmpty()) {
			final Collection<E> empty = Collections.emptyList();
			return Collections.singleton(empty);
		}
		boolean firstElement = true;
		Collection<Collection<E>> expandedDnf = null;
		for (final Collection<E> conjunct : cnf) {
			if (firstElement) {
				expandedDnf = new ArrayList<>();
				for (final E e : conjunct) {
					final Collection<E> conjunctSingleton = new ArrayList<>();
					conjunctSingleton.add(e);
					expandedDnf.add(conjunctSingleton);
				}
				firstElement = false;
			} else {
				expandedDnf = expandCnfToDnfSingle(expandedDnf, conjunct);
			}
		}
		assert expandedDnf != null;
		return expandedDnf;
	}

	/**
	 * Performs a single expandation, meaning transforming conjunct /\ dnf to an equivalent dnf
	 *
	 * @param dnf
	 *            the dnf the conjunct is conjuncted to
	 * @param conjunct
	 *            the conjunct that is conjuncted to the dnf
	 * @param <E>
	 *            : the type of Literals in the cnf/dnf
	 * @return a new dnf equivalent to conjunct /\ dnf
	 */
	private static <E> Collection<Collection<E>> expandCnfToDnfSingle(final Collection<Collection<E>> dnf,
			final Collection<E> conjunct) {
		final Collection<Collection<E>> result = new ArrayList<>();
		for (final Collection<E> disjunct : dnf) {
			for (final E item : conjunct) {
				final Collection<E> resultItem = new ArrayList<>();
				resultItem.addAll(disjunct);
				resultItem.add(item);
				result.add(resultItem);
			}
		}
		return result;

	}
}
