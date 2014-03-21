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

import de.uni_freiburg.informatik.ultimate.logic.Term;
import de.uni_freiburg.informatik.ultimate.model.boogie.BoogieVar;


/**
 * 
 * 
 * @author Jan Leike
 */
public class AuxVar extends RankVar {
	private static final long serialVersionUID = 5797704734079950805L;
	
	private final String m_name;
	private final BoogieVar m_boogieVar;
	private final Term m_definition;
	
	/**
	 * @param name a globally unique name
	 * @param boogieVar the associated boogieVar, if any
	 * @param definition the definition of this auxiliary variable, i.e.,
	 *                   the term it replaces
	 */
	public AuxVar(String name, BoogieVar boogieVar, Term definition) {
		m_name = name;
		m_boogieVar = boogieVar;
		m_definition = definition;
	}
	
	/**
	 * @return the definition of this auxiliary variable, i.e., the term it
	 *         replaces
	 */
	public Term getDefinition() {
		return m_definition;
	}
	
	@Override
	public BoogieVar getAssociatedBoogieVar() {
		return m_boogieVar;
	}
	
	@Override
	public String getGloballyUniqueId() {
		return m_name;
	}
	
	@Override
	public String toString() {
		return m_name;
	}
}
