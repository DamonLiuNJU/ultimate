/*
 * Copyright (C) 2012-2014 University of Freiburg
 *
 * This file is part of the ULTIMATE LassoRanker Library.
 *
 * The ULTIMATE LassoRanker Library is free software: you can redistribute it
 * and/or modify it under the terms of the GNU Lesser General Public License
 * as published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * The ULTIMATE LassoRanker Library is distributed in the hope that it will
 * be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with the ULTIMATE LassoRanker Library. If not,
 * see <http://www.gnu.org/licenses/>.
 * 
 * Additional permission under GNU GPL version 3 section 7:
 * If you modify the ULTIMATE LassoRanker Library, or any covered work, by
 * linking or combining it with Eclipse RCP (or a modified version of
 * Eclipse RCP), containing parts covered by the terms of the Eclipse Public
 * License, the licensors of the ULTIMATE LassoRanker Library grant you
 * additional permission to convey the resulting work.
 */
package de.uni_freiburg.informatik.ultimate.plugins.analysis.lassoranker;

import java.io.FileNotFoundException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.apache.log4j.Logger;

import de.uni_freiburg.informatik.ultimate.core.api.UltimateServices;
import de.uni_freiburg.informatik.ultimate.logic.Rational;
import de.uni_freiburg.informatik.ultimate.logic.SMTLIBException;
import de.uni_freiburg.informatik.ultimate.logic.Script;
import de.uni_freiburg.informatik.ultimate.logic.Script.LBool;
import de.uni_freiburg.informatik.ultimate.logic.Term;
import de.uni_freiburg.informatik.ultimate.logic.Util;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.boogie.Boogie2SMT;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.boogie.TransFormula;
import de.uni_freiburg.informatik.ultimate.plugins.analysis.lassoranker.exceptions.TermException;
import de.uni_freiburg.informatik.ultimate.plugins.analysis.lassoranker.preprocessors.DNF;
import de.uni_freiburg.informatik.ultimate.plugins.analysis.lassoranker.preprocessors.PreProcessor;
import de.uni_freiburg.informatik.ultimate.plugins.analysis.lassoranker.preprocessors.RemoveNegation;
import de.uni_freiburg.informatik.ultimate.plugins.analysis.lassoranker.preprocessors.RewriteArrays;
import de.uni_freiburg.informatik.ultimate.plugins.analysis.lassoranker.preprocessors.RewriteBooleans;
import de.uni_freiburg.informatik.ultimate.plugins.analysis.lassoranker.preprocessors.RewriteDivision;
import de.uni_freiburg.informatik.ultimate.plugins.analysis.lassoranker.preprocessors.RewriteEquality;
import de.uni_freiburg.informatik.ultimate.plugins.analysis.lassoranker.preprocessors.RewriteIte;
import de.uni_freiburg.informatik.ultimate.plugins.analysis.lassoranker.preprocessors.RewriteStrictInequalities;
import de.uni_freiburg.informatik.ultimate.plugins.analysis.lassoranker.preprocessors.RewriteTrueFalse;
import de.uni_freiburg.informatik.ultimate.plugins.analysis.lassoranker.templates.RankingFunctionTemplate;


/**
 * This is the class that controls LassoRanker's (non-)termination argument
 * synthesis.
 * 
 * Tools that use LassoRanker as a library probably want to use this class
 * as an interface for invoking LassoRanker. This class can also be derived
 * for a more fine-grained control over the synthesis process.
 * 
 * @author Jan Leike
 */
public class LassoRankerTerminationAnalysis {
	private static Logger s_Logger =
			UltimateServices.getInstance().getLogger(Activator.s_PLUGIN_ID);
	
	/**
	 * Stem formula of the linear lasso program
	 */
	private TransFormula m_stem_transition;
	
	/**
	 * Loop formula of the linear lasso program
	 */
	private final TransFormula m_loop_transition;
		
