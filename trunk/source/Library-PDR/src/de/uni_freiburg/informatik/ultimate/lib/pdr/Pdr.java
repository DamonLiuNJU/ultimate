/*
 * Copyright (C) 2018 Jonas Werner (jonaswerner95@gmail.com)
 *
 * This file is part of the ULTIMATE PDR library .
 *
 * The ULTIMATE IcfgTransformer is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * The ULTIMATE IcfgTransformer is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with the ULTIMATE PDR library . If not, see <http://www.gnu.org/licenses/>.
 *
 * Additional permission under GNU GPL version 3 section 7:
 * If you modify the ULTIMATE PDR library , or any covered work, by linking
 * or combining it with Eclipse RCP (or a modified version of Eclipse RCP),
 * containing parts covered by the terms of the Eclipse Public License, the
 * licensors of the ULTIMATE PDR library grant you additional permission
 * to convey the resulting work.
 */
package de.uni_freiburg.informatik.ultimate.lib.pdr;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import de.uni_freiburg.informatik.ultimate.core.lib.exceptions.ToolchainCanceledException;
import de.uni_freiburg.informatik.ultimate.core.model.services.ILogger;
import de.uni_freiburg.informatik.ultimate.core.model.services.IUltimateServiceProvider;
import de.uni_freiburg.informatik.ultimate.core.model.translation.IProgramExecution;
import de.uni_freiburg.informatik.ultimate.logic.Script.LBool;
import de.uni_freiburg.informatik.ultimate.logic.Term;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.cfg.CfgSmtToolkit;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.cfg.IcfgUtils;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.cfg.structure.IIcfg;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.cfg.structure.IIcfgCallTransition;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.cfg.structure.IIcfgInternalTransition;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.cfg.structure.IIcfgReturnTransition;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.cfg.structure.IIcfgTransition;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.cfg.structure.IInternalAction;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.cfg.structure.IcfgEdge;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.cfg.structure.IcfgLocation;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.cfg.structure.IcfgLocationIterator;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.cfg.transitions.TransFormula;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.cfg.variables.IProgramNonOldVar;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.hoaretriple.IHoareTripleChecker;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.hoaretriple.IHoareTripleChecker.Validity;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.smt.interpolant.IInterpolantGenerator;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.smt.interpolant.InterpolantComputationStatus;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.smt.interpolant.InterpolantComputationStatus.ItpErrorStatus;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.smt.managedscript.ManagedScript;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.smt.predicates.IPredicate;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.smt.predicates.IPredicateUnifier;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.smt.predicates.PredicateTransformer;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.smt.predicates.TermDomainOperationProvider;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.smt.tracecheck.ITraceCheck;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.smt.tracecheck.ITraceCheckPreferences;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.smt.tracecheck.TraceCheckReasonUnknown;
import de.uni_freiburg.informatik.ultimate.util.datastructures.relation.Triple;
import de.uni_freiburg.informatik.ultimate.util.statistics.IStatisticsDataProvider;

/**
 *
 * @author Jonas Werner (jonaswerner95@gmail.com)
 *
 */
public class Pdr<LETTER extends IIcfgTransition<?>> implements ITraceCheck, IInterpolantGenerator<LETTER> {

	private final ILogger mLogger;
	private final IUltimateServiceProvider mServices;
	private final Map<IcfgLocation, List<IPredicate>> mFrames;
	private final ManagedScript mScript;
	private final PredicateTransformer<Term, IPredicate, TransFormula> mPredTrans;
	private final IIcfg<? extends IcfgLocation> mIcfg;
	private final CfgSmtToolkit mCsToolkit;
	private final IHoareTripleChecker mHtc;
	private final IPredicateUnifier mPredicateUnifier;
	private final IPredicate mTruePred;
	private final IPredicate mFalsePred;
	private final List<LETTER> mTrace;

	private LBool mIsTraceCorrect;
	private IProgramExecution<IcfgEdge, Term> mFeasibleProgramExecution;

	public Pdr(final ILogger logger, final ITraceCheckPreferences prefs, final IPredicateUnifier predicateUnifier,
			final IHoareTripleChecker htc, final List<LETTER> counterexample) {
		// from params
		mLogger = logger;
		mPredicateUnifier = predicateUnifier;
		mHtc = htc;
		mTrace = counterexample;

		// stuff from prefs
		mServices = prefs.getUltimateServices();
		mIcfg = prefs.getIcfgContainer();
		mCsToolkit = prefs.getCfgSmtToolkit();
		mScript = mCsToolkit.getManagedScript();

		mFrames = new HashMap<>();
		mPredTrans = new PredicateTransformer<>(mScript, new TermDomainOperationProvider(mServices, mScript));

		mTruePred = mPredicateUnifier.getOrConstructPredicate(mScript.getScript().term("true"));
		mFalsePred = mPredicateUnifier.getOrConstructPredicate(mScript.getScript().term("false"));

		mLogger.debug("PDR initialized...");
		computePdr();
	}

