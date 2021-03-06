/*
 * Copyright (C) 2010-2015 Matthias Heizmann (heizmann@informatik.uni-freiburg.de)
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
package de.uni_freiburg.informatik.ultimate.plugins.generator.traceabstraction;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import de.uni_freiburg.informatik.ultimate.automata.AutomataLibraryServices;
import de.uni_freiburg.informatik.ultimate.automata.nestedword.INestedWordAutomaton;
import de.uni_freiburg.informatik.ultimate.automata.nestedword.NestedWordAutomaton;
import de.uni_freiburg.informatik.ultimate.automata.nestedword.VpAlphabet;
import de.uni_freiburg.informatik.ultimate.automata.statefactory.IStateFactory;
import de.uni_freiburg.informatik.ultimate.core.model.services.IUltimateServiceProvider;
import de.uni_freiburg.informatik.ultimate.logic.Term;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.cfg.structure.IIcfg;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.cfg.structure.IIcfgCallTransition;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.cfg.structure.IIcfgReturnTransition;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.cfg.structure.IIcfgTransition;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.cfg.structure.IcfgEdge;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.cfg.structure.IcfgLocation;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.cfg.structure.IcfgLocationIterator;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.smt.managedscript.ManagedScript;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.smt.predicates.IPredicate;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.smt.predicates.SmtFreePredicateFactory;
import de.uni_freiburg.informatik.ultimate.plugins.generator.rcfgbuilder.cfg.Summary;
import de.uni_freiburg.informatik.ultimate.plugins.generator.traceabstraction.predicates.PredicateFactory;

public class CFG2NestedWordAutomaton<LETTER extends IIcfgTransition<?>> {
	private static final boolean DEBUG_STORE_HISTORY = false;

	private CFG2NestedWordAutomaton() {
		// do not instantiate
	}

	/**
	 * Construct the control automata (see Trace Abstraction) for the program of
	 * rootNode. If mInterprocedural==false we construct an automaton for each
	 * procedure otherwise we construct one nested word automaton for the whole
	 * program.
	 *
	 * @param acceptingLocations
	 *            locations for which the corresponding automaton state should be
	 *            accepting
	 *
	 */
	@SuppressWarnings("unchecked")
	public static <LETTER> INestedWordAutomaton<LETTER, IPredicate> constructAutomatonWithSPredicates(
			final IUltimateServiceProvider services, final IIcfg<? extends IcfgLocation> icfg,
			final IStateFactory<IPredicate> automataStateFactory,
			final Collection<? extends IcfgLocation> acceptingLocations, final boolean interprocedural,
			final PredicateFactory predicateFactory) {
		final VpAlphabet<LETTER> vpAlphabet = extractVpAlphabet(icfg, !interprocedural);

		Function<IcfgLocation, IPredicate> predicateProvider;
		final ManagedScript mgdScript = icfg.getCfgSmtToolkit().getManagedScript();
		predicateProvider = constructSPredicateProvider(predicateFactory, mgdScript);
		final Function<IIcfgTransition<?>, LETTER> transitionMapping = constructIdentityTransitionProvider();
		return constructAutomaton(services, icfg, automataStateFactory, acceptingLocations, interprocedural, vpAlphabet,
				predicateProvider, transitionMapping);
	}

	/**
	 * @param newTransition2OldTransition If null then this method uses the identity to map input transitions to
	 * result transitions.
	 */
	public static <LETTER> INestedWordAutomaton<LETTER, IPredicate> constructAutomatonWithDebugPredicates(
			final IUltimateServiceProvider services, final IIcfg<? extends IcfgLocation> icfg,
			final IStateFactory<IPredicate> automataStateFactory,
			final Collection<? extends IcfgLocation> acceptingLocations, final boolean interprocedural,
			final VpAlphabet<LETTER> vpAlphabet, final Map<IIcfgTransition<?>, IIcfgTransition<?>> newTransition2OldTransition) {
		final Function<IcfgLocation, IPredicate> predicateProvider = constructDebugPredicateProvider();
		final Function<IIcfgTransition<?>, LETTER> transitionMapping;
		if (newTransition2OldTransition == null) {
			transitionMapping = constructIdentityTransitionProvider();
		} else {
			transitionMapping = constructMapBasedTransitionProvider(newTransition2OldTransition);
		}
		return constructAutomaton(services, icfg, automataStateFactory, acceptingLocations, interprocedural, vpAlphabet,
				predicateProvider, transitionMapping);
	}

	public static <LETTER> String printIcfg(final IUltimateServiceProvider services,
			final IIcfg<? extends IcfgLocation> icfg) {
		final VpAlphabet<LETTER> vpAlphabet = extractVpAlphabet(icfg, false);
		final INestedWordAutomaton<LETTER, IPredicate> nwa = constructAutomatonWithDebugPredicates(services, icfg,
				new PredicateFactoryResultChecking(new SmtFreePredicateFactory()), Collections.emptySet(), true,
				vpAlphabet, null);
		return nwa.toString();
	}

	private static Function<IcfgLocation, IPredicate> constructSPredicateProvider(
			final PredicateFactory predicateFactory, final ManagedScript mgdScript) {
		Function<IcfgLocation, IPredicate> predicateProvider;
		final Term trueTerm = mgdScript.getScript().term("true");
		if (DEBUG_STORE_HISTORY) {
			predicateProvider = x -> {
				return predicateFactory.newPredicateWithHistory(x, trueTerm, new HashMap<Integer, Term>());
			};
		} else {
			predicateProvider = x -> predicateFactory.newSPredicate(x, trueTerm);
		}
		return predicateProvider;
	}

	private static Function<IcfgLocation, IPredicate> constructDebugPredicateProvider() {
		final SmtFreePredicateFactory pf = new SmtFreePredicateFactory();
		return x -> pf.newDebugPredicate(x.toString());
	}

	private static <LETTER> Function<IIcfgTransition<?>, LETTER> constructIdentityTransitionProvider() {
		return x -> (LETTER) x;
	}

	private static <LETTER> Function<IIcfgTransition<?>, LETTER> constructMapBasedTransitionProvider(
			final Map<IIcfgTransition<?>, IIcfgTransition<?>> mapping) {
		return x -> (LETTER) mapping.get(x);
	}

	private static <LETTER> INestedWordAutomaton<LETTER, IPredicate> constructAutomaton(
			final IUltimateServiceProvider services, final IIcfg<? extends IcfgLocation> icfg,
			final IStateFactory<IPredicate> automataStateFactory,
			final Collection<? extends IcfgLocation> acceptingLocations, final boolean interprocedural,
			final VpAlphabet<LETTER> vpAlphabet, final Function<IcfgLocation, IPredicate> predicateProvider,
			final Function<IIcfgTransition<?>, LETTER> letterProvider) {
		final IcfgLocationIterator<?> iter = new IcfgLocationIterator<>(icfg);
		final Set<IcfgLocation> allNodes = iter.asStream().collect(Collectors.toSet());
		final Set<? extends IcfgLocation> initialNodes = icfg.getInitialNodes();

		// construct the automaton
		final NestedWordAutomaton<LETTER, IPredicate> nwa = new NestedWordAutomaton<>(
				new AutomataLibraryServices(services), vpAlphabet, automataStateFactory);
		final Map<IcfgLocation, IPredicate> nodes2States = new HashMap<>();

		{
			// add states
			for (final IcfgLocation locNode : allNodes) {
				final boolean isInitial = initialNodes.contains(locNode);
				final boolean isAccepting = acceptingLocations.contains(locNode);
				final IPredicate automatonState = predicateProvider.apply(locNode);
				nwa.addState(isInitial, isAccepting, automatonState);
				nodes2States.put(locNode, automatonState);
			}
		}

		// add transitions
		for (final IcfgLocation locNode : allNodes) {
			final IPredicate state = nodes2States.get(locNode);
			if (locNode.getOutgoingNodes() != null) {
				for (final IcfgEdge edge : locNode.getOutgoingEdges()) {
					final IcfgLocation succLoc = edge.getTarget();
					final IPredicate succState = nodes2States.get(succLoc);
					if (edge instanceof IIcfgCallTransition<?>) {
						if (interprocedural) {
							nwa.addCallTransition(state, letterProvider.apply(edge), succState);
						}
					} else if (edge instanceof IIcfgReturnTransition<?, ?>) {
						if (interprocedural) {
							final IIcfgReturnTransition<?, ?> returnEdge = (IIcfgReturnTransition<?, ?>) edge;
							final IcfgLocation callerLocNode = returnEdge.getCallerProgramPoint();
							if (nodes2States.containsKey(callerLocNode)) {
								nwa.addReturnTransition(state, nodes2States.get(callerLocNode),
										letterProvider.apply(returnEdge), succState);
							} else {
								throw new AssertionError(
										"Cannot add " + returnEdge + ", missing callerNode " + callerLocNode);
							}
						}
					} else if (edge instanceof Summary) {
						final Summary summaryEdge = (Summary) edge;
						if (summaryEdge.calledProcedureHasImplementation()) {
							if (!interprocedural) {
								nwa.addInternalTransition(state, letterProvider.apply(summaryEdge), succState);
							}
						} else {
							nwa.addInternalTransition(state, letterProvider.apply(summaryEdge), succState);
						}
					} else {
						nwa.addInternalTransition(state, letterProvider.apply(edge), succState);
					}
				}
			}
		}
		return nwa;
	}

	/**
	 * Extract from an ICFG the alphabet that is needed for an trace
	 * abstraction-based analysis.
	 *
	 * @param icfg
	 * @param intraproceduralAnalysis
	 *            In an intraprocedural analysis we ignore call and return
	 *            statements. Instead we add summary edges between the call
	 *            predecessor and the return successor. If a specification of the
	 *            procedure is given, this specification is used here. If no
	 *            specification is given we use the trivial ("true") specification.
	 * @return
	 */
	public static <LETTER> VpAlphabet<LETTER> extractVpAlphabet(final IIcfg<? extends IcfgLocation> icfg,
			final boolean intraproceduralAnalysis) {
		final Set<LETTER> internalAlphabet = new HashSet<>();
		final Set<LETTER> callAlphabet = new HashSet<>();
		final Set<LETTER> returnAlphabet = new HashSet<>();

		final IcfgLocationIterator<?> iter = new IcfgLocationIterator<>(icfg);

		while (iter.hasNext()) {
			final IcfgLocation locNode = iter.next();
			if (locNode.getOutgoingNodes() != null) {
				for (final IcfgEdge edge : locNode.getOutgoingEdges()) {
					if (edge instanceof IIcfgCallTransition) {
						if (!intraproceduralAnalysis) {
							callAlphabet.add((LETTER) edge);
						}
					} else if (edge instanceof IIcfgReturnTransition) {
						if (!intraproceduralAnalysis) {
							returnAlphabet.add((LETTER) edge);
						}
					} else if (edge instanceof Summary) {
						final Summary summary = (Summary) edge;
						if (summary.calledProcedureHasImplementation()) {
							// do nothing if analysis is interprocedural
							// add summary otherwise
							if (intraproceduralAnalysis) {
								internalAlphabet.add((LETTER) summary);
							}
						} else {
							internalAlphabet.add((LETTER) summary);
						}
					} else {
						internalAlphabet.add((LETTER) edge);
					}
				}
			}
		}
		return new VpAlphabet<>(internalAlphabet, callAlphabet, returnAlphabet);
	}
}