	/**
	 * Stem formula of the linear lasso program as linear inequalities in DNF
	 */
	private LinearTransition m_stem;
	
	/**
	 * Loop formula of the linear lasso program as linear inequalities in DNF
	 */
	private LinearTransition m_loop;
	
	/**
	 * The RankVarFactory used by the preprocessors and the RankVarCollector
	 */
	private final VarFactory m_rankVarFactory;
	
	/**
	 * SMT script that created the transition formulae
	 */
	protected final Script m_old_script;
	
	/**
	 * The axioms regarding the transitions' constants
	 */
	protected final Term[] m_axioms;
	
	/**
	 * Number of supporting invariants generated by the last termination
	 * analysis
	 */
	private int m_numSIs = 0;
	
	/**
	 * Number of Motzkin's Theorem applications in the last termination
	 * analysis
	 */
	private int m_numMotzkin = 0;
	
	/**
	 * The current preferences
	 */
	protected final Preferences m_preferences;
	
	/**
	 * Set of terms in that preprocessors (e.g. RewriteArrays) put 
	 * supporting invariants that they discovered. 
	 */
	protected final Set<Term> m_SupportingInvariantsDiscoveredByPreprocessors;

	private final Boogie2SMT m_Boogie2SMT;
	
	/**
	 * Constructor for the LassoRanker interface. Calling this invokes the
	 * preprocessor on the stem and loop formula.
	 * 
	 * If the stem is null, the stem has to be added separately by calling
	 * addStem().
	 * 
	 * @param script the SMT script used to construct the transition formulae
	 * @param boogie2smt the boogie2smt object that created the TransFormula's
	 * @param stem a transition formula corresponding to the lasso's stem
	 * @param loop a transition formula corresponding to the lasso's loop
	 * @param axioms a collection of axioms regarding the transitions' constants
	 * @param preferences configuration options for this plugin
	 * @throws TermException if preprocessing fails
	 * @throws FileNotFoundException if the file for dumping the script
	 *                               cannot be opened
	 */
	public LassoRankerTerminationAnalysis(Script script, Boogie2SMT boogie2smt,
			TransFormula stem, TransFormula loop, Term[] axioms,
			Preferences preferences)
					throws TermException {
		m_preferences = preferences;
		checkPreferences(preferences);
		m_rankVarFactory = new VarFactory(boogie2smt);
		m_old_script = script;
		m_axioms = axioms;
		m_SupportingInvariantsDiscoveredByPreprocessors = new HashSet<Term>();
		m_Boogie2SMT = boogie2smt;
		
		m_stem_transition = stem;
		if (stem != null) {
			s_Logger.debug("Stem transition:\n" + m_stem_transition);
			m_stem = preprocess(m_stem_transition, stem, loop);
			s_Logger.debug("Preprocessed stem:\n" + m_stem);
		} else {
			m_stem = null;
		}
		
		assert(loop != null);
		m_loop_transition = loop;
		s_Logger.debug("Loop transition:\n" + m_loop_transition);
		m_loop = preprocess(m_loop_transition, stem, loop);
		checkVariables();
		s_Logger.debug("Preprocessed loop:\n" + m_loop);
	}
	
	/**
	 * Constructor for the LassoRanker interface. Calling this invokes the
	 * preprocessor on the stem and loop formula.
	 *  
	 * This constructor may only be supplied a loop transition, a stem has to
	 * be added later by calling addStem().
	 * 
	 * @param script the SMT script used to construct the transition formulae
	 * @param boogie2smt the boogie2smt object that created the TransFormulas
	 * @param loop a transition formula corresponding to the lasso's loop
	 * @param axioms a collection of axioms regarding the transitions' constants
	 * @param preferences configuration options for this plugin
	 * @throws TermException if preprocessing fails
	 * @throws FileNotFoundException if the file for dumping the script
	 *                               cannot be opened
	 */
	public LassoRankerTerminationAnalysis(Script script, Boogie2SMT boogie2smt,
			TransFormula loop, Term[] axioms, Preferences preferences)
					throws TermException, FileNotFoundException {
		this(script, boogie2smt, null, loop, axioms, preferences);
	}
	
