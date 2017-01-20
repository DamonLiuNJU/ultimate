/*
 * Copyright (C) 2017 Julian Loeffler (loefflju@informatik.uni-freiburg.de)
 * Copyright (C) 2017 University of Freiburg
 *
 * This file is part of the ULTIMATE SpaceExParser plug-in.
 *
 * The ULTIMATE SpaceExParser plug-in is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * The ULTIMATE SpaceExParser plug-in is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with the ULTIMATE SpaceExParser plug-in. If not, see <http://www.gnu.org/licenses/>.
 *
 * Additional permission under GNU GPL version 3 section 7:
 * If you modify the ULTIMATE SpaceExParser plug-in, or any covered work, by linking
 * or combining it with Eclipse RCP (or a modified version of Eclipse RCP),
 * containing parts covered by the terms of the Eclipse Public License, the
 * licensors of the ULTIMATE SpaceExParser plug-in grant you additional permission
 * to convey the resulting work.
 */
package de.uni_freiburg.informatik.ultimate.plugins.spaceex.icfg;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import de.uni_freiburg.informatik.ultimate.boogie.ast.BoogieASTNode;
import de.uni_freiburg.informatik.ultimate.core.lib.models.annotation.ModernAnnotations;
import de.uni_freiburg.informatik.ultimate.core.model.models.ILocation;
import de.uni_freiburg.informatik.ultimate.core.model.models.IPayload;
import de.uni_freiburg.informatik.ultimate.core.model.models.Payload;
import de.uni_freiburg.informatik.ultimate.core.model.models.annotation.IAnnotations;
import de.uni_freiburg.informatik.ultimate.core.model.services.ILogger;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.cfg.BasicIcfg;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.cfg.CfgSmtToolkit;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.cfg.structure.IcfgInternalTransition;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.cfg.transitions.TransFormulaBuilder;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.cfg.transitions.UnmodifiableTransFormula;
import de.uni_freiburg.informatik.ultimate.plugins.generator.rcfgbuilder.cfg.BoogieIcfgLocation;
import de.uni_freiburg.informatik.ultimate.plugins.spaceex.automata.hybridsystem.HybridAutomaton;
import de.uni_freiburg.informatik.ultimate.plugins.spaceex.automata.hybridsystem.Location;
import de.uni_freiburg.informatik.ultimate.plugins.spaceex.automata.hybridsystem.Transition;
import de.uni_freiburg.informatik.ultimate.plugins.spaceex.parser.preferences.SignValuePair;
import de.uni_freiburg.informatik.ultimate.plugins.spaceex.parser.preferences.SpaceExPreferenceManager;

/**
 * Class that handles conversion of Hybrid Models/Systems/Automata to an ICFG
 *
 * @author Julian Loeffler (loefflju@informatik.uni-freiburg.de)
 *
 */
public class HybridIcfgGenerator extends ModernAnnotations {
	
	private final ILogger mLogger;
	private final SpaceExPreferenceManager mSpaceExPreferenceManager;
	private final CfgSmtToolkit mSmtToolkit;
	private final Map<String, HybridCfgComponent> mCfgComponents;
	private final IPayload mPayload;
	private final String mProcedure = "MAIN";
	private BoogieASTNode mBoogieASTNode;
	
	public HybridIcfgGenerator(final ILogger logger, final SpaceExPreferenceManager preferenceManager,
			final CfgSmtToolkit smtToolkit) {
		mLogger = logger;
		mSpaceExPreferenceManager = preferenceManager;
		mSmtToolkit = smtToolkit;
		mPayload = new Payload();
		mCfgComponents = new HashMap<>();
		mBoogieASTNode = new BoogieASTNode(new ILocation() {
			
			@Override
			public int getStartLine() {
				// TODO Auto-generated method stub
				return -1;
			}
			
			@Override
			public int getStartColumn() {
				// TODO Auto-generated method stub
				return -1;
			}
			
			@Override
			public String getFileName() {
				// TODO Auto-generated method stub
				return "";
			}
			
			@Override
			public int getEndLine() {
				// TODO Auto-generated method stub
				return -1;
			}
			
			@Override
			public int getEndColumn() {
				// TODO Auto-generated method stub
				return -1;
			}
			
			@Override
			public ILocation getOrigin() {
				// TODO Auto-generated method stub
				return null;
			}
			
			@Override
			public IAnnotations getCheck() {
				// TODO Auto-generated method stub
				return null;
			}
			
			@Override
			public boolean isLoop() {
				// TODO Auto-generated method stub
				return false;
			}
		});
		// create a root + error location;
		final BoogieIcfgLocation root = new BoogieIcfgLocation("root", mProcedure, false, mBoogieASTNode);
		mCfgComponents.put("root",
				new HybridCfgComponent("root", root, root, Collections.EMPTY_LIST, Collections.EMPTY_LIST));
		final BoogieIcfgLocation error = new BoogieIcfgLocation("error", mProcedure, true, mBoogieASTNode);
		mCfgComponents.put("error",
				new HybridCfgComponent("error", error, error, Collections.EMPTY_LIST, Collections.EMPTY_LIST));
	}
	
