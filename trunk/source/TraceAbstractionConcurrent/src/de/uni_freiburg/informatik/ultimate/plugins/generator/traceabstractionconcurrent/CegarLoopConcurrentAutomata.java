/*
 * Copyright (C) 2014-2015 Daniel Dietsch (dietsch@informatik.uni-freiburg.de)
 * Copyright (C) 2011-2015 Matthias Heizmann (heizmann@informatik.uni-freiburg.de)
 * Copyright (C) 2015 University of Freiburg
 *
 * This file is part of the ULTIMATE TraceAbstractionConcurrent plug-in.
 *
 * The ULTIMATE TraceAbstractionConcurrent plug-in is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * The ULTIMATE TraceAbstractionConcurrent plug-in is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with the ULTIMATE TraceAbstractionConcurrent plug-in. If not, see <http://www.gnu.org/licenses/>.
 *
 * Additional permission under GNU GPL version 3 section 7:
 * If you modify the ULTIMATE TraceAbstractionConcurrent plug-in, or any covered work, by linking
 * or combining it with Eclipse RCP (or a modified version of Eclipse RCP),
 * containing parts covered by the terms of the Eclipse Public License, the
 * licensors of the ULTIMATE TraceAbstractionConcurrent plug-in grant you additional permission
 * to convey the resulting work.
 */
package de.uni_freiburg.informatik.ultimate.plugins.generator.traceabstractionconcurrent;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import de.uni_freiburg.informatik.ultimate.automata.AutomataLibraryException;
import de.uni_freiburg.informatik.ultimate.automata.AutomataLibraryServices;
import de.uni_freiburg.informatik.ultimate.automata.AutomataOperationCanceledException;
import de.uni_freiburg.informatik.ultimate.automata.nestedword.IDoubleDeckerAutomaton;
import de.uni_freiburg.informatik.ultimate.automata.nestedword.INestedWordAutomaton;
import de.uni_freiburg.informatik.ultimate.automata.nestedword.INestedWordAutomatonSimple;
import de.uni_freiburg.informatik.ultimate.automata.nestedword.NestedWord;
import de.uni_freiburg.informatik.ultimate.automata.nestedword.operations.Accepts;
import de.uni_freiburg.informatik.ultimate.automata.nestedword.operations.Difference;
import de.uni_freiburg.informatik.ultimate.automata.nestedword.operations.PowersetDeterminizer;
import de.uni_freiburg.informatik.ultimate.automata.nestedword.operations.oldapi.IOpWithDelayedDeadEndRemoval;
import de.uni_freiburg.informatik.ultimate.automata.nestedword.senwa.DifferenceSenwa;
import de.uni_freiburg.informatik.ultimate.automata.statefactory.IStateFactory;
import de.uni_freiburg.informatik.ultimate.core.model.services.IToolchainStorage;
import de.uni_freiburg.informatik.ultimate.core.model.services.IUltimateServiceProvider;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.cfg.CfgSmtToolkit;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.hoaretriple.IHoareTripleChecker;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.hoaretriple.IncrementalHoareTripleChecker;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.smt.predicates.IPredicate;
import de.uni_freiburg.informatik.ultimate.plugins.generator.rcfgbuilder.cfg.BoogieIcfgContainer;
import de.uni_freiburg.informatik.ultimate.plugins.generator.rcfgbuilder.cfg.BoogieIcfgLocation;
import de.uni_freiburg.informatik.ultimate.plugins.generator.rcfgbuilder.cfg.CodeBlock;
import de.uni_freiburg.informatik.ultimate.plugins.generator.traceabstraction.BasicCegarLoop;
import de.uni_freiburg.informatik.ultimate.plugins.generator.traceabstraction.CegarLoopStatisticsDefinitions;
import de.uni_freiburg.informatik.ultimate.plugins.generator.traceabstraction.PredicateFactoryForInterpolantAutomata;
import de.uni_freiburg.informatik.ultimate.plugins.generator.traceabstraction.PredicateFactoryResultChecking;
import de.uni_freiburg.informatik.ultimate.plugins.generator.traceabstraction.TraceAbstractionBenchmarks;
import de.uni_freiburg.informatik.ultimate.plugins.generator.traceabstraction.TraceAbstractionUtils;
import de.uni_freiburg.informatik.ultimate.plugins.generator.traceabstraction.automataminimization.AutomataMinimization;
import de.uni_freiburg.informatik.ultimate.plugins.generator.traceabstraction.automataminimization.AutomataMinimization.AutomataMinimizationTimeout;
import de.uni_freiburg.informatik.ultimate.plugins.generator.traceabstraction.interpolantautomata.transitionappender.DeterministicInterpolantAutomaton;
import de.uni_freiburg.informatik.ultimate.plugins.generator.traceabstraction.predicates.IMLPredicate;
import de.uni_freiburg.informatik.ultimate.plugins.generator.traceabstraction.predicates.InductivityCheck;
import de.uni_freiburg.informatik.ultimate.plugins.generator.traceabstraction.predicates.PredicateFactory;
import de.uni_freiburg.informatik.ultimate.plugins.generator.traceabstraction.preferences.TAPreferences;
import de.uni_freiburg.informatik.ultimate.plugins.generator.traceabstraction.preferences.TAPreferences.Artifact;
import de.uni_freiburg.informatik.ultimate.plugins.generator.traceabstraction.preferences.TraceAbstractionPreferenceInitializer.InterpolationTechnique;
import de.uni_freiburg.informatik.ultimate.plugins.generator.traceabstraction.preferences.TraceAbstractionPreferenceInitializer.Minimization;