	/**
	 * Verify that the preferences are set self-consistent and sensible
	 * Issues a bunch of logger infos and warnings.
	 */
	protected void checkPreferences(Preferences preferences) {
		assert preferences.num_strict_invariants >= 0;
		assert preferences.num_non_strict_invariants >= 0;
//		assert preferences.termination_check_nonlinear
//				|| preferences.only_nondecreasing_invariants
//				: "Use nondecreasing invariants with a linear SMT query.";
		if (preferences.num_strict_invariants == 0 &&
				preferences.num_non_strict_invariants == 0) {
			s_Logger.warn("Generation of supporting invariants is disabled.");
		}
	}
	
	/**
	 * @param rvc the ranking variable collector to be passed to the
	 *        preprocessors
	 * @return an array of all preprocessors that should be called before
	 *         termination analysis
	 */
	protected PreProcessor[] getPreProcessors(VarCollector rvc,
			TransFormula stem, TransFormula loop) {
		return new PreProcessor[] {
				new RewriteArrays(rvc, stem, loop, m_Boogie2SMT, m_SupportingInvariantsDiscoveredByPreprocessors),
				new RewriteDivision(rvc),
				new RewriteBooleans(rvc),
				new RewriteIte(),
				new RewriteTrueFalse(),
				new RewriteEquality(),
				new DNF(),
				new RemoveNegation(),
				new RewriteStrictInequalities()
		};
	}
	
	/**
	 * Add a stem transition to the lasso program.
	 * Calling this invokes the preprocessor on the stem transition.
	 * 
	 * @param stem a transition formula corresponding to the lasso's stem
	 * @throws TermException
	 */
	public void addStem(TransFormula stem_transition, 
			TransFormula originalStem, TransFormula originalLoop) throws TermException {
		if (m_stem != null) {
			s_Logger.warn("Adding a stem to a lasso that already had one.");
		}
		s_Logger.debug("Adding stem transition:\n" + stem_transition);
		m_stem = preprocess(stem_transition, originalStem, originalLoop);
		checkVariables();
		s_Logger.debug("Preprocessed stem:\n" + m_stem);
	}
	
	/**
	 * Preprocess the stem or loop transition. This applies the preprocessor
	 * classes and transforms the formula into a list of inequalities in DNF.
	 * 
	 * The list of preprocessors is given by this.getPreProcessors().
	 * 
	 * @see PreProcessor
	 * @throws TermException
	 */
	protected LinearTransition preprocess(TransFormula transition, 
			TransFormula originalStem, TransFormula originalLoop)
			throws TermException {
		s_Logger.info("Starting preprocessing step...");
		
		Term trans_term = transition.getFormula();
		Term axioms = Util.and(m_old_script, m_axioms);
		trans_term = Util.and(m_old_script, trans_term, axioms);
		VarCollector rvc =
				new VarCollector(m_rankVarFactory, transition);
		assert rvc.auxVarsDisjointFromInOutVars();
		assert rvc.allAreInOutAux(trans_term.getFreeVars()) == null;
		
		// Apply preprocessors
		for (PreProcessor preprocessor : 
					this.getPreProcessors(rvc, originalStem, originalLoop)) {
			trans_term = preprocessor.process(m_old_script, trans_term);
		}
		
		assert rvc.auxVarsDisjointFromInOutVars();
		assert rvc.allAreInOutAux(trans_term.getFreeVars()) == null;
		
		s_Logger.debug(SMTPrettyPrinter.print(trans_term));
		
		// Match inVars
		rvc.matchInVars();
		
		LinearTransition linear_trans = LinearTransition.fromTerm(
				trans_term, rvc.getInVars(), rvc.getOutVars());
		if (!m_preferences.enable_disjunction
				&& !linear_trans.isConjunctive()) {
			throw new UnsupportedOperationException(
					"Support for non-conjunctive lasso programs is disabled.");
		}
		
		return linear_trans;
	}
	
