/*
 * Copyright (C) 2018 Matthias Heizmann (heizmann@informatik.uni-freiburg.de)
 * Copyright (C) 2018 University of Freiburg
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

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;

import de.uni_freiburg.informatik.ultimate.core.lib.models.annotation.Check;
import de.uni_freiburg.informatik.ultimate.core.lib.models.annotation.Check.Spec;
import de.uni_freiburg.informatik.ultimate.core.lib.models.annotation.LoopEntryAnnotation;
import de.uni_freiburg.informatik.ultimate.core.lib.results.GenericResultAtElement;
import de.uni_freiburg.informatik.ultimate.core.lib.results.InsufficientAnnotationResult;
import de.uni_freiburg.informatik.ultimate.core.model.results.IResultWithSeverity.Severity;
import de.uni_freiburg.informatik.ultimate.core.model.services.ILogger;
import de.uni_freiburg.informatik.ultimate.core.model.services.IToolchainStorage;
import de.uni_freiburg.informatik.ultimate.core.model.services.IUltimateServiceProvider;
import de.uni_freiburg.informatik.ultimate.core.model.translation.IProgramExecution.ProgramState;
import de.uni_freiburg.informatik.ultimate.logic.Term;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.cfg.structure.BasicInternalAction;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.cfg.structure.IIcfg;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.cfg.structure.IIcfgTransition;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.cfg.structure.IcfgEdge;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.cfg.structure.IcfgLocation;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.cfg.transitions.UnmodifiableTransFormula;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.hoaretriple.IHoareTripleChecker.Validity;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.hoaretriple.IncrementalHoareTripleChecker;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.smt.SmtUtils.SimplificationTechnique;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.smt.SmtUtils.XnfConversionTechnique;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.smt.predicates.IPredicate;
import de.uni_freiburg.informatik.ultimate.plugins.generator.traceabstraction.predicates.PredicateFactory;

/**
 * Check given annotation without inferring invariants.
 *
 * @author Matthias Heizmann (heizmann@informatik.uni-freiburg.de)
 *
 */
public class InvariantChecker {

	private final ILogger mLogger;
	private final IUltimateServiceProvider mServices;
	private final IToolchainStorage mToolchainStorage;
	private final IIcfg<IcfgLocation> mIcfg;

	private final Map<IcfgLocation, IcfgEdge> mLoopLoc2errorEdge = new HashMap<>();
	private final Map<IcfgLocation, IcfgEdge> mLoopErrorLoc2errorEdge = new HashMap<>();

	public enum LocationType {
		ENTRY, LOOP_HEAD, ERROR_LOC, UNKNOWN, LOOP_INVARIANT_ERROR_LOC
	}

	public InvariantChecker(final IUltimateServiceProvider services, final IToolchainStorage storage,
			final IIcfg<IcfgLocation> icfg) {
		mServices = services;
		mToolchainStorage = storage;
		mLogger = mServices.getLoggingService().getLogger(Activator.PLUGIN_ID);
		mIcfg = icfg;
		final List<IcfgLocation> loopLocWithoutInvariant = new ArrayList<>();
		for (final IcfgLocation loopLoc : mIcfg.getLoopLocations()) {
			final IcfgEdge errorEdge = getErrorEdgeForLoopInvariant(loopLoc);
			if (errorEdge == null) {
				loopLocWithoutInvariant.add(loopLoc);
			} else {
				mLoopLoc2errorEdge.put(loopLoc, errorEdge);
				mLoopErrorLoc2errorEdge.put(errorEdge.getTarget(), errorEdge);
			}
		}
		if (!loopLocWithoutInvariant.isEmpty()) {

			final String shortDescription = "Not every loop was annotated with an invariant.";
			final String longDescription = "Missing invariants at: " + loopLocWithoutInvariant;
			final Severity severity = Severity.ERROR;
			final GenericResultAtElement<?> grae = new GenericResultAtElement<>(
					loopLocWithoutInvariant.get(0).getOutgoingEdges().get(0), Activator.PLUGIN_ID,
					mServices.getBacktranslationService(), shortDescription, longDescription, severity);
			mServices.getResultService().reportResult(Activator.PLUGIN_ID, grae);
			return;
		}
		mLogger.info("Found " + mIcfg.getLoopLocations().size() + " loops.");

		final Set<IcfgLocation> loopLocsAndNonLoopErrorLocs = new HashSet<>();
		final Map<String, Set<IcfgLocation>> proc2errNodes = icfg.getProcedureErrorNodes();
		for (final Entry<String, Set<IcfgLocation>> entry : proc2errNodes.entrySet()) {
			for (final IcfgLocation errorLoc : entry.getValue()) {
				final IcfgEdge loopErrorEdge = mLoopErrorLoc2errorEdge.get(errorLoc);
				if (loopErrorEdge != null) {
					loopLocsAndNonLoopErrorLocs.add(loopErrorEdge.getSource());
				} else {
					loopLocsAndNonLoopErrorLocs.add(errorLoc);
				}
			}
		}

		final List<TwoPointSubgraphDefinition> tpsds = new ArrayList<>();
		for (final IcfgLocation backwardStartLoc : loopLocsAndNonLoopErrorLocs) {
			tpsds.addAll(findSubgraphGivenError(backwardStartLoc, icfg));
		}
		for (final TwoPointSubgraphDefinition tpsd : tpsds) {
			final IcfgLocation startLoc = tpsd.getStartLocation();
			final IcfgLocation errorLoc = tpsd.getEndLocation();
			IcfgEdge omitEdge = mLoopLoc2errorEdge.get(startLoc);
			if (!tpsd.getSubgraphEdges().contains(omitEdge)) {
				omitEdge = null;
			}
			final AcyclicSubgraphMerger asm = new AcyclicSubgraphMerger(mServices, mIcfg, tpsd.getSubgraphEdges(),
					tpsd.getStartLocation(), omitEdge, Collections.singleton(tpsd.getEndLocation()));
			final UnmodifiableTransFormula tf = asm.getTransFormula(errorLoc);
			Objects.requireNonNull(tf);
			doCheck(startLoc, tf, errorLoc);
		}

	}