public class CegarLoopConcurrentAutomata extends BasicCegarLoop {
	
	public CegarLoopConcurrentAutomata(final String name, final BoogieIcfgContainer rootNode, final CfgSmtToolkit csToolkit, final PredicateFactory predicateFactory,
			final TraceAbstractionBenchmarks timingStatistics, final TAPreferences taPrefs,
			final Collection<BoogieIcfgLocation> errorLocs, final IUltimateServiceProvider services,
			final IToolchainStorage storage) {
		super(name, rootNode, csToolkit, predicateFactory, taPrefs, errorLocs, InterpolationTechnique.Craig_TreeInterpolation, false,
				services, storage);
		// mContentFactory = new TaConcurContentFactory(
		// rootNode.getRootAnnot().getLocNodes(),
		// this,
		// super.mCsToolkit.getTheory(),
		// super.mHoareAnnotation,
		// false);
	}
	
	@Override
	protected void getInitialAbstraction() {
		final IStateFactory<IPredicate> predicateFactory =
				new PredicateFactoryForInterpolantAutomata(super.mCsToolkit.getManagedScript(), mPredicateFactory, mPref.computeHoareAnnotation());
		final CFG2Automaton cFG2NestedWordAutomaton = new Cfg2Nwa(mIcfgContainer, predicateFactory, mCsToolkit, mPredicateFactory, mServices,
				mXnfConversionTechnique, mSimplificationTechnique);
		mAbstraction = (INestedWordAutomatonSimple<CodeBlock, IPredicate>) cFG2NestedWordAutomaton.getResult();
		
		if (mIteration <= mPref.watchIteration()
				&& (mPref.artifact() == Artifact.ABSTRACTION || mPref.artifact() == Artifact.RCFG)) {
			mArtifactAutomaton = mAbstraction;
		}
	}
	
	private static <E> Set<E> asHashSet(final E[] array) {
		return new HashSet<>(Arrays.asList(array));
	}

	@Override
	protected void minimizeAbstraction(final PredicateFactoryForInterpolantAutomata predicateFactoryRefinement,
			final PredicateFactoryResultChecking resultCheckPredFac, final Minimization minimization)
			throws AutomataOperationCanceledException, AutomataLibraryException, AssertionError {
		if (mPref.dumpAutomata()) {
			final String filename = mIcfgContainer.getFilename() + "_DiffAutomatonBeforeMinimization_Iteration" + mIteration;
			super.writeAutomatonToFile(mAbstraction, filename);
		}
		final Function<IMLPredicate, Set<BoogieIcfgLocation>> lcsProvider = x -> asHashSet(x.getProgramPoints());
		AutomataMinimization<Set<BoogieIcfgLocation>, IMLPredicate> am;
		try {
			am = new AutomataMinimization<>(mServices,
					(INestedWordAutomaton<CodeBlock, IPredicate>) mAbstraction, minimization, mComputeHoareAnnotation,
					mIteration, predicateFactoryRefinement, MINIMIZE_EVERY_KTH_ITERATION, mStoredRawInterpolantAutomata,
					mInterpolAutomaton, MINIMIZATION_TIMEOUT, resultCheckPredFac, lcsProvider, false);
		} catch (final AutomataMinimizationTimeout e) {
			mCegarLoopBenchmark.addAutomataMinimizationData(e.getStatistics());
			throw e.getAutomataOperationCanceledException();
		}
		mCegarLoopBenchmark.addAutomataMinimizationData(am.getStatistics());

		final boolean newAutomatonWasBuilt = am.newAutomatonWasBuilt();

		if (newAutomatonWasBuilt) {
			// postprocessing after minimization
			final IDoubleDeckerAutomaton<CodeBlock, IPredicate> newAbstraction = am.getMinimizedAutomaton();

			// extract Hoare annotation
			if (mComputeHoareAnnotation) {
				final Map<IPredicate, IPredicate> oldState2newState = am.getOldState2newStateMapping();
				if (oldState2newState == null) {
					throw new AssertionError("Hoare annotation and " + minimization + " incompatible");
				}
				mHaf.updateOnMinimization(oldState2newState, newAbstraction);
			}

			// statistics
			final int oldSize = mAbstraction.size();
			final int newSize = newAbstraction.size();
			assert oldSize == 0 || oldSize >= newSize : "Minimization increased state space";

			// use result
			mAbstraction = newAbstraction;

		}
	}
	