	/**
	 * Add all inVars of the loop as in- and outVars of the stem,
	 * and add all outVars of the stem as in- and outVars of the loop.
	 * 
	 * This ensures that (global) valuations for variables (e.g. those
	 * generated in the nontermination analysis) stay constant in transitions
	 * where these variables are not explicitly scoped.
	 */
	private void checkVariables() {
		if (m_stem == null) {
			return; // nothing to do
		}
		// Add variables existing in the loop to the stem
		Map<RankVar, Term> addVars = new HashMap<RankVar, Term>();
		for (Map.Entry<RankVar, Term> entry : m_loop.getInVars().entrySet()) {
			if (!m_stem.getInVars().containsKey(entry.getKey()) &&
					!m_stem.getOutVars().containsKey(entry.getKey())) {
				addVars.put(entry.getKey(), entry.getValue());
			}
		}
		if (!addVars.isEmpty()) {
			// Because the variable maps in LinearTransition are immutable,
			// make a new transition and replace the old one
			Map<RankVar, Term> inVars =
					new HashMap<RankVar, Term>(m_stem.getInVars());
			Map<RankVar, Term> outVars =
					new HashMap<RankVar, Term>(m_stem.getOutVars());
			inVars.putAll(addVars);
			outVars.putAll(addVars);
			m_stem = new LinearTransition(m_stem.getPolyhedra(), inVars,
					outVars);
		}
		
		// Add variables existing in the stem to the loop
		addVars = new HashMap<RankVar, Term>();
		for (Map.Entry<RankVar, Term> entry : m_stem.getOutVars().entrySet()) {
			if (!m_loop.getInVars().containsKey(entry.getKey()) &&
					!m_loop.getOutVars().containsKey(entry.getKey())) {
				addVars.put(entry.getKey(), entry.getValue());
			}
		}
		if (!addVars.isEmpty()) {
			// Because the variable maps in LinearTransition are immutable,
			// make a new transition and replace the old one
			Map<RankVar, Term> inVars =
					new HashMap<RankVar, Term>(m_loop.getInVars());
			Map<RankVar, Term> outVars =
					new HashMap<RankVar, Term>(m_loop.getOutVars());
			inVars.putAll(addVars);
			outVars.putAll(addVars);
			m_loop = new LinearTransition(m_loop.getPolyhedra(), inVars,
					outVars);
		}
	}
	
	/**
	 * @return the number of variables occurring in the preprocessed loop
	 *         transition
	 */
	public int getLoopVarNum() {
		return m_loop.getVariables().size();
	}
	
	/**
	 * @return the number of variables occurring in the preprocessed stem
	 *         transition
	 */
	public int getStemVarNum() {
		if (m_stem != null) {
			return m_stem.getVariables().size();
		} else {
			return 0;
		}
	}
	
	/**
	 * @return the number of disjuncts in the loop transition's DNF after
	 *         preprocessing
	 */
	public int getLoopDisjuncts() {
		return m_loop.getNumPolyhedra();
	}
	
	/**
	 * @return the number of disjuncts in the stem transition's DNF after
	 *         preprocessing
	 */
	public int getStemDisjuncts() {
		if (m_stem != null) {
			return m_stem.getNumPolyhedra();
		} else {
			return 1;
		}
	}
	
	/**
	 * @return the number of supporting invariants generated by the last
	 * termination analysis
	 */
	public int getNumSIs() {
		return m_numSIs;
	}
	
	/**
	 * @return the number of Motzkin's Theorem applications in the last
	 * termination analysis
	 */
	public int getNumMotzkin() {
		return m_numMotzkin;
	}
	
