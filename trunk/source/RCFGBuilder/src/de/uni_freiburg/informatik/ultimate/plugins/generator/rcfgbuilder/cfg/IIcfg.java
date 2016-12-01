/*
 * Copyright (C) 2016 Daniel Dietsch (dietsch@informatik.uni-freiburg.de)
 * Copyright (C) 2016 Matthias Heizmann (heizmann@informatik.uni-freiburg.de)
 * Copyright (C) 2016 University of Freiburg
 *
 * This file is part of the ULTIMATE RCFGBuilder plug-in.
 *
 * The ULTIMATE RCFGBuilder plug-in is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * The ULTIMATE RCFGBuilder plug-in is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with the ULTIMATE RCFGBuilder plug-in. If not, see <http://www.gnu.org/licenses/>.
 *
 * Additional permission under GNU GPL version 3 section 7:
 * If you modify the ULTIMATE RCFGBuilder plug-in, or any covered work, by linking
 * or combining it with Eclipse RCP (or a modified version of Eclipse RCP),
 * containing parts covered by the terms of the Eclipse Public License, the
 * licensors of the ULTIMATE RCFGBuilder plug-in grant you additional permission
 * to convey the resulting work.
 */
package de.uni_freiburg.informatik.ultimate.plugins.generator.rcfgbuilder.cfg;

import java.util.Collection;
import java.util.Map;

import de.uni_freiburg.informatik.ultimate.core.lib.models.VisualizationNode;
import de.uni_freiburg.informatik.ultimate.core.model.models.IElement;
import de.uni_freiburg.informatik.ultimate.core.model.models.IVisualizable;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.cfg.CfgSmtToolkit;

/**
 *
 * @author Matthias Heizmann (heizmann@informatik.uni-freiburg.de)
 * @author Daniel Dietsch (dietsch@informatik.uni-freiburg.de)
 *
 */
public interface IIcfg extends IElement, IVisualizable<VisualizationNode> {

	/**
	 * Maps the pair of procedure name location name to the LocNode that represents this location.
	 */
	Map<String, Map<String, BoogieIcfgLocation>> getProgramPoints();

	/**
	 * Maps a procedure name to the entry node of that procedure. The entry node of a procedure represents an auxiliary
	 * location that is reached after calling the procedure. Afterwards we assume that the global variables and
	 * corresponding and oldvars have the same values, we assume the requires clause and reach the initial node.
	 *
	 */
	Map<String, BoogieIcfgLocation> getProcedureEntryNodes();

	/**
	 * Maps a procedure name to the the exit node of that procedure. The exit node of a procedure represents an
	 * auxiliary location that is reached after assuming the ensures part of the specification. This locNode is the
	 * source of ReturnEdges which lead to the callers of this procecure.
	 */
	Map<String, BoogieIcfgLocation> getProcedureExitNodes();

	/**
	 * Maps a procedure name to error locations generated for this procedure.
	 */
	Map<String, Collection<BoogieIcfgLocation>> getProcedureErrorNodes();

	CodeBlockFactory getCodeBlockFactory();

	CfgSmtToolkit getCfgSmtToolkit();

	/**
	 * Returns an identifier that can be used during debugging.
	 */
	String getIdentifier();

	@Override
	default VisualizationNode getVisualizationGraph() {
		return IcfgGraphProvider.getVisualizationGraph(this);
	}
}