	private List<TwoPointSubgraphDefinition> findSubgraphGivenError(final IcfgLocation backwardStartLoc,
			final IIcfg<IcfgLocation> icfg) {
		final List<TwoPointSubgraphDefinition> tpsds = new ArrayList<>();
		final ArrayDeque<IcfgEdge> worklistBackward = new ArrayDeque<>();
		final Set<IcfgEdge> seenBackward = new HashSet<>();
		final Set<IcfgLocation> startLocations = new HashSet<>();
		worklistBackward.addAll(backwardStartLoc.getIncomingEdges());
		seenBackward.addAll(backwardStartLoc.getIncomingEdges());
		while (!worklistBackward.isEmpty()) {
			final IcfgEdge edge = worklistBackward.removeFirst();
			final IcfgLocation loc = edge.getSource();
			if (icfg.getInitialNodes().contains(loc) || icfg.getLoopLocations().contains(loc)) {
				startLocations.add(loc);
			} else {
				for (final IcfgEdge pred : loc.getIncomingEdges()) {
					if (!seenBackward.contains(pred)) {
						seenBackward.add(pred);
						worklistBackward.add(pred);
					}
				}
			}

		}
		for (final IcfgLocation startLoc : startLocations) {
			final List<TwoPointSubgraphDefinition> newTpsds =
					findSubgraphGivenStart(startLoc, Collections.unmodifiableSet(seenBackward), icfg, backwardStartLoc);
			for (final TwoPointSubgraphDefinition tpsd : newTpsds) {
				if (mLoopLoc2errorEdge.containsKey(backwardStartLoc)) {
					final IcfgEdge errorEdge = mLoopLoc2errorEdge.get(backwardStartLoc);
					final IcfgLocation errorLoc = errorEdge.getTarget();
					if (tpsd.getEndLocation() != errorLoc) {
						throw new AssertionError("wrong error loc");
					}
				} else {
					if (tpsd.getEndLocation() != backwardStartLoc) {
						throw new AssertionError("wrong error loc");
					}
				}
			}
			tpsds.addAll(newTpsds);
		}
		return tpsds;
	}