	private String benchmarkScriptMessage(LBool constraintSat,
			RankingFunctionTemplate template) {
		StringBuilder sb = new StringBuilder();
		sb.append("BenchmarkResult: ");
		sb.append(constraintSat);
		sb.append(" for template ");
		sb.append(template.getName());
		sb.append(" with degree ");
		sb.append(template.getDegree());
		sb.append(". ");
		sb.append(getStatistics());
		return sb.toString();
	}
	
	/**
	 * @return a pretty version of the guesses for Motzkin coefficients
	 */
	private String motzkinGuesses(ArgumentSynthesizer as) {
		StringBuilder sb = new StringBuilder();
		Rational[] eigenvalues = as.guessEigenvalues(true);
		sb.append("[");
		for (int i = 0; i < eigenvalues.length; ++i) {
			if (i > 0) {
				sb.append(", ");
			}
			sb.append(eigenvalues[i].toString());
		}
		sb.append("]");
		return sb.toString();
	}
	
	/**
	 * @return various statistics as a neatly formatted string
	 */
	public String getStatistics() {
		StringBuilder sb = new StringBuilder();
		sb.append("Number of variables in the stem: ");
		sb.append(getStemVarNum());
		sb.append("  Number of variables in the loop: ");
		sb.append(getLoopVarNum());
		sb.append("  Number of disjunctions in the stem: ");
		sb.append(getStemDisjuncts());
		sb.append("  Number of disjunctions in the loop: ");
		sb.append(getLoopDisjuncts());
		sb.append("  Number of supporting invariants: ");
		sb.append(getNumSIs());
		sb.append("  Number of Motzkin applications: ");
		sb.append(getNumMotzkin());
		return sb.toString();
	}
	
	/**
	 * Try to find a non-termination argument for the lasso program.
	 * 
	 * @return the non-termination argument or null of none is found
	 */
	public NonTerminationArgument checkNonTermination()
			throws SMTLIBException, TermException {
		s_Logger.info("Checking for nontermination...");
		
		NonTerminationArgumentSynthesizer nas =
				new NonTerminationArgumentSynthesizer(
						m_stem,
						m_loop,
						m_preferences
				);
		s_Logger.debug("Guesses for Motzkin coefficients: "
				+ motzkinGuesses(nas));
		final LBool constraintSat = nas.synthesize();
		if (constraintSat == LBool.SAT) {
			s_Logger.info("Proved nontermination.");
			s_Logger.info(nas.getArgument());
		}
		nas.close();
		return (constraintSat == LBool.SAT) ? nas.getArgument() : null;
	}
	
	/**
	 * Try to find a termination argument for the lasso program specified by
	 * the given ranking function template.
	 * 
	 * @param template the ranking function template
	 * @return the non-termination argument or null of none is found
	 */
	public TerminationArgument tryTemplate(RankingFunctionTemplate template)
			throws SMTLIBException, TermException {
		// ignore stem
		s_Logger.info("Using template '" + template.getName()
				+ "'.");
		s_Logger.info("Template has degree " + template.getDegree() + ".");
		s_Logger.debug(template);
		
		TerminationArgumentSynthesizer tas =
				new TerminationArgumentSynthesizer(m_stem, m_loop,
						template, m_preferences);
		s_Logger.debug("Guesses for Motzkin coefficients: "
				+ motzkinGuesses(tas));
		final LBool constraintSat = tas.synthesize();
		m_numSIs = tas.getNumSIs();
		m_numMotzkin = tas.getNumMotzkin();
		s_Logger.debug(benchmarkScriptMessage(constraintSat, template));
		if (constraintSat == LBool.SAT) {
			s_Logger.info("Proved termination.");
			TerminationArgument arg = tas.getArgument();
			s_Logger.info(arg);
			Term[] lexTerm = arg.getRankingFunction().asLexTerm(m_old_script);
			for (Term t : lexTerm) {
				s_Logger.debug(SMTPrettyPrinter.print(t));
			}
		}
		tas.close();
		return (constraintSat == LBool.SAT) ? tas.getArgument() : null;
	}
}
