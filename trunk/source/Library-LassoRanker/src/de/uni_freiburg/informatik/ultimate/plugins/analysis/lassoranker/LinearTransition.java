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

import java.util.*;

import de.uni_freiburg.informatik.ultimate.logic.ApplicationTerm;
import de.uni_freiburg.informatik.ultimate.logic.Term;
import de.uni_freiburg.informatik.ultimate.logic.TermVariable;
import de.uni_freiburg.informatik.ultimate.plugins.analysis.lassoranker.exceptions.TermException;
import de.uni_freiburg.informatik.ultimate.plugins.analysis.lassoranker.preprocessors.InequalityConverter;
import de.uni_freiburg.informatik.ultimate.plugins.analysis.lassoranker.preprocessors.IntegralHull;


/**
 * A LinearTransition is a transition relation given as a finite union of
 * polyhedra.
 * 
 * There are two kinds of distinguished variables:
 * <li> inVars, (unprimed) input variables, and
 * <li> outVars, (primed) output variables.
 * 
 * Additionally, there might also be 'internal' variables that are neither
 * inVars, nor outVars.
 * 
 * The LinearTransition is LassoRanker's internal representation of
 * the TransFormula.
 * 
 * @author Jan Leike
 * @see de.uni_freiburg.informatik.ultimate.modelcheckerutils.boogie.TransFormula
 */
public class LinearTransition {
	
	private final Map<RankVar, TermVariable> m_inVars;
	private final Map<RankVar, TermVariable> m_outVars;
	
	private List<List<LinearInequality>> m_polyhedra;
	private final boolean m_contains_integers;
	
	/**
	 * 
	 * @param polyhedra
	 * @param inVars
	 * @param outVars
	 */
	public LinearTransition(List<List<LinearInequality>> polyhedra,
			Map<RankVar, TermVariable> inVars,
			Map<RankVar, TermVariable> outVars) {
		assert(polyhedra != null);
		assert(inVars != null);
		assert(outVars != null);
		for (List<LinearInequality> polyhedron : polyhedra) {
			assert(polyhedron != null);
		}
		m_polyhedra = polyhedra;
		m_inVars = Collections.unmodifiableMap(inVars);
		m_outVars = Collections.unmodifiableMap(outVars);
		m_contains_integers = checkIfContainsIntegers();
	}
	
	/**
	 * @return true iff there is at least one integer variable in m_polyhedra
	 */
	private boolean checkIfContainsIntegers() {
		for (List<LinearInequality> polyhedron : m_polyhedra) {
			for (LinearInequality ieq : polyhedron) {
				for (TermVariable var : ieq.getVariables()) {
					if (var.getSort().getName().equals("Int")) {
						return true;
					}
				}
			}
		}
		return false;
	}
	
	/**
	 * @return the maximal transition (0 <= 0)
	 */
	public static LinearTransition getTranstionTrue() {
		LinearInequality eqTrue = new LinearInequality();
		return new LinearTransition(
				Collections.singletonList(Collections.singletonList(eqTrue)),
				Collections.<RankVar, TermVariable> emptyMap(),
				Collections.<RankVar, TermVariable> emptyMap()
		);
	}
	
	/**
	 * @return the empty transition (0 < 0)
	 */
	public static LinearTransition getTranstionFalse() {
		LinearInequality eqFalse = new LinearInequality();
		eqFalse.setStrict(true);
		return new LinearTransition(
				Collections.singletonList(Collections.singletonList(eqFalse)),
				Collections.<RankVar, TermVariable> emptyMap(),
				Collections.<RankVar, TermVariable> emptyMap()
		);
	}
	
	/**
	 * Convert a term into a list of clauses
	 * @param term a term in disjunctive normal form
	 * @return list of clauses
	 */
	private static List<Term> toClauses(Term term) {
		List<Term> l = new ArrayList<Term>();
		if (!(term instanceof ApplicationTerm)) {
			l.add(term);
			return l;
		}
		ApplicationTerm appt = (ApplicationTerm) term;
		if (!appt.getFunction().getName().equals("or")) {
			l.add(term);
			return l;
		}
		for (Term t : appt.getParameters()) {
			l.addAll(toClauses(t));
		}
		return l;
	}
	
	/**
	 * Convert a term in the proper form into a linear transition.
	 * 
	 * The term must be in DNF, contain no negations, and only atoms in
	 * the form a <= b, a < b, a >= b, and a > b, with a and b being linear
	 * expressions.
	 * 
	 * @param term the input term
	 * @throws TermException if the supplied term does not have the correct form
	 */
	public static LinearTransition fromTerm(Term term,
			Map<RankVar, TermVariable> inVars,
			Map<RankVar, TermVariable> outVars) throws TermException {
		List<List<LinearInequality>> polyhedra =
				new ArrayList<List<LinearInequality>>();
		for (Term disjunct : toClauses(term)) {
			polyhedra.add(InequalityConverter.convert(disjunct));
		}
		return new LinearTransition(polyhedra, inVars, outVars);
	}
	
	/**
	 * @return the mapping between the trasition's input (unprimed) variables
	 *         and their representation as a TermVariable
	 */
	public Map<RankVar, TermVariable> getInVars() {
		return m_inVars;
	}
	
	/**
	 * @return the mapping between the trasition's output (primed) variables
	 *         and their representation as a TermVariable
	 */
	public Map<RankVar, TermVariable> getOutVars() {
		return m_outVars;
	}
	
	/**
	 * @return whether this linear transition contains any integer variables
	 */
	public boolean containsIntegers() {
		return m_contains_integers;
	}
	
	/**
	 * Compute the integral hull of each polyhedron
	 */
	public void integralHull() {
		for (List<LinearInequality> polyhedron : m_polyhedra) {
			polyhedron.addAll(IntegralHull.compute(polyhedron));
		}
	}
	
	/**
	 * @return whether this transition contains only one polyhedron
	 */
	public boolean isConjunctive() {
		return m_polyhedra.size() <= 1;
	}
	
	/**
	 * @return the number of polyhedra (number of disjuncts)
	 */
	public int getNumPolyhedra() {
		return m_polyhedra.size();
	}
	
	/**
	 * @return the total number of inequalities in all polyhedra 
	 */
	public int getNumInequalities() {
		int num = 0;
		for (List<LinearInequality> polyhedron : m_polyhedra) {
			num += polyhedron.size();
		}
		return num;
	}
	
	/**
	 * @return all variables occuring in any of the inequalities
	 */
	public Set<TermVariable> getVariables() {
		Set<TermVariable> vars = new HashSet<TermVariable>();
		for (List<LinearInequality> polyhedron : m_polyhedra) {
			for (LinearInequality li : polyhedron) {
				vars.addAll(li.getVariables());
			}
		}
		return vars;
	}
	
	/**
	 * @return this transition's polyhedra as a list
	 */
	public List<List<LinearInequality>> getPolyhedra() {
		return Collections.unmodifiableList(m_polyhedra);
	}
	
	public String toString() {
		StringBuilder sb = new StringBuilder();
		
		// inVars and outVars
		sb.append("InVars: ");
		sb.append(m_inVars.toString());
		sb.append("\nOutVars: ");
		sb.append(m_outVars.toString());
		
		// Transition polyhedron
		sb.append("\n(OR\n");
		for (List<LinearInequality> polyhedron : m_polyhedra) {
			sb.append("    (AND\n");
			for (LinearInequality ieq : polyhedron) {
				sb.append("        ");
				sb.append(ieq);
				sb.append("\n");
			}
			sb.append("    )\n");
		}
		sb.append(")");
		return sb.toString();
	}
}
