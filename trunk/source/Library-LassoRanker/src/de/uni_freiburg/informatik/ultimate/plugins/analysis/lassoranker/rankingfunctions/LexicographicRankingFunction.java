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
 * An lexicographic ranking function as generated by the lexicographic template
 * 
 * @author Jan Leike
 */
public class LexicographicRankingFunction extends RankingFunction {
	private static final long serialVersionUID = -7426526617632086331L;
	
	private final List<AffineFunction> m_ranking;
	public final int lex;
	
	public LexicographicRankingFunction(List<AffineFunction> ranking) {
		m_ranking = Collections.unmodifiableList(ranking);
		lex = ranking.size();
		assert(lex > 0);
	}
	
	@Override
	public String getName() {
		return m_ranking.size() + "-lex";
	}
	
	public List<AffineFunction> getComponents() {
		return m_ranking;
	}
	
	@Override
	public Set<RankVar> getVariables() {
		Set<RankVar> vars = new LinkedHashSet<RankVar>();
		for (AffineFunction af : m_ranking) {
			vars.addAll(af.getVariables());
		}
		return vars;
	}
	
	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		sb.append(m_ranking.size());
		sb.append("-lexicographic ranking function:\n");
		sb.append("  f(");
		boolean first = true;
		for (RankVar var : getVariables()) {
			if (!first) {
				sb.append(", ");
			}
			sb.append(var.getGloballyUniqueId());
			first = false;
		}
		sb.append(") = <");
		for (int i = 0; i < lex; ++i) {
			sb.append(m_ranking.get(i));
			if (i < lex - 1) {
				sb.append(",  ");
			}
		}
		sb.append(">");
		return sb.toString();
	}
	
	@Override
	public Term[] asLexTerm(Script script) throws SMTLIBException {
		Term[] lex = new Term[m_ranking.size()];
		for (int i = 0; i < m_ranking.size(); ++i) {
			lex[i] = m_ranking.get(i).asTerm(script);
		}
		return lex;
	}
	
	@Override
	public Ordinal evaluate(Map<RankVar, Rational> assignment) {
		Ordinal o = Ordinal.ZERO;
		Ordinal w_pow = Ordinal.ONE;
		for (int i = lex - 1; i >= 0; --i) {
			Rational r = m_ranking.get(i).evaluate(assignment);
			if (r.compareTo(Rational.ZERO) > 0) {
				BigInteger k = r.ceil().numerator();
				o = o.add(w_pow.mult(Ordinal.fromInteger(k)));
			}
			w_pow = w_pow.mult(Ordinal.OMEGA);
		}
		return o;
	}
}