	private Object computePdr() {

		final Set<? extends IcfgLocation> init = mIcfg.getInitialNodes();
		final Set<? extends IcfgLocation> error = IcfgUtils.getErrorLocations(mIcfg);

		/**
		 * Check for 0-Counter-Example
		 */
		for (final IcfgLocation initNode : init) {
			if (error.contains(initNode)) {
				return null;
			}
		}

		/**
		 * Initialize level 0.
		 */
		final IcfgLocationIterator<? extends IcfgLocation> iter = new IcfgLocationIterator<>(mIcfg);
		while (iter.hasNext()) {
			final IcfgLocation loc = iter.next();
			if (error.contains(loc)) {
				continue;
			}
			mFrames.put(loc, new ArrayList<>());
			if (init.contains(loc)) {
				mFrames.get(loc).add(mTruePred);
			} else {
				mFrames.get(loc).add(mFalsePred);
			}
		}

		int level = 0;

		while (true) {
			if (!mServices.getProgressMonitorService().continueProcessing()) {
				throw new ToolchainCanceledException(getClass(), "Timeout or canceled while running Pdr");
			}
			/**
			 * Initialize new level.
			 */
			for (final Entry<IcfgLocation, List<IPredicate>> trace : mFrames.entrySet()) {
				trace.getValue().add(mTruePred);
			}

			level += 1;

			final List<Triple<IPredicate, IcfgLocation, Integer>> proofObligations = new ArrayList<>();

			/**
			 * Generate the initial proof-obligations
			 */
			for (final IcfgEdge edge : error.iterator().next().getIncomingEdges()) {
				/*
				 * AssertionError: No corresponding IProgramVar for v_main_x_3 ?
				 */
				final Term sp = mPredTrans.strongestPostcondition(mTruePred, edge.getTransformula());
				final IPredicate proofObligationPred = mPredicateUnifier.getOrConstructPredicate(sp);
				final Triple<IPredicate, IcfgLocation, Integer> initialProofObligation =
						new Triple<>(proofObligationPred, edge.getSource(), level);
				proofObligations.add(initialProofObligation);
			}

			/**
			 * Generated proof-obligation on level 0 -> error is reachable
			 */
			if (!blockingPhase(proofObligations)) {
				throw new UnsupportedOperationException("error reachable");
			}

			/**
			 * Found invariant -> error is not reachable
			 */
			if (propagationPhase()) {
				throw new UnsupportedOperationException("error not reachable");
			}
		}
	}

	/**
	 * Blocking-phase, for blocking proof-obligations.
	 *
	 * @return false, if proof-obligation on level 0 is created true, if there is no proof-obligation left
	 */
	private boolean blockingPhase(final List<Triple<IPredicate, IcfgLocation, Integer>> initialProofObligations) {

		final Deque<Triple<IPredicate, IcfgLocation, Integer>> proofObligations =
				new ArrayDeque<>(initialProofObligations);
		while (!proofObligations.isEmpty()) {
			final Triple<IPredicate, IcfgLocation, Integer> proofObligation = proofObligations.pop();
			final IPredicate toBeBlocked = proofObligation.getFirst();
			final IcfgLocation location = proofObligation.getSecond();
			final int level = proofObligation.getThird();

			for (final IcfgEdge predecessorTransition : location.getIncomingEdges()) {
				final IcfgLocation predecessor = predecessorTransition.getSource();
				final IPredicate predecessorFrame = mFrames.get(predecessor).get(level - 1);

				final Validity result =
						checkSatInternal(predecessorFrame, (IInternalAction) predecessorTransition, toBeBlocked);

				/**
				 * If Sat generate new proof-obligation
				 */
				if (result == Validity.INVALID) {
					/**
					 * Found Error trace
					 */
					if (level - 1 == 0) {
						return false;
					}

					final IPredicate preCondition;

					if (predecessorTransition instanceof IIcfgInternalTransition) {
						preCondition = (IPredicate) mPredTrans.weakestPrecondition(toBeBlocked,
								predecessorTransition.getTransformula());

					} else if (predecessorTransition instanceof IIcfgCallTransition) {
						final TransFormula globalVarsAssignments = mCsToolkit.getOldVarsAssignmentCache()
								.getGlobalVarsAssignment(predecessorTransition.getSucceedingProcedure());
						final TransFormula oldVarAssignments = mCsToolkit.getOldVarsAssignmentCache()
								.getOldVarsAssignment(predecessorTransition.getSucceedingProcedure());
						final Set<IProgramNonOldVar> modifiableGlobals = mCsToolkit.getModifiableGlobalsTable()
								.getModifiedBoogieVars(predecessorTransition.getSucceedingProcedure());
						preCondition = (IPredicate) mPredTrans.weakestPreconditionCall(toBeBlocked,
								predecessorTransition.getTransformula(), globalVarsAssignments, oldVarAssignments,
								modifiableGlobals);

					} else if (predecessorTransition instanceof IIcfgReturnTransition) {
						throw new UnsupportedOperationException();

					} else {
						throw new UnsupportedOperationException(
								"Unknown transition type: " + predecessorTransition.getClass().toString());
					}

					final Triple<IPredicate, IcfgLocation, Integer> newProofObligation =
							new Triple<>(preCondition, predecessor, level - 1);
					proofObligations.addFirst(newProofObligation);

					/**
					 * If Unsat strengthen the frames of the location
					 */
				} else if (result == Validity.VALID) {
					for (int i = 0; i <= level; i++) {
						IPredicate fTerm = mFrames.get(location).get(i);
						final IPredicate negToBeBlocked = mPredicateUnifier.getPredicateFactory().not(toBeBlocked);
						fTerm = mPredicateUnifier.getPredicateFactory().and(fTerm, negToBeBlocked);
						mFrames.get(location).set(i, fTerm);
					}

				} else {
					/**
					 * What do if unknown?
					 *
					 */
					throw new UnsupportedOperationException("what to do with unknown?");
				}
			}
		}
		return true;
	}