	private List<TwoPointSubgraphDefinition> findSubgraphGivenStart(final IcfgLocation startLoc,
			final Set<IcfgEdge> seenBackward, final IIcfg<IcfgLocation> icfg, final IcfgLocation backwardStartLoc) {
		final List<TwoPointSubgraphDefinition> result = new ArrayList<>();
		final ArrayDeque<IcfgEdge> worklistForward = new ArrayDeque<>();
		final Set<IcfgEdge> seenForward = new HashSet<>();
		final Set<IcfgLocation> errorLocations = new HashSet<>();
		for (final IcfgEdge edge : startLoc.getOutgoingEdges()) {
			if (seenBackward.contains(edge)) {
				worklistForward.add(edge);
				seenForward.add(edge);
			}
		}
		while (!worklistForward.isEmpty()) {
			final IcfgEdge currentEdge = worklistForward.removeFirst();
			final IcfgLocation loc = currentEdge.getTarget();
			if (loc == backwardStartLoc) {
				if (icfg.getLoopLocations().contains(loc)) {
					final IcfgEdge loopErrorEdge = mLoopLoc2errorEdge.get(loc);
					seenForward.add(loopErrorEdge);
					errorLocations.add(loopErrorEdge.getTarget());
				} else if (icfg.getProcedureErrorNodes().get(loc.getProcedure()).contains(loc)) {
					errorLocations.add(loc);
				} else {
					throw new AssertionError("unknown backwardStartLoc");
				}
			} else {
				for (final IcfgEdge succEdge : loc.getOutgoingEdges()) {
					if (!seenBackward.contains(succEdge)) {
						// does not belong to search space
						continue;
					}
					seenForward.add(succEdge);
					worklistForward.add(succEdge);
				}
			}
		}
		assert errorLocations.size() == 1;
		final IcfgLocation errorLoc = errorLocations.iterator().next();
		final TwoPointSubgraphDefinition tpsd = new TwoPointSubgraphDefinition(startLoc, seenForward, errorLoc);
		mLogger.info(message23(tpsd));
		result.add(tpsd);
		return result;
	}

	private String message23(final TwoPointSubgraphDefinition tpsd) {
		final StringBuilder sb = new StringBuilder();
		sb.append("Will check inductivity from ");
		sb.append(classify(tpsd.getStartLocation()));
		sb.append(" ");
		sb.append(tpsd.getStartLocation());
		sb.append(" to ");
		sb.append(classify(tpsd.getEndLocation()));
		sb.append(" ");
		sb.append(tpsd.getEndLocation());
		sb.append(". ");
		sb.append("Corresponding subgraph has " + tpsd.getSubgraphEdges().size() + " edges.");
		return sb.toString();
	}

	LocationType classify(final IcfgLocation loc) {
		if (mIcfg.getLoopLocations().contains(loc)) {
			return LocationType.LOOP_HEAD;
		} else if (mLoopErrorLoc2errorEdge.containsKey(loc)) {
			return LocationType.LOOP_INVARIANT_ERROR_LOC;
		} else {
			final String proc = loc.getProcedure();
			if (mIcfg.getProcedureEntryNodes().get(proc).equals(loc)) {
				return LocationType.ENTRY;
			} else if (mIcfg.getProcedureErrorNodes().get(proc).contains(loc)) {
				return LocationType.ERROR_LOC;
			} else {
				return LocationType.UNKNOWN;
			}
		}
	}

	private void doCheck(final IcfgLocation startLoc, final UnmodifiableTransFormula tf, final IcfgLocation errorLoc) {
		final IncrementalHoareTripleChecker htc = new IncrementalHoareTripleChecker(mIcfg.getCfgSmtToolkit(), true);
		final PredicateFactory pf = new PredicateFactory(mServices, mIcfg.getCfgSmtToolkit().getManagedScript(),
				mIcfg.getCfgSmtToolkit().getSymbolTable(), SimplificationTechnique.NONE,
				XnfConversionTechnique.BOTTOM_UP_WITH_LOCAL_SIMPLIFICATION);
		final IPredicate truePredicate =
				pf.newPredicate(mIcfg.getCfgSmtToolkit().getManagedScript().getScript().term("true"));
		final IPredicate falsePredicate =
				pf.newPredicate(mIcfg.getCfgSmtToolkit().getManagedScript().getScript().term("false"));
		final Validity validity = htc.checkInternal(truePredicate,
				new BasicInternalAction(startLoc.getProcedure(), errorLoc.getProcedure(), tf), falsePredicate);
		switch (validity) {
		case INVALID:
			final ProgramState<Term> ctxPre = htc.getCounterexampleStatePrecond();
			final ProgramState<Term> ctxPost = htc.getCounterexampleStatePostcond();
			final InsufficientAnnotationResult<IcfgEdge, Term> iar =
					new InsufficientAnnotationResult<>(startLoc.getOutgoingEdges().get(0), Activator.PLUGIN_ID,
							mServices.getBacktranslationService(), errorLoc.getIncomingEdges().get(0), ctxPre, ctxPost);
			mServices.getResultService().reportResult(Activator.PLUGIN_ID, iar);
			mLogger.info(iar.getShortDescription());
			break;
		case NOT_CHECKED:
			throw new AssertionError();
		case UNKNOWN:
			throw new UnsupportedOperationException();
		case VALID:
			mLogger.info(generateMessage(startLoc, errorLoc, true));
			break;
		default:
			break;
		}
		htc.releaseLock();
	}

