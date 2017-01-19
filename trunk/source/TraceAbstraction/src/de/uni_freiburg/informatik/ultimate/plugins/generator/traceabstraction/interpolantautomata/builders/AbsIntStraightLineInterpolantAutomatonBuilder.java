/*
 * Copyright (C) 2016 Daniel Dietsch (dietsch@informatik.uni-freiburg.de)
 * Copyright (C) 2016 Marius Greitschus (greitsch@informatik.uni-freiburg.de)
 * Copyright (C) 2016 University of Freiburg
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
package de.uni_freiburg.informatik.ultimate.plugins.generator.traceabstraction.interpolantautomata.builders;

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.stream.Collectors;

import de.uni_freiburg.informatik.ultimate.automata.AutomataLibraryServices;
import de.uni_freiburg.informatik.ultimate.automata.IRun;
import de.uni_freiburg.informatik.ultimate.automata.Word;
import de.uni_freiburg.informatik.ultimate.automata.nestedword.INestedWordAutomatonSimple;
import de.uni_freiburg.informatik.ultimate.automata.nestedword.NestedRun;
import de.uni_freiburg.informatik.ultimate.automata.nestedword.NestedWordAutomaton;
import de.uni_freiburg.informatik.ultimate.core.model.services.ILogger;
import de.uni_freiburg.informatik.ultimate.core.model.services.IUltimateServiceProvider;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.absint.IAbstractState;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.boogie.IBoogieVar;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.cfg.CfgSmtToolkit;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.cfg.IIcfgSymbolTable;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.cfg.structure.ICallAction;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.cfg.structure.IIcfgReturnTransition;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.cfg.structure.IIcfgTransition;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.cfg.structure.IReturnAction;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.smt.SmtUtils;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.smt.SmtUtils.SimplificationTechnique;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.smt.SmtUtils.XnfConversionTechnique;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.smt.predicates.IPredicate;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.smt.predicates.IPredicateUnifier;
import de.uni_freiburg.informatik.ultimate.plugins.analysis.abstractinterpretationv2.algorithm.rcfg.RcfgDebugHelper;
import de.uni_freiburg.informatik.ultimate.plugins.analysis.abstractinterpretationv2.tool.IAbstractInterpretationResult;
import de.uni_freiburg.informatik.ultimate.plugins.generator.rcfgbuilder.cfg.Call;
import de.uni_freiburg.informatik.ultimate.plugins.generator.rcfgbuilder.cfg.Return;
import de.uni_freiburg.informatik.ultimate.plugins.generator.traceabstraction.Activator;
import de.uni_freiburg.informatik.ultimate.util.datastructures.relation.Triple;

/**
 * Builder for counter-example-based abstract interpretation interpolant automata. This builder constructs a straight
 * line interpolant automaton from the counter-example and adds the predicates generated by abstract interpretation to
 * the automaton. No further predicates and/or transitions are inferred.
 *
 * @author Daniel Dietsch (dietsch@informatik.uni-freiburg.de)
 * @author Marius Greitschus (greitsch@informatik.uni-freiburg.de)
 */