	@Override
	protected boolean refineAbstraction() throws AutomataLibraryException {
		mStateFactoryForRefinement.setIteration(super.mIteration);
		// howDifferentAreInterpolants(mInterpolAutomaton.getStates());
		
		mCegarLoopBenchmark.start(CegarLoopStatisticsDefinitions.AutomataDifference.toString());
		final boolean explointSigmaStarConcatOfIA = !mComputeHoareAnnotation;
		
		final PredicateFactoryForInterpolantAutomata predicateFactory =
				(PredicateFactoryForInterpolantAutomata) mAbstraction.getStateFactory();
		
		final INestedWordAutomaton<CodeBlock, IPredicate> oldAbstraction =
				(INestedWordAutomaton<CodeBlock, IPredicate>) mAbstraction;
		final Map<IPredicate, Set<IPredicate>> removedDoubleDeckers = null;
		final Map<IPredicate, IPredicate> context2entry = null;
		final IHoareTripleChecker htc = TraceAbstractionUtils.constructEfficientHoareTripleChecker(mServices, mPref.getHoareTripleChecks(),
				mCsToolkit, mTraceCheckAndRefinementEngine.getPredicateUnifier());
		mLogger.debug("Start constructing difference");
		// assert (oldAbstraction.getStateFactory() == mInterpolAutomaton.getStateFactory());
		
		IOpWithDelayedDeadEndRemoval<CodeBlock, IPredicate> diff;
		
		final DeterministicInterpolantAutomaton determinized =
				new DeterministicInterpolantAutomaton(mServices, mCsToolkit, htc, mInterpolAutomaton, mTraceCheckAndRefinementEngine.getPredicateUnifier(),
						mLogger, false, false);
		// ComplementDeterministicNwa<CodeBlock, IPredicate>
		// cdnwa = new ComplementDeterministicNwa<>(dia);
		final PowersetDeterminizer<CodeBlock, IPredicate> psd2 =
				new PowersetDeterminizer<>(determinized, false, mPredicateFactoryInterpolantAutomata);
		
		if (mPref.differenceSenwa()) {
			diff = new DifferenceSenwa<>(new AutomataLibraryServices(mServices), oldAbstraction, determinized, psd2,
					false);
		} else {
			diff = new Difference<>(new AutomataLibraryServices(mServices), oldAbstraction, determinized, psd2,
					mStateFactoryForRefinement, explointSigmaStarConcatOfIA);
		}
		determinized.switchToReadonlyMode();
		assert !mCsToolkit.getManagedScript().isLocked();
		assert new InductivityCheck(mServices, mInterpolAutomaton, false, true,
				new IncrementalHoareTripleChecker(mCsToolkit))
						.getResult();
		// do the following check only to obtain logger messages of
		// checkInductivity
		
		if (REMOVE_DEAD_ENDS) {
			if (mComputeHoareAnnotation) {
				final Difference<CodeBlock, IPredicate> difference = (Difference<CodeBlock, IPredicate>) diff;
				mHaf.updateOnIntersection(difference.getFst2snd2res(), difference.getResult());
			}
			diff.removeDeadEnds();
			if (mComputeHoareAnnotation) {
				mHaf.addDeadEndDoubleDeckers(diff);
			}
		}
		
		mAbstraction = diff.getResult();
		// mDeadEndRemovalTime = diff.getDeadEndRemovalTime();
		if (mPref.dumpAutomata()) {
			final String filename = "InterpolantAutomaton_Iteration" + mIteration;
			super.writeAutomatonToFile(mInterpolAutomaton, filename);
		}
		
		mCegarLoopBenchmark.addEdgeCheckerData(htc.getEdgeCheckerBenchmark());
		mCegarLoopBenchmark.stop(CegarLoopStatisticsDefinitions.AutomataDifference.toString());
		
		final Minimization minimization = mPref.getMinimization();
		switch (minimization) {
		case NONE:
			break;
		case MINIMIZE_SEVPA:
		case SHRINK_NWA:
			minimizeAbstraction(mStateFactoryForRefinement, mPredicateFactoryResultChecking, minimization);
			break;
		default:
			throw new AssertionError();
		}
		
		final boolean stillAccepted = new Accepts<>(new AutomataLibraryServices(mServices),
				(INestedWordAutomatonSimple<CodeBlock, IPredicate>) mAbstraction,
				(NestedWord<CodeBlock>) mCounterexample.getWord()).getResult();
		return !stillAccepted;
	}
}
