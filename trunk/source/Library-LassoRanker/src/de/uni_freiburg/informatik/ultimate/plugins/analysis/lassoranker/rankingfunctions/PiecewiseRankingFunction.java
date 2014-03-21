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
package de.uni_freiburg.informatik.ultimate.plugins.analysis.lassoranker.rankingfunctions;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import de.uni_freiburg.informatik.ultimate.logic.Rational;
import de.uni_freiburg.informatik.ultimate.logic.SMTLIBException;
import de.uni_freiburg.informatik.ultimate.logic.Script;
import de.uni_freiburg.informatik.ultimate.logic.Term;
import de.uni_freiburg.informatik.ultimate.plugins.analysis.lassoranker.AffineFunction;
import de.uni_freiburg.informatik.ultimate.plugins.analysis.lassoranker.RankVar;


/**
 * An piecewise ranking function as generated by the piecewise template
 * 
 * @author Jan Leike
 */
public class PiecewiseRankingFunction extends RankingFunction {
	private static final long serialVersionUID = 1605612582853046558L;
	
	private final List<AffineFunction> m_ranking;
	private final List<AffineFunction> m_predicates;
	public final int pieces;
	
	public PiecewiseRankingFunction(List<AffineFunction> ranking, List<AffineFunction> predicates) {
		m_ranking = Collections.unmodifiableList(ranking);
		m_predicates = Collections.unmodifiableList(predicates);
		pieces = ranking.size();
		assert(pieces > 0);
		assert(pieces == predicates.size());
	}
	
	@Override
	public String getName() {
		return m_ranking.size() + "-piece";
	}

	
	@Override
	public Set<RankVar> getVariables() {
		Set<RankVar> vars = new LinkedHashSet<RankVar>();
		for (AffineFunction af : m_ranking) {
			vars.addAll(af.getVariables());
		}
		return vars;
	}
	
	public List<AffineFunction> getComponents() {
		List<AffineFunction> l = new ArrayList<AffineFunction>();
		l.addAll(m_ranking);
		l.addAll(m_predicates);
		return l;
	}
	
	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		sb.append(m_ranking.size());
		sb.append("-piece ranking function:\n");
		sb.append("  f(");
		boolean first = true;
		for (RankVar var : getVariables()) {
			if (!first) {
				sb.append(", ");
			}
			sb.append(var.getGloballyUniqueId());
			first = false;
		}
		sb.append(") = {\n");
		for (int i = 0; i < pieces; ++i) {
			sb.append("    ");
			sb.append(m_ranking.get(i));
			sb.append(",\tif ");
			sb.append(m_predicates.get(i));
			sb.append(" >= 0");
			if (i < pieces - 1) {
				sb.append(",\n");
			} else {
				sb.append(".");
			}
		}
		return sb.toString();
	}
	
	@Override
	public Term[] asLexTerm(Script script) throws SMTLIBException {
		Term value = m_ranking.get(m_ranking.size() - 1).asTerm(script);
		for (int i = m_ranking.size() - 1; i >= 0; --i) {
			AffineFunction af = m_ranking.get(i);
			AffineFunction gf = m_predicates.get(i);
			Term pred = script.term(">=", gf.asTerm(script),
					script.numeral(BigInteger.ZERO));
			if (i < m_ranking.size() - 1) {
				value = script.term("ite", pred, af.asTerm(script), value);
			}
		}
		return new Term[] { value };
	}
	
	@Override
	public Ordinal evaluate(Map<RankVar, Rational> assignment) {
		Rational r = Rational.ZERO;
		for (int i = 0; i < pieces; ++i) {
			if (!m_predicates.get(i).evaluate(assignment).isNegative()) {
				Rational rnew = m_ranking.get(i).evaluate(assignment);
				if (rnew.compareTo(r) > 0) {
					r = rnew;
				}
			}
		}
		return Ordinal.fromInteger(r.ceil().numerator());
	}
}