	/**
	 * Use a {@link IHoareTripleChecker} to check for satisfiability of pre &and; tf &and; post
	 *
	 * This method converts the post condition because an {@link IHoareTripleChecker} checks for P &and; S &and; !Q
	 *
	 * @param pre
	 * @param trans
	 * @param post
	 *
	 * @return {@link Validity#VALID} iff the formula is unsat, {@link Validity#INVALID} iff the formula is sat,
	 *         {@link Validity#UNKNOWN} iff the solver was not able to find a solution, and {@link Validity#NOT_CHECKED}
	 *         should not be returned
	 */
	private Validity checkSatInternal(final IPredicate pre, final IInternalAction trans, final IPredicate post) {
		final IPredicate notP = not(post);
		final Validity result = mHtc.checkInternal(pre, trans, notP);
		assert result != Validity.NOT_CHECKED;
		return result;

	}

	private IPredicate not(final IPredicate pred) {
		return mPredicateUnifier.getOrConstructPredicate(mPredicateUnifier.getPredicateFactory().not(pred));
	}

	/**
	 * Propagation-Phase, for finding invariants
	 */
	private boolean propagationPhase() {
		for (final Entry<IcfgLocation, List<IPredicate>> locationTrace : mFrames.entrySet()) {
			final List<IPredicate> frames = locationTrace.getValue();
			for (int i = 0; i < frames.size(); i++) {
				for (int k = i + 1; k < frames.size(); k++) {
					if (frames.get(i) == frames.get(k)) {
						return true;
					}
				}
			}
		}
		return false;
	}

	private IProgramExecution<IcfgEdge, Term> computeProgramExecution() {
		// note: use IcfgProgramExecutionBuilder when it becomes available
		throw new UnsupportedOperationException("not yet implemented");
	}

	// TODO: Implement ITraceCheck interface

	@Override
	public LBool isCorrect() {
		return mIsTraceCorrect;
	}

	@Override
	public IPredicate getPrecondition() {
		return mTruePred;
	}

	@Override
	public IPredicate getPostcondition() {
		return mFalsePred;
	}

	@Override
	public Map<Integer, IPredicate> getPendingContexts() {
		return Collections.emptyMap();
	}

	@Override
	public boolean providesRcfgProgramExecution() {
		return mIsTraceCorrect == LBool.SAT;
	}

	@Override
	public IProgramExecution<IcfgEdge, Term> getRcfgProgramExecution() {
		if (mFeasibleProgramExecution == null) {
			mFeasibleProgramExecution = computeProgramExecution();
		}
		return mFeasibleProgramExecution;
	}

	@Override
	public IStatisticsDataProvider getTraceCheckBenchmark() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public ToolchainCanceledException getToolchainCanceledExpection() {
		return null;
	}

	@Override
	public TraceCheckReasonUnknown getTraceCheckReasonUnknown() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public boolean wasTracecheckFinishedNormally() {
		// TODO Auto-generated method stub
		return true;
	}

	// TODO: Implement iInterpolantGenerator interface

	@Override
	public List<LETTER> getTrace() {
		return mTrace;
	}

	@Override
	public IPredicate[] getInterpolants() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public IPredicateUnifier getPredicateUnifier() {
		return mPredicateUnifier;
	}

	@Override
	public boolean isPerfectSequence() {
		// if we analyze path programs, each of our sequences is correct
		return isCorrect() == LBool.UNSAT;
	}

	@Override
	public InterpolantComputationStatus getInterpolantComputationStatus() {
		if (isCorrect() == LBool.UNSAT) {
			return new InterpolantComputationStatus(true, null, null);
		} else if (isCorrect() == LBool.SAT) {
			return new InterpolantComputationStatus(false, ItpErrorStatus.TRACE_FEASIBLE, null);
		} else {
			throw new UnsupportedOperationException();
		}
	}

}