	public BasicIcfg<BoogieIcfgLocation> createIfcgFromComponents() {
		BasicIcfg<BoogieIcfgLocation> icfg = new BasicIcfg<>("testicfg", mSmtToolkit, BoogieIcfgLocation.class);
		mCfgComponents.forEach((key, value) -> {
			mLogger.info("ID:" + key + ", Component:" + value.toString());
		});
		// root, initial state
		icfg.addLocation(mCfgComponents.get("root").getStart(), true, false, false, false, false);
		mCfgComponents.remove("root");
		// error, error state
		icfg.addLocation(mCfgComponents.get("error").getStart(), false, true, false, false, false);
		mCfgComponents.remove("error");
		// push the remaining locations into the icfg
		mCfgComponents.forEach((id, comp) -> {
			// start is proc_entry + end is proc_exit
			icfg.addOrdinaryLocation(comp.getStart());
			icfg.addOrdinaryLocation(comp.getEnd());
			for (final BoogieIcfgLocation loc : comp.getLocations()) {
				icfg.addOrdinaryLocation(loc);
			}
		});
		mLogger.info("#################ICFG###################");
		mLogger.info(icfg.getProgramPoints().toString());
		mLogger.info(icfg.getSymboltable().getLocals("MAIN").toString());
		return icfg;
	}
	
	public void modelToIcfg(final HybridAutomaton aut) {
		/*
		 * in order to convert the hybrid model to an ICFG, we have to convert the parallelComposition of the regarded
		 * system.
		 */
		if (aut == null) {
			throw new IllegalStateException("HybridAutomaton aut has not been assigned and is null");
		} else {
			// convert automaton to cfg components
			automatonToIcfg(aut);
		}
	}
	
	private void automatonToIcfg(final HybridAutomaton automaton) {
		final Location initialLocation = automaton.getInitialLocation();
		final Map<Integer, Location> locations = automaton.getLocations();
		final List<Transition> transitions = automaton.getTransitions();
		// we can merge all variables into one set.
		final Set<String> variables = automaton.getGlobalParameters();
		variables.addAll(automaton.getGlobalConstants());
		variables.addAll(automaton.getLocalConstants());
		variables.addAll(automaton.getLocalParameters());
		// ICFG locations + edges for variables
		variablesToIcfg(variables);
		// for locations
		locationsToIcfg(locations);
		// for transitions
		transitionsToIcfg(transitions);
	}
	
	/*
	 * variable methods
	 */
	private void variablesToIcfg(final Set<String> variables) {
		// get initial values of the variable
		final Map<String, List<SignValuePair>> initialVars = mSpaceExPreferenceManager.getInitialVariables();
		// create a location/transition which holds ALL variable assignments.
		final int value;
		for (final String var : variables) {
			// if the variable got an initial value, assign it.
			if (initialVars.containsKey(var)) {
				final List<SignValuePair> init = initialVars.get(var);
				// TODO: convert initial values to a reasonable value or interval
			} else {
				// TODO: assume invariant of initial location.
			}
		}
		// create variable component of the form start ----variable assignment----> end
		final List<BoogieIcfgLocation> locations = new ArrayList<>();
		final List<IcfgInternalTransition> transitions = new ArrayList<>();
		final String id = "varAssignment";
		final BoogieIcfgLocation start = new BoogieIcfgLocation(id + "_start", mProcedure, false, mBoogieASTNode);
		final BoogieIcfgLocation end = new BoogieIcfgLocation(id + "_end", mProcedure, false, mBoogieASTNode);
		// TODO: transformula
		final UnmodifiableTransFormula tfStartEnd =
				TransFormulaBuilder.getTrivialTransFormula(mSmtToolkit.getManagedScript());
		final IcfgInternalTransition startEnd = new IcfgInternalTransition(start, end, mPayload, tfStartEnd);
		start.addOutgoing(startEnd);
		end.addIncoming(startEnd);
		transitions.add(startEnd);
		// new cfgComponent, has to be connected to the root node.
		mCfgComponents.put(id, new HybridCfgComponent(id, start, end, locations, transitions));
		/*
		 * Transition from Root to varAssignment
		 */
		// the source of the transition is the the end of the source CFG component
		final BoogieIcfgLocation source = mCfgComponents.get("root").getEnd();
		// the target of the transition is the the start of the target CFG component
		final BoogieIcfgLocation target = mCfgComponents.get("varAssignment").getStart();
		// TODO: transformula
		final UnmodifiableTransFormula transFormula =
				TransFormulaBuilder.getTrivialTransFormula(mSmtToolkit.getManagedScript());
		final IcfgInternalTransition transition = new IcfgInternalTransition(source, target, mPayload, transFormula);
		source.addOutgoing(transition);
		target.addIncoming(transition);
	}
	