public class AbsIntStraightLineInterpolantAutomatonBuilder<LETTER extends IIcfgTransition<?>>
		implements IInterpolantAutomatonBuilder<LETTER, IPredicate> {

	private static final long PRINT_PREDS_LIMIT = 30;

	private final IUltimateServiceProvider mServices;
	private final ILogger mLogger;
	private final NestedWordAutomaton<LETTER, IPredicate> mResult;
	private final CfgSmtToolkit mCsToolkit;
	private final IRun<LETTER, IPredicate, ?> mCurrentCounterExample;
	private final IIcfgSymbolTable mSymbolTable;

	public AbsIntStraightLineInterpolantAutomatonBuilder(final IUltimateServiceProvider services,
			final INestedWordAutomatonSimple<LETTER, IPredicate> oldAbstraction,
			final IAbstractInterpretationResult<?, LETTER, IBoogieVar, ?> aiResult, final IPredicateUnifier predUnifier,
			final CfgSmtToolkit csToolkit, final IRun<LETTER, IPredicate, ?> currentCounterExample,
			final SimplificationTechnique simplificationTechnique, final XnfConversionTechnique xnfConversionTechnique,
			final IIcfgSymbolTable symbolTable) {
		mServices = services;
		mLogger = services.getLoggingService().getLogger(Activator.PLUGIN_ID);
		mCsToolkit = csToolkit;
		mSymbolTable = symbolTable;
		mCurrentCounterExample = currentCounterExample;
		mResult = constructAutomaton(oldAbstraction, aiResult, predUnifier);
	}

	@Override
	public NestedWordAutomaton<LETTER, IPredicate> getResult() {
		return mResult;
	}

	private <STATE extends IAbstractState<STATE, IBoogieVar>> NestedWordAutomaton<LETTER, IPredicate>
			constructAutomaton(final INestedWordAutomatonSimple<LETTER, IPredicate> oldAbstraction,
					final IAbstractInterpretationResult<STATE, LETTER, IBoogieVar, ?> aiResult,
					final IPredicateUnifier predicateUnifier) {

		final RcfgDebugHelper<STATE, LETTER, IBoogieVar, ?> debugHelper =
				new RcfgDebugHelper<>(mCsToolkit, mServices, mSymbolTable);
		mLogger.info("Creating interpolant automaton from AI predicates (straight)");

		final NestedWordAutomaton<LETTER, IPredicate> result = new NestedWordAutomaton<>(
				new AutomataLibraryServices(mServices), oldAbstraction.getInternalAlphabet(),
				oldAbstraction.getCallAlphabet(), oldAbstraction.getReturnAlphabet(), oldAbstraction.getStateFactory());

		final NestedRun<LETTER, IPredicate> cex = (NestedRun<LETTER, IPredicate>) mCurrentCounterExample;
		final Word<LETTER> word = cex.getWord();

		final int wordlength = word.length();
		assert wordlength > 1 : "Unexpected: length of word smaller or equal to 1.";

		final TripleStack<STATE> callStack = new TripleStack<>();

		final IPredicate falsePredicate = predicateUnifier.getFalsePredicate();
		final Set<IPredicate> alreadyThereAsState = new HashSet<>();

		Set<STATE> previousStates = Collections.emptySet();
		IPredicate previous = predicateUnifier.getTruePredicate();
		alreadyThereAsState.add(previous);
		result.addState(true, false, previous);

		for (int i = 0; i < wordlength; i++) {
			final LETTER symbol = word.getSymbol(i);

			if (mLogger.isDebugEnabled()) {
				mLogger.debug("CallStack Before" + callStack.getCalls().stream()
						.map(a -> '[' + String.valueOf(a.hashCode()) + ']').reduce((a, b) -> a + ',' + b).orElse(""));
			}
			final Set<STATE> postStates;
			final Triple<LETTER, IPredicate, Set<STATE>> hierarchicalPreState;

			if (symbol instanceof ICallAction) {
				hierarchicalPreState = getHierachicalPreState(symbol, previous, previousStates, callStack);
				postStates = aiResult.getPostStates(callStack.getCalls(), symbol, previousStates);
			} else if (symbol instanceof IReturnAction) {
				hierarchicalPreState = getHierachicalPreState(symbol, previous, previousStates, callStack);
				postStates = aiResult.getPostStates(callStack.getCalls(), symbol, previousStates);
			} else {
				postStates = aiResult.getPostStates(callStack.getCalls(), symbol, previousStates);
				hierarchicalPreState = getHierachicalPreState(symbol, previous, previousStates, callStack);
			}

			if (mLogger.isDebugEnabled()) {
				mLogger.debug("CallStack After" + callStack.getCalls().stream()
						.map(a -> '[' + String.valueOf(a.hashCode()) + ']').reduce((a, b) -> a + ',' + b).orElse(""));
			}

			final IPredicate target;
			if (postStates.isEmpty()) {
				target = falsePredicate;
			} else {
				target = predicateUnifier.getOrConstructPredicateForDisjunction(
						postStates.stream().map(s -> s.getTerm(mCsToolkit.getManagedScript().getScript()))
								.map(predicateUnifier::getOrConstructPredicate).collect(Collectors.toSet()));
			}

			if (alreadyThereAsState.add(target)) {
				result.addState(false, falsePredicate.equals(target), target);
			}

			// Add transition
			assert isSound(previousStates, hierarchicalPreState, symbol, postStates,
					debugHelper) : "About to insert unsound transition";
			if (symbol instanceof Call) {
				result.addCallTransition(previous, symbol, target);
			} else if (symbol instanceof Return) {
				result.addReturnTransition(previous, hierarchicalPreState.getSecond(), symbol, target);
			} else {
				result.addInternalTransition(previous, symbol, target);
			}

			if (mLogger.isDebugEnabled()) {
				writeTransitionAddLog(i, symbol, postStates, previous,
						hierarchicalPreState == null ? null : hierarchicalPreState.getSecond(), target);
			}

			previousStates = postStates;
			previous = target;
		}

		// Add self-loops to final states
		addSelfLoops(oldAbstraction, result, callStack);

		if (PRINT_PREDS_LIMIT < alreadyThereAsState.size()) {
			mLogger.info("Using "
					+ alreadyThereAsState.size() + " predicates from AI: " + String.join(",", alreadyThereAsState
							.stream().limit(PRINT_PREDS_LIMIT).map(a -> a.toString()).collect(Collectors.toList()))
					+ "...");
		} else {
			mLogger.info("Using " + alreadyThereAsState.size() + " predicates from AI: " + String.join(",",
					alreadyThereAsState.stream().map(a -> a.toString()).collect(Collectors.toList())));
		}

		return result;
	}

	private <STATE extends IAbstractState<STATE, IBoogieVar>> void addSelfLoops(
			final INestedWordAutomatonSimple<LETTER, IPredicate> oldAbstraction,
			final NestedWordAutomaton<LETTER, IPredicate> result, final TripleStack<STATE> callStack) {
		if (!result.getFinalStates().isEmpty()) {
			for (final IPredicate finalState : result.getFinalStates()) {
				oldAbstraction.getInternalAlphabet()
						.forEach(l -> result.addInternalTransition(finalState, l, finalState));
				oldAbstraction.getCallAlphabet().forEach(l -> result.addCallTransition(finalState, l, finalState));
				for (final LETTER returnSymbol : oldAbstraction.getReturnAlphabet()) {
					final IIcfgReturnTransition<?, ?> ret = (IIcfgReturnTransition<?, ?>) returnSymbol;
					result.addReturnTransition(finalState, finalState, returnSymbol, finalState);
					for (final Triple<LETTER, IPredicate, Set<STATE>> openCall : callStack) {
						if (ret.getCorrespondingCall().equals(openCall.getFirst())) {
							result.addReturnTransition(finalState, openCall.getSecond(), returnSymbol, finalState);
						}
					}
				}
			}
		}
	}

	private <STATE extends IAbstractState<STATE, IBoogieVar>> boolean isSound(final Set<STATE> previousStates,
			final Triple<LETTER, IPredicate, Set<STATE>> hierarchicalPreState, final LETTER symbol,
			final Set<STATE> postStates, final RcfgDebugHelper<STATE, LETTER, IBoogieVar, ?> debugHelper) {
		if (hierarchicalPreState == null) {
			return debugHelper.isPostSound(previousStates, null, postStates, symbol);
		}
		return debugHelper.isPostSound(previousStates, hierarchicalPreState.getThird(), postStates, symbol);
	}

	private <STATE extends IAbstractState<STATE, IBoogieVar>> Triple<LETTER, IPredicate, Set<STATE>>
			getHierachicalPreState(final LETTER symbol, final IPredicate previous, final Set<STATE> previousStates,
					final TripleStack<STATE> callStack) {
		final Triple<LETTER, IPredicate, Set<STATE>> hierarchicalPreState;
		if (symbol instanceof ICallAction) {
			hierarchicalPreState = new Triple<>(symbol, previous, previousStates);
			callStack.addFirst(hierarchicalPreState);
		} else if (symbol instanceof IReturnAction) {
			assert !callStack.isEmpty() : "Return does not have a corresponding call.";
			hierarchicalPreState = callStack.removeFirst();
		} else {
			hierarchicalPreState = null;
		}
		return hierarchicalPreState;
	}

	private <STATE extends IAbstractState<STATE, IBoogieVar>> void writeTransitionAddLog(final int i,
			final LETTER symbol, final Set<STATE> nextStates, final IPredicate source,
			final IPredicate hierarchicalPreState, final IPredicate target) {
		final String divider = "------------------------------------------------";
		if (i == 0) {
			mLogger.debug(divider);
		}
		mLogger.debug("Transition: " + symbol);
		if (nextStates == null) {
			mLogger.debug("Post States: NONE");
		} else {
			mLogger.debug("Post States:");
			for (final STATE nextState : nextStates) {
				mLogger.debug("  " + nextState);
			}
		}

		mLogger.debug("Pre: " + source);
		if (hierarchicalPreState != null) {
			mLogger.debug("HierPre: " + hierarchicalPreState);
		}
		mLogger.debug("Post: " + target);
		mLogger.debug("Post (S): " + SmtUtils.simplify(mCsToolkit.getManagedScript(), target.getFormula(), mServices,
				SimplificationTechnique.SIMPLIFY_DDA));
		mLogger.debug(divider);
	}

	private final class TripleStack<STATE extends IAbstractState<STATE, IBoogieVar>>
			implements Iterable<Triple<LETTER, IPredicate, Set<STATE>>> {
		private final Deque<LETTER> mCalls;
		private final Deque<IPredicate> mPredicates;
		private final Deque<Set<STATE>> mStates;

		private TripleStack() {
			mCalls = new ArrayDeque<>();
			mPredicates = new ArrayDeque<>();
			mStates = new ArrayDeque<>();
		}

		public Deque<LETTER> getCalls() {
			return mCalls;
		}

		public Triple<LETTER, IPredicate, Set<STATE>> removeFirst() {
			return new Triple<>(mCalls.removeFirst(), mPredicates.removeFirst(), mStates.removeFirst());
		}

		public boolean isEmpty() {
			// its enough, they always have the same size
			return mCalls.isEmpty();
		}

		public void addFirst(final Triple<LETTER, IPredicate, Set<STATE>> hierarchicalPreState) {
			mCalls.addFirst(hierarchicalPreState.getFirst());
			mPredicates.addFirst(hierarchicalPreState.getSecond());
			mStates.addFirst(hierarchicalPreState.getThird());
		}

		@Override
		public String toString() {
			return getCalls().toString();
		}

		@Override
		public Iterator<Triple<LETTER, IPredicate, Set<STATE>>> iterator() {
			return new Iterator<Triple<LETTER, IPredicate, Set<STATE>>>() {
				private final Iterator<LETTER> mCallIter = mCalls.iterator();
				private final Iterator<IPredicate> mPredicatesIter = mPredicates.iterator();
				private final Iterator<Set<STATE>> mStatesIter = mStates.iterator();

				@Override
				public boolean hasNext() {
					return mCallIter.hasNext();
				}

				@Override
				public Triple<LETTER, IPredicate, Set<STATE>> next() {
					return new Triple<>(mCallIter.next(), mPredicatesIter.next(), mStatesIter.next());
				}
			};
		}
	}
}
