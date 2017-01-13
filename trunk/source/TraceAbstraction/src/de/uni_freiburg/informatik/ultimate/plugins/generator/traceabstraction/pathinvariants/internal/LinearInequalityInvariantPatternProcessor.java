/*
 * Copyright (C) 2015 David Zschocke
 * Copyright (C) 2015 Dirk Steinmetz
 * Copyright (C) 2015 Matthias Heizmann (heizmann@informatik.uni-freiburg.de)
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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import de.uni_freiburg.informatik.ultimate.core.lib.exceptions.ToolchainCanceledException;
import de.uni_freiburg.informatik.ultimate.core.model.services.ILogger;
import de.uni_freiburg.informatik.ultimate.core.model.services.IToolchainStorage;
import de.uni_freiburg.informatik.ultimate.core.model.services.IUltimateServiceProvider;
import de.uni_freiburg.informatik.ultimate.lassoranker.AffineTerm;
import de.uni_freiburg.informatik.ultimate.lassoranker.AnalysisType;
import de.uni_freiburg.informatik.ultimate.lassoranker.LinearInequality;
import de.uni_freiburg.informatik.ultimate.lassoranker.LinearTransition;
import de.uni_freiburg.informatik.ultimate.lassoranker.ModelExtractionUtils;
import de.uni_freiburg.informatik.ultimate.lassoranker.exceptions.TermException;
import de.uni_freiburg.informatik.ultimate.lassoranker.termination.MotzkinTransformation;
import de.uni_freiburg.informatik.ultimate.logic.AnnotatedTerm;
import de.uni_freiburg.informatik.ultimate.logic.Annotation;
import de.uni_freiburg.informatik.ultimate.logic.ApplicationTerm;
import de.uni_freiburg.informatik.ultimate.logic.LetTerm;
import de.uni_freiburg.informatik.ultimate.logic.Logics;
import de.uni_freiburg.informatik.ultimate.logic.QuotedObject;
import de.uni_freiburg.informatik.ultimate.logic.Rational;
import de.uni_freiburg.informatik.ultimate.logic.Script;
import de.uni_freiburg.informatik.ultimate.logic.Script.LBool;
import de.uni_freiburg.informatik.ultimate.logic.Term;
import de.uni_freiburg.informatik.ultimate.logic.TermTransformer;
import de.uni_freiburg.informatik.ultimate.logic.TermVariable;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.cfg.CfgSmtToolkit;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.cfg.structure.IcfgInternalTransition;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.cfg.structure.IcfgLocation;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.cfg.transitions.TransFormulaBuilder;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.cfg.transitions.UnmodifiableTransFormula;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.cfg.variables.IProgramVar;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.smt.SmtUtils;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.smt.SmtUtils.SimplificationTechnique;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.smt.SmtUtils.XnfConversionTechnique;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.smt.predicates.IPredicate;
import de.uni_freiburg.informatik.ultimate.plugins.generator.traceabstraction.Activator;
import de.uni_freiburg.informatik.ultimate.plugins.generator.traceabstraction.singletracecheck.PredicateUnifier;

/**
 * A {@link IInvariantPatternProcessor} using patterns composed of linear
 * inequalities on a linear approximation of the program.
 * 
 * The outer collection within the invariant pattern type represents a
 * disjunction, the inner one a conjunction. Within the inner conjunction, there
 * are strict and non-strict inequalities. These collections are generated
 * according to a {@link ILinearInequalityInvariantPatternStrategy}.
 * @author David Zschocke, Dirk Steinmetz, Matthias Heizmann, Betim Musa
 */