	/*
	 * Location methods
	 */
	private void locationsToIcfg(final Map<Integer, Location> autLocations) {
		/*
		 * locations consist of Flow and the invariant. -> Startnode (1) -> if/else invariant (2) -> apply flow (3) ->
		 * if/else invariant (4) -> transition/back to (3) if the invariant is injured and no transition can be taken,
		 * go to exit node.
		 */
		for (final Map.Entry<Integer, Location> entry : autLocations.entrySet()) {
			final Integer autid = entry.getKey();
			final Location loc = entry.getValue();
			final List<IcfgInternalTransition> transitions = new ArrayList<>();
			final List<BoogieIcfgLocation> locations = new ArrayList<>();
			/*
			 * Locations: Start, End, Flow, InvariantCheck
			 */
			final BoogieIcfgLocation start =
					new BoogieIcfgLocation(autid + "_start", mProcedure, false, mBoogieASTNode);
			final BoogieIcfgLocation end = new BoogieIcfgLocation(autid + "_end", mProcedure, false, mBoogieASTNode);
			final BoogieIcfgLocation flow = new BoogieIcfgLocation(autid + "_flow", mProcedure, false, mBoogieASTNode);
			locations.add(flow);
			final BoogieIcfgLocation invCheck =
					new BoogieIcfgLocation(autid + "_invCheck", mProcedure, false, mBoogieASTNode);
			locations.add(invCheck);
			/*
			 * Transitions from start to Flow/Exit if invariant true, go to flow, else to error loc
			 */
			// TODO: transformula
			final UnmodifiableTransFormula tfStartFlow =
					TransFormulaBuilder.getTrivialTransFormula(mSmtToolkit.getManagedScript());
			final IcfgInternalTransition startFlow = new IcfgInternalTransition(start, flow, mPayload, tfStartFlow);
			start.addOutgoing(startFlow);
			flow.addIncoming(startFlow);
			transitions.add(startFlow);
			// TODO: transformula
			final UnmodifiableTransFormula tfStartError =
					TransFormulaBuilder.getTrivialTransFormula(mSmtToolkit.getManagedScript());
			final IcfgInternalTransition startError =
					new IcfgInternalTransition(start, mCfgComponents.get("error").getStart(), mPayload, tfStartError);
			start.addOutgoing(startError);
			mCfgComponents.get("error").getStart().addIncoming(startError);
			transitions.add(startError);
			/*
			 * Transition flow to invCheck
			 */
			// TODO: transformula
			final UnmodifiableTransFormula tfFlowInv =
					TransFormulaBuilder.getTrivialTransFormula(mSmtToolkit.getManagedScript());
			final IcfgInternalTransition flowInv = new IcfgInternalTransition(flow, invCheck, mPayload, tfFlowInv);
			flow.addOutgoing(flowInv);
			invCheck.addIncoming(flowInv);
			transitions.add(flowInv);
			/*
			 * Transition invCheck to end/exit if invariant true, go to end, else to error loc
			 */
			// invcheck -> end
			// TODO: transformula
			final UnmodifiableTransFormula tfInvEnd =
					TransFormulaBuilder.getTrivialTransFormula(mSmtToolkit.getManagedScript());
			final IcfgInternalTransition invEnd = new IcfgInternalTransition(invCheck, end, mPayload, tfInvEnd);
			invCheck.addOutgoing(invEnd);
			end.addIncoming(invEnd);
			transitions.add(invEnd);
			// invcheck -> exit
			// TODO: transformula
			final UnmodifiableTransFormula tfInvError =
					TransFormulaBuilder.getTrivialTransFormula(mSmtToolkit.getManagedScript());
			final IcfgInternalTransition invError =
					new IcfgInternalTransition(invCheck, mCfgComponents.get("error").getStart(), mPayload, tfInvError);
			invCheck.addOutgoing(invError);
			mCfgComponents.get("error").getStart().addIncoming(invError);
			transitions.add(invError);
			/*
			 * Transition End to flow
			 */
			// TODO: transformula
			final UnmodifiableTransFormula tfEndFlow =
					TransFormulaBuilder.getTrivialTransFormula(mSmtToolkit.getManagedScript());
			final IcfgInternalTransition endFlow = new IcfgInternalTransition(end, flow, mPayload, tfEndFlow);
			end.addOutgoing(endFlow);
			flow.addIncoming(endFlow);
			transitions.add(endFlow);
			// create new cfgComponent
			mCfgComponents.put(autid.toString(),
					new HybridCfgComponent(autid.toString(), start, end, locations, transitions));
		}
	}
	
	/*
	 * Transition methods
	 */
	private void transitionsToIcfg(final List<Transition> transitions) {
		// a transition in a hybrid automaton is simply an edge from one location to another.
		// guard and update can be merged with &&
		transitions.forEach(trans -> {
			// the source of the transition is the the end of the source CFG component
			final BoogieIcfgLocation source = mCfgComponents.get(Integer.toString(trans.getSourceId())).getEnd();
			// the target of the transition is the the start of the target CFG component
			final BoogieIcfgLocation target = mCfgComponents.get(Integer.toString(trans.getTargetId())).getStart();
			// TODO: transformula
			final UnmodifiableTransFormula transFormula =
					TransFormulaBuilder.getTrivialTransFormula(mSmtToolkit.getManagedScript());
			final IcfgInternalTransition transition =
					new IcfgInternalTransition(source, target, mPayload, transFormula);
			source.addOutgoing(transition);
			target.addIncoming(transition);
		});
	}
}