	private String generateMessage(final IcfgLocation startLoc, final IcfgLocation errorLoc, final boolean positive) {
		final StringBuilder sb = new StringBuilder();
		sb.append("The annotation(s) from ");
		sb.append(getType(startLoc));
		sb.append(" at line ");
		// sb.append(startLoc.getStartLine());
		sb.append(" to ");
		sb.append(getType(startLoc));
		sb.append(" at line ");
		// sb.append(startLoc.getStartLine());
		sb.append(" is");
		if (!positive) {
			sb.append(" NOT");
		}
		sb.append(" inductive.");
		return sb.toString();
	}

	private String getType(final IcfgLocation startLoc) {
		if (isInvariant(startLoc)) {
			return "loop head";
		} else if (isErrorLoc(startLoc)) {
			return "error location";
		} else if (isLoopLoc(startLoc)) {
			return "loop head";
		} else {
			return "entry";
		}
	}

	public static <E extends IIcfgTransition<IcfgLocation>> Set<E> collectAdjacentEdges(final IIcfg<IcfgLocation> icfg,
			final Set<IcfgLocation> locations) {
		final Set<E> result = new HashSet<>();
		for (final IcfgLocation loc : locations) {
			loc.getOutgoingEdges();
			for (final IcfgEdge edge : loc.getOutgoingEdges()) {
				if (locations.contains(edge.getTarget())) {
					result.add((E) edge);
				}
			}
		}
		return result;
	}

	private void processForward(final ArrayDeque<IcfgLocation> worklistForward, final Set<IcfgLocation> seenForward,
			final Set<IcfgLocation> errorLocations, final IcfgLocation succLoc, final boolean checkForErrorLocs) {
		seenForward.add(succLoc);
		final LoopEntryAnnotation loa = LoopEntryAnnotation.getAnnotation(succLoc);
		if (loa != null) {
			final IcfgLocation eLoc = getErrorEdgeForLoopInvariant(succLoc).getTarget();
			seenForward.add(eLoc);
			errorLocations.add(eLoc);
		} else {
			final Check check = Check.getAnnotation(succLoc);
			if (checkForErrorLocs && check != null) {
				seenForward.add(succLoc);
				errorLocations.add(succLoc);
			} else {
				seenForward.add(succLoc);
				worklistForward.add(succLoc);
			}
		}
	}

	private IcfgEdge getErrorEdgeForLoopInvariant(final IcfgLocation loopLoc) {
		IcfgEdge result = null;
		for (final IcfgEdge succEdge : loopLoc.getOutgoingEdges()) {
			final IcfgLocation succLoc = succEdge.getTarget();
			if (isInvariant(succLoc)) {
				if (result == null) {
					result = succEdge;
				} else {
					throw new UnsupportedOperationException("several invariants");
				}
			}
		}
		return result;
	}

	private boolean isInvariant(final IcfgLocation loc) {
		final Check check = Check.getAnnotation(loc);
		if (check != null) {
			final EnumSet<Spec> specs = check.getSpec();
			// if (specs.size() == 1) {
			return specs.contains(Spec.INVARIANT);
			// } else {
			// throw new UnsupportedOperationException("several specs");
			// }
		}
		return false;
	}

	private boolean isErrorLoc(final IcfgLocation loc) {
		final Check check = Check.getAnnotation(loc);
		return (check != null);
	}

	private boolean isLoopLoc(final IcfgLocation loc) {
		final LoopEntryAnnotation loa = LoopEntryAnnotation.getAnnotation(loc);
		return (loa != null);
	}

	private class TwoPointSubgraphDefinition {
		private final IcfgLocation mStartLocation;
		private final Set<IcfgEdge> mSubgraphEdges;
		private final IcfgLocation mEndLocation;

		public TwoPointSubgraphDefinition(final IcfgLocation startLocation, final Set<IcfgEdge> subgraphEdges,
				final IcfgLocation endLocation) {
			super();
			mStartLocation = startLocation;
			mSubgraphEdges = subgraphEdges;
			mEndLocation = endLocation;
		}

		public IcfgLocation getStartLocation() {
			return mStartLocation;
		}

		public Set<IcfgEdge> getSubgraphEdges() {
			return mSubgraphEdges;
		}

		public IcfgLocation getEndLocation() {
			return mEndLocation;
		}

	}

}