public final class LinearInequalityInvariantPatternProcessor
extends
AbstractSMTInvariantPatternProcessor<Collection<Collection<AbstractLinearInvariantPattern>>> {
	
	public enum SimplificationType {
		NO_SIMPLIFICATION,
		SIMPLE,
		TWO_MODE
	}

	private static final String PREFIX = "lp_";
	private static final String PREFIX_SEPARATOR = "_";

	private static final String ANNOT_PREFIX = "LIIPP_Annot";
	private int mAnnotTermCounter;
	/**
	 * Stores the mapping from annotation of a term to the original motzkin term. It is used to restore the original terms from the annotations in unsat core.
	 */
	private Map<String, Term> mAnnotTerm2MotzkinTerm;
	/**
	 * @see {@link MotzkinTransformation}.mMotzkinCoefficients2LinearInequalities
	 */
	private Map<String, LinearInequality> mMotzkinCoefficients2LinearInequalities;
	
	/**
	 * TODO:
	 */
	private Map<Set<LinearInequality>, Set<IcfgLocation>> mLinearInequalities2Locations;
	

	private final boolean mUseVarsFromUnsatCore;


	private final IUltimateServiceProvider mServices;
	private final ILogger mLogger;
	private final Script mSolver;
	private final ILinearInequalityInvariantPatternStrategy<Collection<Collection<AbstractLinearInvariantPattern>>> mStrategy;
	private final LinearTransition mPrecondition;
	private final LinearTransition mPostcondition;
	private final CachedTransFormulaLinearizer mLinearizer;

	private Collection<Collection<AbstractLinearInvariantPattern>> mEntryInvariantPattern;
	private Collection<Collection<AbstractLinearInvariantPattern>> mExitInvariantPattern;
	private int mPrefixCounter;
	private int mCurrentRound;
	private final int mMaxRounds;
	private final boolean mUseNonlinearConstraints; 
	private final SimplificationType mSimplifySatisfyingAssignment = SimplificationType.TWO_MODE;

	private Collection<TermVariable> mVarsFromUnsatCore;
	private final IcfgLocation mStartLocation;
	private final IcfgLocation mErrorLocation;
	
	private static final boolean PRINT_CONSTRAINTS = false;
	private static final boolean DEBUG_OUTPUT = false;
	
	/**
	 * Contains all coefficients of all patterns from the current round.
	 */
	private Set<Term> mAllPatternCoefficients;
	/**
	 * If the current constraints are satisfiable, then this map contains the values of the pattern coefficients. 
	 */
	private Map<Term, Rational> mPatternCoefficients2Values;
	private final boolean mUseUnsatCoreForDynamicPatternSettingChanges;



	/**
	 * Creates a pattern processor using linear inequalities as patterns.
	 * 
	 * @param services
	 *            Service provider to use, for example for logging and timeouts
	 * @param predicateUnifier
	 *            the predicate unifier to unify final predicates with
	 * @param csToolkit
	 *            the smt manager to use with the predicateUnifier
	 * @param solver
	 *            SMT solver to use
	 * @param cfg
	 *            the ControlFlowGraph to generate an invariant map on
	 * @param precondition
	 *            the invariant on the {@link ControlFlowGraph#getEntry()} of
	 *            cfg
	 * @param postcondition
	 *            the invariant on the {@link ControlFlowGraph#getExit()} of cfg
	 * @param strategy
	 *            The strategy to generate invariant patterns with
	 * @param useNonlinearConstraints
	 * 			  Kind of constraints that are used to specify invariant.
	 * @param storage 
	 * @param simplicationTechnique 
	 * @param xnfConversionTechnique 
	 * @param axioms 
	 * @param errorLocation 
	 * @param startLocation 
	 */
	public LinearInequalityInvariantPatternProcessor(
			final IUltimateServiceProvider services,
			final IToolchainStorage storage,
			final PredicateUnifier predicateUnifier,
			final CfgSmtToolkit csToolkit, final IPredicate axioms, 
			final Script solver,
			final List<IcfgLocation> locations, final List<IcfgInternalTransition> transitions, final IPredicate precondition,
			final IPredicate postcondition,
			final IcfgLocation startLocation, final IcfgLocation errorLocation, 
			final ILinearInequalityInvariantPatternStrategy<Collection<Collection<AbstractLinearInvariantPattern>>> strategy,
			final boolean useNonlinearConstraints,
			final boolean useUnsatCoreVarsForPatterns,
			final boolean useUnsatCoreForDynamicPatternSettingChanges,
			final SimplificationTechnique simplicationTechnique, 
			final XnfConversionTechnique xnfConversionTechnique) {
		super(predicateUnifier, csToolkit);
		this.mServices = services;
		mLogger = services.getLoggingService().getLogger(
				Activator.PLUGIN_ID);
		this.mSolver = solver;
		this.mStrategy = strategy;
		mStartLocation = startLocation;
		mErrorLocation = errorLocation;


		mLinearizer = new CachedTransFormulaLinearizer(services, 
				csToolkit, axioms, storage, simplicationTechnique, xnfConversionTechnique);
		this.mPrecondition = mLinearizer.linearize(
				TransFormulaBuilder.constructTransFormulaFromPredicate(precondition, csToolkit.getManagedScript()));
		this.mPostcondition = mLinearizer.linearize(
				TransFormulaBuilder.constructTransFormulaFromPredicate(postcondition, csToolkit.getManagedScript()));

		mCurrentRound = -1;
		mMaxRounds = strategy.getMaxRounds();
		mUseNonlinearConstraints = useNonlinearConstraints;
		mUseVarsFromUnsatCore = useUnsatCoreVarsForPatterns;
		mUseUnsatCoreForDynamicPatternSettingChanges = useUnsatCoreForDynamicPatternSettingChanges;
		mAnnotTermCounter = 0;
		mAnnotTerm2MotzkinTerm = new HashMap<>();
		mMotzkinCoefficients2LinearInequalities = new HashMap<>();
		mLinearInequalities2Locations = new HashMap<>();
		mAllPatternCoefficients = null;
		mPatternCoefficients2Values = null;
		
	}
	/**
	 * {@inheritDoc}
	 */
	@Override
	public void startRound(final int round) {
		mSolver.echo(new QuotedObject("Round " + round));
		resetSettings();
		mEntryInvariantPattern = null;
		mExitInvariantPattern = null;
		mPrefixCounter = 0;
		mCurrentRound = round;
		
		mAllPatternCoefficients = new HashSet<>();
		mLinearInequalities2Locations = new HashMap<>();
	}

	/**
	 * Reset the solver and additionally reset the annotation term counter and the map mAnnotTerm2OriginalTerm
	 */
	private void resetSettings() {
		reinitializeSolver();
		// Reset annotation term counter
		mAnnotTermCounter = 0;
		// Reset map that stores the mapping from the annotated term to the original term.
		mAnnotTerm2MotzkinTerm = new HashMap<>();
		// Reset map that stores motzkin coefficients to linear inequalities
		mMotzkinCoefficients2LinearInequalities = new HashMap<>();
		// Reset settings of strategy
		mStrategy.resetSettings();

	}

	/**
	 * Generates a new prefix, which is guaranteed (within prefixes generated
	 * through this method on one single instance) to be unique within the
	 * current round.
	 * 
	 * @return unique prefix (within this instance and round)
	 */
	protected String newPrefix() {
		return PREFIX + (mPrefixCounter++);
	}



	/**
	 * Transforms a pattern into a DNF of linear inequalities relative to a
	 * given mapping of {@link IProgramVar}s involved.
	 * 
	 * @param pattern
	 *            the pattern to transform
	 * @param mapping
	 *            the mapping to use
	 * @return transformed pattern, equivalent to the pattern under the mapping
	 */
	private static Collection<Collection<LinearInequality>> mapPattern(
			final Collection<Collection<AbstractLinearInvariantPattern>> pattern,
			final Map<IProgramVar, Term> mapping) {
		final Collection<Collection<LinearInequality>> result = new ArrayList<>(
				pattern.size());
		for (final Collection<AbstractLinearInvariantPattern> conjunct : pattern) {
			final Collection<LinearInequality> mappedConjunct = new ArrayList<>(
					conjunct.size());
			for (final AbstractLinearInvariantPattern base : conjunct) {
					mappedConjunct.add(base.getLinearInequality(mapping));
				
			}
			result.add(mappedConjunct);
		}
		return result;

	}

	/**
	 * Transforms and negates a pattern into a DNF of linear inequalities
	 * relative to a given mapping of {@link IProgramVar}s involved.
	 * 
	 * @param pattern
	 *            the pattern to transform
	 * @param mapping
	 *            the mapping to use
	 * @return transformed pattern, equivalent to the negated pattern under the
	 *         mapping
	 */
	private static Collection<Collection<LinearInequality>> mapAndNegatePattern(
			final Collection<Collection<AbstractLinearInvariantPattern>> pattern,
			final Map<IProgramVar, Term> mapping) {
		// This is the trivial algorithm (expanding). Feel free to optimize ;)
		// 1. map Pattern, result is dnf
		final Collection<Collection<LinearInequality>> mappedPattern = mapPattern(
				pattern, mapping);
		// 2. negate every LinearInequality, result is a cnf
		final Collection<Collection<LinearInequality>> cnfAfterNegation = new ArrayList<>(
				mappedPattern.size());
		for (final Collection<LinearInequality> conjunct : mappedPattern) {
			final Collection<LinearInequality> disjunctWithNegatedLinearInequalities = new ArrayList<>(
					conjunct.size());
			for (final LinearInequality li : conjunct) {
				// copy original linear inequality
				final LinearInequality negatedLi = new LinearInequality(li);
				negatedLi.negate();
				disjunctWithNegatedLinearInequalities.add(negatedLi);
			}
			cnfAfterNegation.add(disjunctWithNegatedLinearInequalities);
		}
		// 3. expand the cnf to get a dnf
		final Collection<Collection<LinearInequality>> mappedAndNegatedPattern = expandCnfToDnf(cnfAfterNegation);
		assert mappedAndNegatedPattern != null;
		// 4. return the resulting dnf as the solution
		return mappedAndNegatedPattern;
	}



	/**
	 * Transforms a conjunction to an equivalent term representing a disjunction
	 * of the motzkin transformations of the expanded DNF conjuncts.
	 * 
	 * @see #expandConjunction(Collection...)
	 * @see MotzkinTransformation
	 * @param dnfs
	 *            DNFs to conjunct together
	 * @return term equivalent to the negated term
	 */
	@SafeVarargs
	private final Term transformNegatedConjunction(
			final Collection<Collection<LinearInequality>>... dnfs) {
		mLogger.info( "[LIIPP] About to invoke motzkin:");
		for (final Collection<? extends Collection<LinearInequality>> dnf : dnfs) {
			mLogger.info( "[LIIPP] DNF to transform: " + dnf);
		}
		final Collection<Collection<LinearInequality>> conjunctionDNF = expandConjunction(dnfs);

		// Apply Motzkin and generate the conjunction of the resulting Terms
		final Collection<Term> resultTerms = new ArrayList<Term>(
				conjunctionDNF.size());
		final AnalysisType analysisType = mUseNonlinearConstraints ? 
				AnalysisType.NONLINEAR : AnalysisType.LINEAR;
		for (final Collection<LinearInequality> conjunct : conjunctionDNF) {
			mLogger.info( "[LIIPP] Transforming conjunct "
					+ conjunct);
			final MotzkinTransformation transformation = new MotzkinTransformation(
					mSolver, analysisType, !false);
			transformation.add_inequalities(conjunct);
			resultTerms.add(transformation.transform(new Rational[0]));
			mMotzkinCoefficients2LinearInequalities.putAll(transformation.getMotzkinCoefficients2LinearInequalities());
		}
		return SmtUtils.and(mSolver, resultTerms);
	}

	/**
	 * Completes a given mapping by adding fresh auxiliary terms for any
	 * coefficient (see {@link #mPatternCoefficients}) which does not yet have a
	 * mapping.
	 * 
	 * After this method returned, the mapping is guaranteed to contain an entry
	 * for every coefficient.
	 * 
	 * @param mapping
	 *            mapping to add auxiliary terms to
	 */
//	protected void completeMapping(final Map<IProgramVar, Term> mapping, final Set<IProgramVar> patternCoefficients) {
//		final String prefix = newPrefix() + "replace_";
//		int index = 0;
//		for (final IProgramVar coefficient : patternCoefficients) {
//			if (mapping.containsKey(coefficient)) {
//				continue;
//			}
//			final Term replacement = SmtUtils.buildNewConstant(mSolver, prefix
//					+ index++, "Real");
//			mapping.put(coefficient, replacement);
//		}
//	}

	/**
	 * Completes a given mapping by adding auxiliary terms from another mapping
	 * for any coefficient (see {@link #mPatternCoefficients}) which does not yet
	 * have a mapping.
	 * 
	 * After this method returned, the mapping is guaranteed to contain an entry
	 * for every coefficient.
	 * 
	 * @param mapping
	 *            mapping to add auxiliary terms to
	 * @param source
	 *            mapping to get auxiliary terms from, must contain one entry
	 *            for each coefficient
	 */
//	protected void completeMapping(final Map<IProgramVar, Term> mapping,
//			final Map<IProgramVar, Term> source) {
//		for (final IProgramVar coefficient : mPatternCoefficients) {
//			if (mapping.containsKey(coefficient)) {
//				continue;
//			}
//			mapping.put(coefficient, source.get(coefficient));
//		}
//	}

	/**
	 * Generates a {@link Term} that is true iff the given
	 * {@link LinearTransition} implies a given invariant pattern over the
	 * primed variables of the transition.
	 * 
	 * @see #mPrecondition
	 * @see #mPostcondition
	 * @param condition
	 *            transition to build the implication term from, usually a pre-
	 *            or postcondition
	 * @param pattern
	 *            pattern to build the equivalence term from
	 * @return equivalence term
	 */
	private Term buildImplicationTerm(final LinearTransition condition,
			final Collection<Collection<AbstractLinearInvariantPattern>> pattern, final IcfgLocation startLocation,
			final Map<IProgramVar, Term> programVarsRecentlyOccurred) {
		final Map<IProgramVar, Term> primedMapping = new HashMap<IProgramVar, Term>(
				condition.getOutVars());
//		completePatternVariablesMapping(primedMapping, mStrategy.getPatternVariablesForLocation(startLocation, mCurrentRound), programVarsRecentlyOccurred);

		final Collection<List<LinearInequality>> conditionDNF_ = condition
				.getPolyhedra();
		final Collection<Collection<LinearInequality>> conditionDNF = new ArrayList<>();
		for (final List<LinearInequality> list : conditionDNF_) {
			final Collection<LinearInequality> newList = new ArrayList<>();
			newList.addAll(list);
			conditionDNF.add(newList);
		}
		final Collection<Collection<LinearInequality>> negPatternDNF = mapAndNegatePattern(
				pattern, primedMapping);
		int numberOfInequalities = 0;
		for (final Collection<LinearInequality> conjunct : negPatternDNF) {
			numberOfInequalities += conjunct.size();
		}
		mLogger.info( "[LIIPP] Got an implication term with "
				+ numberOfInequalities + " conjuncts");

		return transformNegatedConjunction(conditionDNF, negPatternDNF);
	}

	/**
	 * Generates a {@link Term} that is true iff a given invariant pattern over
	 * the primed variables of the transition implies the given
	 * {@link LinearTransition}.
	 * 
	 * @see #mPrecondition
	 * @see #mPostcondition
	 * @param condition
	 *            transition to build the implication term from, usually a pre-
	 *            or postcondition
	 * @param pattern
	 *            pattern to build the equivalence term from
	 * @return equivalence term
	 */
	private Term buildBackwardImplicationTerm(final LinearTransition condition,
			final Collection<Collection<AbstractLinearInvariantPattern>> pattern, final IcfgLocation errorLocation,
			final Map<IProgramVar, Term> programVarsRecentlyOccurred) {
		final Map<IProgramVar, Term> primedMapping = new HashMap<IProgramVar, Term>(
				condition.getOutVars());
//		completePatternVariablesMapping(primedMapping, mStrategy.getPatternVariablesForLocation(errorLocation, mCurrentRound), programVarsRecentlyOccurred);

		final Collection<List<LinearInequality>> conditionCNF_ = condition
				.getPolyhedra();
		final Collection<Collection<LinearInequality>> conditionCNF = new ArrayList<>();
		for (final List<LinearInequality> list : conditionCNF_) {
			final ArrayList<LinearInequality> newList = new ArrayList<>();
			for (final LinearInequality li : list) {
				final LinearInequality newLi = new LinearInequality(li);
				newLi.negate();
				newList.add(newLi);
			}
			conditionCNF.add(newList);
		}
		final Collection<Collection<LinearInequality>> newConditionDNF = expandCnfToDnf(conditionCNF);

		final Collection<Collection<LinearInequality>> PatternDNF = mapPattern(
				pattern, primedMapping);
		int numberOfInequalities = 0;
		for (final Collection<LinearInequality> conjunct : PatternDNF) {
			numberOfInequalities += conjunct.size();
		}
		mLogger.info( "[LIIPP] Got an implication term with "
				+ numberOfInequalities + " conjuncts");

		return transformNegatedConjunction(newConditionDNF, PatternDNF);
	}
	
	private void completePatternVariablesMapping(final Map<IProgramVar, Term> mapToComplete, final Set<IProgramVar> varsShouldBeInMap, final Map<IProgramVar, Term> mapToCompleteWith) {
		final String prefix = newPrefix() + "replace_";
		int index = 0;
		if (DEBUG_OUTPUT) {
			mLogger.info("Var-Map before completion: " + mapToComplete);
		}
		for (final IProgramVar var : varsShouldBeInMap) {
			if (!mapToComplete.containsKey(var)) {
				if (mapToCompleteWith.get(var) != null) {
					mapToComplete.put(var, mapToCompleteWith.get(var));
				} else {
					final Term replacement = SmtUtils.buildNewConstant(mSolver, prefix + var + "_" + (index++), "Real");
					mapToComplete.put(var, replacement);
					mapToCompleteWith.put(var, replacement);
				}
			}
		}
		if (DEBUG_OUTPUT) {
			mLogger.info("Var-Map after completion: " + mapToComplete);
		}
	}

	/**
	 * Generates a {@link Term} that is true iff the given
	 * {@link InvariantTransitionPredicate} holds.
	 * 
	 * @param predicate
	 *            the predicate to build the term from
	 * @return term true iff the given predicate holds
	 */
	private Term buildPredicateTerm(
			final InvariantTransitionPredicate<Collection<Collection<AbstractLinearInvariantPattern>>> predicate,
			final Map<IProgramVar, Term> programVarsRecentlyOccurred) {
		final LinearTransition transition = mLinearizer.linearize(predicate.getTransition());
		final Map<IProgramVar, Term> unprimedMapping = new HashMap<IProgramVar, Term>(
				transition.getInVars());
		programVarsRecentlyOccurred.putAll(unprimedMapping);
//		completeMapping(unprimedMapping);
		completePatternVariablesMapping(unprimedMapping, predicate.getVariablesForSourcePattern(),
				programVarsRecentlyOccurred);
		
		final Map<IProgramVar, Term> primedMapping = new HashMap<IProgramVar, Term>(
				transition.getOutVars());
		programVarsRecentlyOccurred.putAll(primedMapping);
		completePatternVariablesMapping(primedMapping, predicate.getVariablesForTargetPattern(),
				programVarsRecentlyOccurred);
		if (DEBUG_OUTPUT) {
			mLogger.info("Size of start-pattern before mapping to lin-inequalities: " + getSizeOfPattern(predicate.getInvStart()));
		}
		final Collection<Collection<LinearInequality>> startInvariantDNF = mapPattern(
				predicate.getInvStart(), unprimedMapping);
		if (mUseUnsatCoreForDynamicPatternSettingChanges) {
			final Set<LinearInequality> lineqsFromStartPattern = new HashSet<>(startInvariantDNF.size());
			for (final Collection<LinearInequality> conjunct : startInvariantDNF) {
				lineqsFromStartPattern.addAll(conjunct);
			}
			final Set<IcfgLocation> loc =  new HashSet<IcfgLocation>();
			loc.add(predicate.getSourceLocation());
			mLinearInequalities2Locations.put(lineqsFromStartPattern, loc);
		}
		if (DEBUG_OUTPUT) {
			mLogger.info("Size of start-pattern after mapping to lin-inequalities: " + getSizeOfPattern(startInvariantDNF));
			mLogger.info("Size of end-pattern before mapping to lin-inequalities: " + getSizeOfPattern(predicate.getInvEnd()));
		}

		final Collection<Collection<LinearInequality>> endInvariantDNF = mapAndNegatePattern(
				predicate.getInvEnd(), primedMapping);
		if (DEBUG_OUTPUT) {
			mLogger.info("Size of end-pattern after mapping to lin-inequalities and negating: " + getSizeOfPattern(endInvariantDNF));
		}
		if (mUseUnsatCoreForDynamicPatternSettingChanges) {
			final Set<LinearInequality> lineqsFromEndPattern = new HashSet<>(endInvariantDNF.size());
			for (final Collection<LinearInequality> conjunct : endInvariantDNF) {
				lineqsFromEndPattern.addAll(conjunct);
			}
			final Set<IcfgLocation> loc =  new HashSet<IcfgLocation>();
			loc.add(predicate.getTargetLocation());
			mLinearInequalities2Locations.put(lineqsFromEndPattern, loc);
		}
		final Collection<List<LinearInequality>> transitionDNF_ = transition
				.getPolyhedra();
		final Collection<Collection<LinearInequality>> transitionDNF = new ArrayList<>();
		for (final List<LinearInequality> list : transitionDNF_) {
			final Collection<LinearInequality> newList = new ArrayList<>();
			newList.addAll(list);
			transitionDNF.add(newList);
		}
		if (mUseUnsatCoreForDynamicPatternSettingChanges) {
			final Set<LinearInequality> lineqsFromTransition = new HashSet<>(transitionDNF.size());
			for (final Collection<LinearInequality> conjunct : transitionDNF) {
				lineqsFromTransition.addAll(conjunct);
			}
			final Set<IcfgLocation> loc =  new HashSet<IcfgLocation>();
			loc.add(predicate.getSourceLocation());
			loc.add(predicate.getTargetLocation());
			mLinearInequalities2Locations.put(lineqsFromTransition, loc);
		}
		if (PRINT_CONSTRAINTS) {
			final StringBuilder sb = new StringBuilder();
			sb.append("\nStartPattern: ");
			startInvariantDNF.forEach(disjunct -> disjunct.forEach(lineq -> sb.append(lineq.toString() + " AND ")));
			sb.append("\nTransition: ");
			transitionDNF.forEach(dis -> dis.forEach(lineq -> sb.append(lineq.toString() + " AND ")));
//			sb.append(" AND "); 
			sb.append("\nEndPattern: ");
			endInvariantDNF.forEach(disjunct -> disjunct.forEach(lineq -> sb.append(lineq.toString() + " AND ")));
			mLogger.info(sb.toString());
		}
		// Respect timeout / toolchain cancellation
		if (!mServices.getProgressMonitorService().continueProcessing()) {
			throw new ToolchainCanceledException(LinearInequalityInvariantPatternProcessor.class);
		}

		return transformNegatedConjunction(startInvariantDNF, endInvariantDNF,
				transitionDNF);
	}
	
	/**
	 * Generate constraints for invariant template as follows:
	 * 1. Generate a constraint s.t. the precondition implies the invariant template.
	 * 2. Generate for each predicate in predicates a constraint.
	 * 3. Generate a constraint s.t. the invariant template implies the post-condition.
	 * @param predicates - represent the transitions of the path program
	 * @author Betim Musa (musab@informatik.uni-freiburg.de)
	 */
	private void generateAndAssertTerms(final Collection<InvariantTransitionPredicate<Collection<Collection<AbstractLinearInvariantPattern>>>> predicates) {
		/**
		 * Maps program vars to their recent occurrence in the program
		 */
		final Map<IProgramVar, Term> programVarsRecentlyOccurred = new HashMap<>();
		mSolver.assertTerm(buildImplicationTerm(mPrecondition,
				mEntryInvariantPattern, mStartLocation, programVarsRecentlyOccurred));
		mSolver.assertTerm(buildBackwardImplicationTerm(mPostcondition,
				mExitInvariantPattern, mErrorLocation, programVarsRecentlyOccurred));

		for (final InvariantTransitionPredicate<Collection<Collection<AbstractLinearInvariantPattern>>> predicate : predicates) {
			mSolver.assertTerm(buildPredicateTerm(predicate, programVarsRecentlyOccurred));
		}
	}

	/**
	 * Split the given term in its conjunctions, annotate and assert each conjunction one by one, 
	 * and store the mapping annotated term -> original term in a map.
	 * @param term - the Term to be annotated and asserted
	 * @author Betim Musa (musab@informaitk.uni-freiburg.de)
	 */
	private void annotateAndAssertTermAndStoreMapping(final Term term) {
		assert term.getFreeVars().length == 0 : "Term has free vars";
		// Annotate and assert the conjuncts of the term one by one 
		final Term[] conjunctsOfTerm = SmtUtils.getConjuncts(term);
		final String termAnnotName = ANNOT_PREFIX + PREFIX_SEPARATOR + (mAnnotTermCounter++);
		for (int conjunctCounter = 0; conjunctCounter < conjunctsOfTerm.length; conjunctCounter++) {
			// Generate unique name for this term
			final String conjunctAnnotName = termAnnotName + PREFIX_SEPARATOR + (conjunctCounter); 
			// Store mapping termAnnotName -> original term
			mAnnotTerm2MotzkinTerm.put(conjunctAnnotName, conjunctsOfTerm[conjunctCounter]);

			final Annotation annot = new Annotation(":named", conjunctAnnotName);
			final Term annotTerm = mSolver.annotate(conjunctsOfTerm[conjunctCounter], annot);
			mSolver.assertTerm(annotTerm);
		}
	}

	/**
	 * Generate constraints for invariant template as follows:
	 * 1. Generate a constraint s.t. the precondition implies the invariant template.
	 * 2. Generate for each predicate in predicates a constraint.
	 * 3. Generate a constraint s.t. the invariant template implies the post-condition.
	 * @param predicates - represent the transitions of the path program
	 * @author Betim Musa (musab@informatik.uni-freiburg.de)
	 */
	private void generateAndAnnotateAndAssertTerms(final Collection<InvariantTransitionPredicate<Collection<Collection<AbstractLinearInvariantPattern>>>> predicates) {
		/**
		 * Maps program vars to their recent occurrence in the program
		 */
		final Map<IProgramVar, Term> programVarsRecentlyOccurred = new HashMap<>();
		// Generate and assert term for precondition
		annotateAndAssertTermAndStoreMapping(buildImplicationTerm(mPrecondition, mEntryInvariantPattern, mStartLocation, programVarsRecentlyOccurred));
		// Generate and assert term for post-condition
		annotateAndAssertTermAndStoreMapping(buildBackwardImplicationTerm(mPostcondition, mExitInvariantPattern, mErrorLocation, programVarsRecentlyOccurred));
		
		// Generate and assert terms for intermediate transitions
		for (final InvariantTransitionPredicate<Collection<Collection<AbstractLinearInvariantPattern>>> predicate : predicates) {
			annotateAndAssertTermAndStoreMapping(buildPredicateTerm(predicate, programVarsRecentlyOccurred));
		}
	}
	/**
	 * Extract the Motzkin coefficients from the given term.
	 * @param t - term the Motzkin coefficients to be extracted from
	 * @return
	 * @author Betim Musa (musab@informatik.uni-freiburg.de)
	 */
	private Set<String> getTermVariablesFromTerm(final Term t) {
		final HashSet<String> result = new HashSet<>();
		if (t instanceof ApplicationTerm) {
			if (((ApplicationTerm)t).getFunction().getName().startsWith("motzkin_")) {
				result.add(((ApplicationTerm)t).getFunction().getName());
				return result;
			} else {
				final Term[] subterms = ((ApplicationTerm)t).getParameters();
				for (final Term st : subterms) {
					result.addAll(getTermVariablesFromTerm(st));
				}
			}
		} else if (t instanceof AnnotatedTerm) {
			final Term subterm = ((AnnotatedTerm)t).getSubterm();
			result.addAll(getTermVariablesFromTerm(subterm));
		} else if (t instanceof LetTerm) {
			final Term subterm = ((LetTerm)t).getSubTerm();
			result.addAll(getTermVariablesFromTerm(subterm));
		} else if (t instanceof TermVariable) {
			//			result.add((TermVariable)t);
		}
		return result;
	}

	/**
	 * @author Betim Musa (musab@informatik.uni-freiburg.de)
	 * @return
	 */
	public Collection<TermVariable> getVarsFromUnsatCore() {
		return mVarsFromUnsatCore;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean hasValidConfiguration(
			final Collection<InvariantTransitionPredicate<Collection<Collection<AbstractLinearInvariantPattern>>>> predicates,
			final int round) {
		mLogger.info( "[LIIPP] Start generating terms.");

		if (!mUseVarsFromUnsatCore) {
			generateAndAssertTerms(predicates);
		} else {
			generateAndAnnotateAndAssertTerms(predicates);
		}

		mLogger.info( "[LIIPP] Terms generated, checking SAT.");
		final LBool result = mSolver.checkSat();
		mLogger.info("Check-sat result: " + result);
		if (result == LBool.UNKNOWN) {
//			mLogger.info("Got \"UNKNOWN\" for last check-sat, give up the invariant search.");
//			// Prevent additional rounds
//			mMaxRounds = mCurrentRound + 1;
		}
		if (result != LBool.SAT) {
			// No configuration found
			if (result == LBool.UNSAT && mUseVarsFromUnsatCore) {
				// Extract the variables from the unsatisfiable core by 
				// first extracting the motzkin variables and then using them
				// to get the corresponding program variables 
				final Term[] unsatCoreAnnots = mSolver.getUnsatCore();
				final Set<String> motzkinVariables = new HashSet<>();
				for (final Term t : unsatCoreAnnots) {
					final Term origMotzkinTerm = mAnnotTerm2MotzkinTerm.get(t.toStringDirect());
					motzkinVariables.addAll(getTermVariablesFromTerm(origMotzkinTerm));
				}
				if (DEBUG_OUTPUT) {
					mLogger.info("UnsatCoreAnnots: " + Arrays.toString(unsatCoreAnnots));
					mLogger.info("MotzkinVars in unsat core: " + motzkinVariables);
				}
				mVarsFromUnsatCore = new HashSet<>();
				final Set<IcfgLocation> locsInUnsatCore = new HashSet<>();
				final Map<IcfgLocation, Integer> locs2Frequency = new HashMap<>();
				for (final String motzkinVar : motzkinVariables) {
					final LinearInequality linq = mMotzkinCoefficients2LinearInequalities.get(motzkinVar);
					for (final Term varInLinq : linq.getVariables()) {
						if (varInLinq instanceof TermVariable) {
							mVarsFromUnsatCore.add((TermVariable)varInLinq);
						} 
//						else if (varInLinq instanceof ApplicationTerm) {
//							
//						} else {
//							throw new UnsupportedOperationException("Var in linear inequality is neither a TermVariable nor a Replacement-Variable.");
//						}

					}
					
					if (mUseUnsatCoreForDynamicPatternSettingChanges && round >= 0) {
						final Set<Set<LinearInequality>> setsContainingLinq = mLinearInequalities2Locations.keySet().stream().filter(key -> key.contains(linq)).collect(Collectors.toSet());
						for (final Set<LinearInequality> s : setsContainingLinq) {
							final Set<IcfgLocation> locs = mLinearInequalities2Locations.get(s);
							locsInUnsatCore.addAll(locs);
							for (final IcfgLocation loc : locs) {
								if (locs2Frequency.containsKey(loc)) {
									Integer i = locs2Frequency.get(loc);
									locs2Frequency.put(loc, ++i);
								} else {
									locs2Frequency.put(loc, new Integer(0));
								}
							}
						}
					}
				}
				if (mUseUnsatCoreForDynamicPatternSettingChanges && round >= 0) {
					locsInUnsatCore.remove(mStartLocation);
					locsInUnsatCore.remove(mErrorLocation);
					for (final IcfgLocation loc : locsInUnsatCore) {
						mStrategy.changePatternSettingForLocation(loc);
						mLogger.info("changed setting for loc: " + loc);
					}
					mLogger.info("locsInUnsatCore2Frequency:" + locs2Frequency);
				}
			}
			mLogger.info( "[LIIPP] No solution found.");
			return false;
		}

		mLogger.info( "[LIIPP] Solution found!");

		return true;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public int getMaxRounds() {
		return mMaxRounds;
	}

	/**
	 * {@inheritDoc}
	 */
//	@Override
//	protected Term getTermForPattern(
//			final Collection<Collection<AbstractLinearInvariantPattern>> pattern) {
//		final Map<IProgramVar, Term> definitionMap = new HashMap<IProgramVar, Term>(
//				mPatternCoefficients.size());
//		for (final IProgramVar coefficient : mPatternCoefficients) {
//			final Term definition = ReplacementVarUtils.getDefinition(coefficient);
//			definitionMap.put(coefficient, definition);
//		}
//
//		final Collection<Term> conjunctions = new ArrayList<Term>(
//				pattern.size());
//		for (final Collection<AbstractLinearInvariantPattern> conjunct : pattern) {
//			final Collection<Term> inequalities = new ArrayList<Term>(
//					conjunct.size());
//			for (final AbstractLinearInvariantPattern inequality : conjunct) {
//				inequalities.add(inequality.getLinearInequality(definitionMap)
//						.asTerm(mSolver));
//			}
//			conjunctions.add(SmtUtils.and(mSolver, inequalities));
//		}
//		return SmtUtils.or(mSolver, conjunctions);
//	}

	/**
	 * Takes a pattern and generates a term with the csToolkit.getScript()
	 * script where the variables are valuated with the values in this.valuation
	 * @param pattern the pattern for which the term is generated
	 * @return a term corresponding to the cnf of LinearInequalites of
	 * the pattern, valuated with the values from this.valuation
	 */
	protected Term getValuatedTermForPattern(
			final Collection<Collection<AbstractLinearInvariantPattern>> pattern) {
		assert mPatternCoefficients2Values != null : "Call method extractValuesForPatternCoefficients"
				+ " before applying configurations for the patterns.";
		final Script script = mCsToolkit.getManagedScript().getScript();
		final Collection<Term> conjunctions = new ArrayList<Term>(
				pattern.size());
		for (final Collection<AbstractLinearInvariantPattern> conjunct : pattern) {
			final Collection<Term> inequalities = new ArrayList<Term>(
					conjunct.size());
			for (final AbstractLinearInvariantPattern inequality : conjunct) {
				final Map<Term, Rational> valuation = new HashMap<>(inequality.getCoefficients().size());
				for (final Term coeff : inequality.getCoefficients()) {
					valuation.put(coeff, mPatternCoefficients2Values.get(coeff));
				}
				final Term affineFunctionTerm = inequality.getAffineFunction(
						valuation).asTerm(script);
				if (inequality.isStrict()) {
					inequalities.add(SmtUtils.less(script, 
							constructZero(script, affineFunctionTerm.getSort()),
							affineFunctionTerm));
				} else {
					inequalities.add(SmtUtils.leq(script,
							constructZero(script, affineFunctionTerm.getSort()),
							affineFunctionTerm));
				}
			}
			conjunctions.add(SmtUtils.and(mCsToolkit.getManagedScript().getScript(), inequalities));
		}
		return SmtUtils.or(mCsToolkit.getManagedScript().getScript(), conjunctions);
	}

//	private static Term constructZero(final Script script, final Sort sort) {
//		if (sort.getRealSort().getName().equals("Int")) {
//			return script.numeral(BigInteger.ZERO);
//		} else if (sort.getRealSort().getName().equals("Real")) {
//			return script.decimal(BigDecimal.ZERO);
//		} else {
//			throw new IllegalArgumentException("unsupported sort " + sort);
//		}
//	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected TermTransformer getConfigurationTransformer() {
		throw new UnsupportedOperationException(
				"not needed, we directly extract Term, Rational mappings");
	}

	/**
	 * Reset solver and initialize it afterwards. For initializing, we set the
	 * option produce-models to true (this allows us to obtain a satisfying
	 * assignment) and we set the logic to QF_AUFNIRA. TODO: Matthias unsat
	 * cores might be helpful for debugging.
	 */
	private void reinitializeSolver() {
		mSolver.reset();
		mSolver.setOption(":produce-models", true);
		if (mUseVarsFromUnsatCore) {
			mSolver.setOption(":produce-unsat-cores", true);
		}
		
		final Logics logic;
		if (mUseNonlinearConstraints) {
			logic = Logics.QF_NRA;
		} else {
			logic = Logics.QF_LRA;
		}
		mSolver.setLogic(logic);
	}

	@Override
	public IPredicate applyConfiguration(
			final Collection<Collection<AbstractLinearInvariantPattern>> pattern) {
		final Term term = getValuatedTermForPattern(pattern);
		return predicateUnifier.getOrConstructPredicate(term);
	}
	
	/**
	 * {@inheritDoc}
	 */
	@Override
	public Collection<Collection<AbstractLinearInvariantPattern>> getInvariantPatternForLocation(final IcfgLocation location,
			final int round) {
		
		if (mStartLocation.equals(location)) {
			assert mEntryInvariantPattern != null : "call initializeEntryAndExitPattern() before this";
			return mEntryInvariantPattern;
		} else if (mErrorLocation.equals(location)) {
			assert mExitInvariantPattern != null : "call initializeEntryAndExitPattern() before this";
			return mExitInvariantPattern;
		} else {

			final Collection<Collection<AbstractLinearInvariantPattern>> p = mStrategy.getInvariantPatternForLocation(location, round, mSolver, newPrefix());
			if (DEBUG_OUTPUT) {	mLogger.info("InvariantPattern for Location " + location + " is:  " + getSizeOfPattern(p)); }
			// Add the coefficients of this pattern to the set of all coefficients
			mAllPatternCoefficients.addAll(mStrategy.getPatternCoefficientsForLocation(location));
			return p;
		}
	}
	
	@Override
	public Collection<Collection<AbstractLinearInvariantPattern>> getInvariantPatternForLocation(final IcfgLocation location,
			final int round, final Set<IProgramVar> vars) {
		
		if (mStartLocation.equals(location)) {
			assert mEntryInvariantPattern != null : "call initializeEntryAndExitPattern() before this";
			return mEntryInvariantPattern;
		} else if (mErrorLocation.equals(location)) {
			assert mExitInvariantPattern != null : "call initializeEntryAndExitPattern() before this";
			return mExitInvariantPattern;
		} else {

			final Collection<Collection<AbstractLinearInvariantPattern>> p = mStrategy.getInvariantPatternForLocation(location, round, mSolver, newPrefix(), vars);
			if (DEBUG_OUTPUT) {	mLogger.info("InvariantPattern for Location " + location + " is:  " + getSizeOfPattern(p)); }
			// Add the coefficients of this pattern to the set of all coefficients
			mAllPatternCoefficients.addAll(mStrategy.getPatternCoefficientsForLocation(location));
			return p;
		}
	}
	
	
	
	@Override
	public final Set<IProgramVar> getVariablesForInvariantPattern(final IcfgLocation location, final int round) {
		if (mStartLocation.equals(location)) {
			return Collections.emptySet();
		} else if (mErrorLocation.equals(location)) {
			return Collections.emptySet();
		} else {
			return mStrategy.getPatternVariablesForLocation(location, round);
		}
	}
	
	
	
	@Override
	public void initializeEntryAndExitPattern() {
		// entry invariant pattern should be equivalent to true, so we create an empty conjunction
		final Collection<AbstractLinearInvariantPattern> emptyConjunction = Collections.emptyList();
		mEntryInvariantPattern = Collections.singleton(emptyConjunction);
		
		// exit pattern is equivalent to false, we create an empty disjunction
		mExitInvariantPattern = Collections.emptyList();
	}
	@Override		
	public Collection<Collection<AbstractLinearInvariantPattern>> getEntryInvariantPattern() {
		return mEntryInvariantPattern;
	}
	@Override	
	public Collection<Collection<AbstractLinearInvariantPattern>> getExitInvariantPattern() {
		return mExitInvariantPattern;
	}
	@Override
	public Collection<Collection<AbstractLinearInvariantPattern>> getEmptyInvariantPattern() {
		final Collection<Collection<AbstractLinearInvariantPattern>> emptyInvPattern;
		// empty invariant pattern should be equivalent to true, so we create an empty conjunction
		final Collection<AbstractLinearInvariantPattern> emptyConjunction = Collections.emptyList();
		emptyInvPattern = Collections.singleton(emptyConjunction);
		return emptyInvPattern;
	}
	
	
	@Override
	public Collection<Collection<AbstractLinearInvariantPattern>> addTransFormulaToEachConjunctInPattern(final Collection<Collection<AbstractLinearInvariantPattern>> pattern, 
			final UnmodifiableTransFormula tf) {
		assert pattern != null : "pattern must not be null";
		assert tf != null : "TransFormula must  not be null";
		if (DEBUG_OUTPUT) { mLogger.info("Size of pattern before adding WP: " + getSizeOfPattern(pattern)); }
		final Collection<Collection<AbstractLinearInvariantPattern>> transFormulaAsLinIneqs = convertTransFormulaToPatternsForLinearInequalities(tf);
		final Collection<Collection<AbstractLinearInvariantPattern>> result = new ArrayList<>();
		// Add conjuncts from transformula to each disjunct of the pattern as additional conjuncts
		for (final Collection<AbstractLinearInvariantPattern> conjunctsInPattern : pattern) {
			for (final Collection<AbstractLinearInvariantPattern> conjunctsInTransFormula : transFormulaAsLinIneqs) {
				final Collection<AbstractLinearInvariantPattern> newConjuncts = new ArrayList<>();
				newConjuncts.addAll(conjunctsInPattern);
				newConjuncts.addAll(conjunctsInTransFormula);
				result.add(newConjuncts);
			}
			
		}
		if (DEBUG_OUTPUT) { mLogger.info("Size of pattern after adding WP: " + getSizeOfPattern(result));}
		return result;
	}
	
	private <E> String getSizeOfPattern(final Collection<Collection<E>> pattern) {
		int totalNumOfConjuncts = 0;
		final int[] conjunctsEachDisjunct = new int[pattern.size()];
		int totalNumOfDisjuncts = 0;
		for (final Collection<?> conjuncts : pattern) {
			totalNumOfConjuncts += conjuncts.size();
			conjunctsEachDisjunct[totalNumOfDisjuncts] = conjuncts.size();
			totalNumOfDisjuncts++;
		}
		return  totalNumOfDisjuncts + " disjuncts with each " + Arrays.toString(conjunctsEachDisjunct) + " conjuncts (Total: " + totalNumOfConjuncts + " cojuncts)";
	}
	
	
	private Collection<Collection<AbstractLinearInvariantPattern>> convertTransFormulaToPatternsForLinearInequalities(final UnmodifiableTransFormula tf) {
		final Map<Term, IProgramVar> termVariables2ProgramVars = new HashMap<>();
		termVariables2ProgramVars.putAll(tf.getInVars().entrySet().stream().collect(Collectors.toMap(Map.Entry::getValue, Map.Entry::getKey)));
		termVariables2ProgramVars.putAll(tf.getOutVars().entrySet().stream().collect(Collectors.toMap(Map.Entry::getValue, Map.Entry::getKey)));
		
		// Transform the transformula into a disjunction of conjunctions, where each conjunct is a LinearInequality
		final List<List<LinearInequality>> linearinequalities = mLinearizer.linearize(tf).getPolyhedra();
		final Collection<Collection<AbstractLinearInvariantPattern>> result = new ArrayList<>(linearinequalities.size());
		for (final List<LinearInequality> lineqs : linearinequalities) {
			final Collection<AbstractLinearInvariantPattern> conjunctsFromTransFormula = new ArrayList<AbstractLinearInvariantPattern>(linearinequalities.size());
			for (final LinearInequality lineq : lineqs) {
				final Map<IProgramVar, AffineTerm> programVarsToConstantCoefficients = new HashMap<>();
				for (final Term termVar : lineq.getVariables()) {
					programVarsToConstantCoefficients.put(termVariables2ProgramVars.get(termVar), lineq.getCoefficient(termVar));
				}
				final LinearPatternWithConstantCoefficients lb = new LinearPatternWithConstantCoefficients(mSolver, programVarsToConstantCoefficients.keySet(), newPrefix(), lineq.isStrict(), 
						programVarsToConstantCoefficients, lineq.getConstant());
				
				conjunctsFromTransFormula.add(lb);
			}
			result.add(conjunctsFromTransFormula);
		}
		return result;
	}
	
	
	@Override
	public Collection<Collection<AbstractLinearInvariantPattern>> addTransFormulaAsAdditionalDisjunctToPattern(final Collection<Collection<AbstractLinearInvariantPattern>> pattern, 
			final UnmodifiableTransFormula tf) {
		assert pattern != null : "pattern must not be null";
		assert tf != null : "TransFormula must  not be null";
		final Collection<Collection<AbstractLinearInvariantPattern>> transFormulaAsLinIneqs = convertTransFormulaToPatternsForLinearInequalities(tf);
		final Collection<Collection<AbstractLinearInvariantPattern>> result = new ArrayList<>();
		
		result.addAll(pattern);
		// Add conjuncts from transformula as additional disjuncts
		result.addAll(transFormulaAsLinIneqs);
		return result;
	}
	
	
	@Override
	public void extractValuesForPatternCoefficients() {
		assert mAllPatternCoefficients != null : "mAllPatternCoefficients must not be null!";
		try {
			if (mSimplifySatisfyingAssignment == SimplificationType.NO_SIMPLIFICATION) {
				mPatternCoefficients2Values = ModelExtractionUtils.getValuation(mSolver, mAllPatternCoefficients);
			} else if (mSimplifySatisfyingAssignment == SimplificationType.SIMPLE) {
				mPatternCoefficients2Values = ModelExtractionUtils.getSimplifiedAssignment(
						mSolver, mAllPatternCoefficients, mLogger, mServices);
			} else {
				mPatternCoefficients2Values = ModelExtractionUtils.getSimplifiedAssignment_TwoMode(
						mSolver, mAllPatternCoefficients, mLogger, mServices);
			}
		}
		catch (final TermException e) {
			e.printStackTrace();
			mLogger.error("Getting values for coefficients failed.");
		}
	}
}
