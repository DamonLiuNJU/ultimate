/*
 * Copyright (C) 2017 Daniel Dietsch (dietsch@informatik.uni-freiburg.de)
 * Copyright (C) 2017 Marius Greitschus (greitsch@informatik.uni-freiburg.de)
 * Copyright (C) 2017 University of Freiburg
 *
 * This file is part of the ULTIMATE AbstractInterpretationV2 plug-in.
 *
 * The ULTIMATE AbstractInterpretationV2 plug-in is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * The ULTIMATE AbstractInterpretationV2 plug-in is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with the ULTIMATE AbstractInterpretationV2 plug-in. If not, see <http://www.gnu.org/licenses/>.
 *
 * Additional permission under GNU GPL version 3 section 7:
 * If you modify the ULTIMATE AbstractInterpretationV2 plug-in, or any covered work, by linking
 * or combining it with Eclipse RCP (or a modified version of Eclipse RCP),
 * containing parts covered by the terms of the Eclipse Public License, the
 * licensors of the ULTIMATE AbstractInterpretationV2 plug-in grant you additional permission
 * to convey the resulting work.
 */

package de.uni_freiburg.informatik.ultimate.plugins.analysis.abstractinterpretationv2.domain.transformula.poorman;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import de.uni_freiburg.informatik.ultimate.boogie.DeclarationInformation;
import de.uni_freiburg.informatik.ultimate.boogie.DeclarationInformation.StorageClass;
import de.uni_freiburg.informatik.ultimate.boogie.ast.AssumeStatement;
import de.uni_freiburg.informatik.ultimate.boogie.ast.Expression;
import de.uni_freiburg.informatik.ultimate.boogie.ast.HavocStatement;
import de.uni_freiburg.informatik.ultimate.boogie.ast.Statement;
import de.uni_freiburg.informatik.ultimate.boogie.ast.VariableLHS;
import de.uni_freiburg.informatik.ultimate.boogie.output.BoogiePrettyPrinter;
import de.uni_freiburg.informatik.ultimate.core.model.services.ILogger;
import de.uni_freiburg.informatik.ultimate.core.model.services.IUltimateServiceProvider;
import de.uni_freiburg.informatik.ultimate.logic.ApplicationTerm;
import de.uni_freiburg.informatik.ultimate.logic.Term;
import de.uni_freiburg.informatik.ultimate.logic.TermVariable;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.absint.IAbstractDomain;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.absint.IAbstractPostOperator;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.absint.IAbstractState;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.boogie.Boogie2SMT;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.boogie.IBoogieSymbolTableVariableProvider;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.boogie.MappedTerm2Expression;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.cfg.CfgSmtToolkit;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.cfg.structure.ICallAction;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.cfg.structure.IIcfg;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.cfg.structure.IReturnAction;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.cfg.structure.IcfgEdge;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.cfg.transitions.UnmodifiableTransFormula;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.cfg.variables.IProgramVar;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.cfg.variables.IProgramVarOrConst;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.smt.managedscript.ManagedScript;
import de.uni_freiburg.informatik.ultimate.plugins.analysis.abstractinterpretationv2.Activator;
import de.uni_freiburg.informatik.ultimate.plugins.analysis.abstractinterpretationv2.util.AbsIntUtil;
import de.uni_freiburg.informatik.ultimate.plugins.generator.rcfgbuilder.cfg.BoogieIcfgContainer;
import de.uni_freiburg.informatik.ultimate.plugins.generator.rcfgbuilder.cfg.CodeBlock;
import de.uni_freiburg.informatik.ultimate.plugins.generator.rcfgbuilder.cfg.CodeBlockFactory;
import de.uni_freiburg.informatik.ultimate.plugins.generator.rcfgbuilder.cfg.StatementSequence.Origin;

/**
 * The post operator for the poorman abstract domain. This post operator converts a given transformula to a sequence of
 * Boogie assumptions and calls the post operator of the Boogie-based backing domain accordingly.
 *
 * @author Marius Greitschus (greitsch@informatik.uni-freiburg.de)
 *
 */
public class PoormansAbstractPostOperator<BACKING extends IAbstractState<BACKING>>
		implements IAbstractPostOperator<PoormanAbstractState<BACKING>, IcfgEdge> {

	private final ILogger mLogger;
	private final IAbstractDomain<BACKING, IcfgEdge> mBackingDomain;
	private final Boogie2SMT mBoogie2Smt;
	private final ManagedScript mManagedScript;
	private final IUltimateServiceProvider mServices;
	private final CodeBlockFactory mCodeBlockFactory;
	private final Boogie2SmtSymbolTableTmpVars mBoogie2SmtSymbolTable;
	private final MappedTerm2Expression mMappedTerm2Expression;
	private final CfgSmtToolkit mCfgSmtToolkit;

	protected PoormansAbstractPostOperator(final IUltimateServiceProvider services, final IIcfg<?> root,
			final IAbstractDomain<BACKING, IcfgEdge> backingDomain,
			final IBoogieSymbolTableVariableProvider variableProvider) {
		mServices = services;
		mLogger = services.getLoggingService().getLogger(Activator.PLUGIN_ID);
		final BoogieIcfgContainer boogieIcfgContainer = AbsIntUtil.getBoogieIcfgContainer(root);
		mCodeBlockFactory = boogieIcfgContainer.getCodeBlockFactory();
		mBoogie2Smt = boogieIcfgContainer.getBoogie2SMT();
		assert variableProvider instanceof Boogie2SmtSymbolTableTmpVars;
		mBoogie2SmtSymbolTable = (Boogie2SmtSymbolTableTmpVars) variableProvider;

		mManagedScript = boogieIcfgContainer.getCfgSmtToolkit().getManagedScript();
		mBackingDomain = backingDomain;

		mMappedTerm2Expression = new MappedTerm2Expression(mBoogie2Smt.getTypeSortTranslator(),
				mBoogie2Smt.getBoogie2SmtSymbolTable(), mManagedScript);

		mCfgSmtToolkit = root.getCfgSmtToolkit();
	}

	@Override
	public List<PoormanAbstractState<BACKING>> apply(final PoormanAbstractState<BACKING> oldstate,
			final IcfgEdge transition) {
		return applyPost(oldstate, transition.getTransformula());
	}

	@Override
	public List<PoormanAbstractState<BACKING>> apply(final PoormanAbstractState<BACKING> stateBeforeLeaving,
			final PoormanAbstractState<BACKING> stateAfterLeaving, final IcfgEdge transition) {
		if (transition instanceof ICallAction) {
			return handleCallTransition(stateBeforeLeaving, stateAfterLeaving, (ICallAction) transition);
		} else if (transition instanceof IReturnAction) {
			return handleReturnTransition(stateBeforeLeaving, stateAfterLeaving, (IReturnAction) transition);
		} else {
			throw new UnsupportedOperationException(
					"This post operator should not receive a transition different from ICallAction and IReturnAction.");
		}
	}

	/**
	 * Applies a transformula to the old abstract state.
	 *
	 * @param oldstate
	 *            The old state to apply the transformula on.
	 * @param transformula
	 *            The transformula to apply.
	 * @return A list of backing states as the result of the application of the transformula on the old state.
	 */
	private List<PoormanAbstractState<BACKING>> applyPost(final PoormanAbstractState<BACKING> oldstate,
			final UnmodifiableTransFormula transformula) {
		// Prepare hashsets and maps that are filled in the call to obtainVariableMappingFromTransformula
		final Map<IProgramVarOrConst, IProgramVarOrConst> renamedInVars = new HashMap<>();
		final Set<IProgramVarOrConst> newOutVars = new HashSet<>();
		final Set<IProgramVarOrConst> newAuxVars = new HashSet<>();
		final Map<IProgramVarOrConst, IProgramVarOrConst> outVarRenaming = new HashMap<>();
		final Set<IProgramVarOrConst> addedVariables = new HashSet<>();
		final Set<IProgramVarOrConst> inAuxVars = new HashSet<>();

		// Construct the assume block
		final Set<TermVariable> variableRetainmentSet = new HashSet<>();
		variableRetainmentSet.addAll(transformula.getOutVars().values());
		variableRetainmentSet.addAll(transformula.getInVars().values());
		variableRetainmentSet.addAll(transformula.getAuxVars());
		final AssumeStatement assumeStmt = constructBoogieAssumeStatement(transformula, variableRetainmentSet);
		final HavocStatement havocStmt = constructBoogieHavocStatementOfUnmappedOutVars(transformula);

		final List<Statement> statementList = new ArrayList<>();
		statementList.add(assumeStmt);
		if (havocStmt != null) {
			statementList.add(havocStmt);
		}

		final CodeBlock codeBlock =
				mCodeBlockFactory.constructStatementSequence(null, null, statementList, Origin.IMPLEMENTATION);

		obtainVariableMappingFromTransformula(transformula, renamedInVars, newOutVars, newAuxVars, outVarRenaming,
				addedVariables, inAuxVars);

		final PoormanAbstractState<BACKING> preState =
				oldstate.renameVariables(renamedInVars).addVariables(addedVariables);

		// Compute the abstract post
		final List<BACKING> postStates = mBackingDomain.getPostOperator().apply(preState.getBackingState(), codeBlock);

		// Remove all added temporary variables from the symbol table
		mBoogie2SmtSymbolTable.clearTemporaryVariables();

		final List<PoormanAbstractState<BACKING>> returnList = new ArrayList<>();
		for (final BACKING state : postStates) {
			// Remove all variables that are the target of the renaming later on
			// TODO determine whether this will maybe lead to imprecision. I don't think so, but I'm not entirely sure.
			final Set<IProgramVarOrConst> removeOverwrittenOuts = state.getVariables().stream()
					.filter(var -> outVarRenaming.values().stream()
							.anyMatch(out -> var.getGloballyUniqueId().equals(out.getGloballyUniqueId())))
					.collect(Collectors.toSet());

			final BACKING newBackingState = state.removeVariables(removeOverwrittenOuts).removeVariables(inAuxVars)
					.renameVariables(outVarRenaming);
			returnList.add(new PoormanAbstractState<>(mServices, mBackingDomain, newBackingState));
		}
		return returnList;
	}

	/**
	 * Variables that occur in the inVars but not in the outVars are implicitly havoc'ed. Also, if a program variable
	 * occurs in the outVars and is mapped to a {@link TermVariable} that is not occurring in the {@link Term}, the
	 * variable is havoc'ed implicitly. This method constructs such a {@link HavocStatement}.
	 *
	 * @param transformula
	 *            The transformula.
	 * @return A havoc statement in which the implicitly havoc'ed variables are explicitly havoc'ed.
	 */
	private HavocStatement constructBoogieHavocStatementOfUnmappedOutVars(final UnmodifiableTransFormula transformula) {
		// Variable occurs in inVars, but not in outVars
		final Set<IProgramVar> havocedVars = transformula.getInVars().keySet().stream()
				.filter(in -> !transformula.getOutVars().keySet().contains(in)).collect(Collectors.toSet());

		final Set<TermVariable> tvSet = new HashSet<>(Arrays.asList(transformula.getFormula().getFreeVars()));

		// Variable in outVars has a mapping to a TermVariable that does not occur in the term of the transformula
		havocedVars
				.addAll(transformula.getOutVars().entrySet().stream().filter(entry -> !tvSet.contains(entry.getValue()))
						.map(entry -> entry.getKey()).collect(Collectors.toSet()));

		if (havocedVars.isEmpty()) {
			return null;
		}

		final List<VariableLHS> variableLHSs = new ArrayList<>();

		for (final IProgramVar var : havocedVars) {
			variableLHSs.add(new VariableLHS(null, mBoogie2Smt.getTypeSortTranslator().getType(var.getSort()),
					var.getGloballyUniqueId(), new DeclarationInformation(StorageClass.IMPLEMENTATION, null)));

			// Note: The following is a somewhat "dirty" hack, but prevents that the lookup for the variable in the
			// symbol table fails, since the declaration information that is used here together with the variable's name
			// is inconsistent with the default symbol table's entries. Therefore, the lookup in
			// Boogie2SmtSymbolTable.getBoogieVar would fail (line 210, AssertionError).
			mBoogie2SmtSymbolTable.addTemporaryVariable(var);
		}

		final HavocStatement havoc =
				new HavocStatement(null, variableLHSs.toArray(new VariableLHS[variableLHSs.size()]));

		if (mLogger.isDebugEnabled()) {
			mLogger.debug("The following variables are havoced by this transformula: " + variableLHSs);
		}

		return havoc;
	}

	private List<PoormanAbstractState<BACKING>> handleCallTransition(
			final PoormanAbstractState<BACKING> stateBeforeLeaving,
			final PoormanAbstractState<BACKING> stateAfterLeaving, final ICallAction transition) {

		// if (true) {
		// return null;
		// }

		final String calledProcedure = transition.getSucceedingProcedure();
		final UnmodifiableTransFormula globalVarsAssignment =
				mCfgSmtToolkit.getOldVarsAssignmentCache().getGlobalVarsAssignment(calledProcedure);

		final List<PoormanAbstractState<BACKING>> afterGlobalVars = applyPost(stateBeforeLeaving, globalVarsAssignment);

		final UnmodifiableTransFormula oldVarAssignment =
				mCfgSmtToolkit.getOldVarsAssignmentCache().getOldVarsAssignment(calledProcedure);

		final List<PoormanAbstractState<BACKING>> localApplication =
				applyPost(stateBeforeLeaving, transition.getLocalVarsAssignment());

		final List<PoormanAbstractState<BACKING>> returnList = new ArrayList<>();
		for (final PoormanAbstractState<BACKING> state : localApplication) {
			returnList.addAll(applyPost(state, transition.getTransformula()));
		}
		return returnList;
	}

	private List<PoormanAbstractState<BACKING>> handleReturnTransition(
			final PoormanAbstractState<BACKING> stateBeforeLeaving,
			final PoormanAbstractState<BACKING> stateAfterLeaving, final IReturnAction transition) {
		// TODO Auto-generated method stub
		return null;
	}

	/**
	 * Fills for a given transformula the given maps and sets with sensible values, depending on the variables in the
	 * transformula. See its usage in, e.g., the {@link #apply(PoormanAbstractState, IcfgEdge)} function.
	 *
	 * @param transformula
	 *            The transformula.
	 * @param renamedInVars
	 *            Is filled with a mapping of program vars occurring in the program to fresh program vars corresponding
	 *            to the inVar mapping of the transformula.
	 * @param newOutVars
	 *            Is filled with fresh variables for outVars of the transformula.
	 * @param newAuxVars
	 *            Is filled with fresh variables for the auxVars of the transformula.
	 * @param outVarRenaming
	 *            Is filled with the mapping of program variables to outVars of the transformula to be able to rename
	 *            the variables in the abstract state back to their original ones after applying the abstract post.
	 * @param addedVariables
	 *            Is filled with all variables that need to be added to the abstract state in order to be able to apply
	 *            the abstract post for the given transformula correctly.
	 * @param inAuxVars
	 *            Is filled with all variables that need to be removed from the computed post state after applying the
	 *            post operator to restore the original variables.
	 */
	private void obtainVariableMappingFromTransformula(final UnmodifiableTransFormula transformula,
			final Map<IProgramVarOrConst, IProgramVarOrConst> renamedInVars, final Set<IProgramVarOrConst> newOutVars,
			final Set<IProgramVarOrConst> newAuxVars, final Map<IProgramVarOrConst, IProgramVarOrConst> outVarRenaming,
			final Set<IProgramVarOrConst> addedVariables, final Set<IProgramVarOrConst> inAuxVars) {

		assert renamedInVars.isEmpty();
		assert newOutVars.isEmpty();
		assert newAuxVars.isEmpty();
		assert outVarRenaming.isEmpty();
		assert addedVariables.isEmpty();
		assert inAuxVars.isEmpty();

		// Collect inVars that are to be renamed.
		// If in a state there is variable x and the transformula's inVars state that {x -> x_1}, then rename x to x_1
		// in the current state.
		renamedInVars.putAll(transformula.getInVars().entrySet().stream()
				.collect(Collectors.toMap(entry -> entry.getKey(), entry -> getFreshProgramVar(entry.getValue()))));

		// Collect the names of all outVars in the transformula and add fresh variables that are to be added as fresh
		// variables to the state.
		// For example, if the outVars state that {x -> x_2}, construct a fresh variable x_2 which is added later to the
		// state.
		// In the case where the outVar is also an inVar, e.g. inVars = {x -> x_1} and outVars = {x -> x_1}, x_1 has
		// already been added to the state in the inVars and will not be added again, here.
		for (final Entry<IProgramVar, TermVariable> entry : transformula.getOutVars().entrySet()) {
			if (!renamedInVars.values().stream()
					.anyMatch(var -> var.getGloballyUniqueId().equals(entry.getValue().getName()))) {
				final IProgramVarOrConst newOutVar = getFreshProgramVar(entry.getValue());
				newOutVars.add(newOutVar);
				outVarRenaming.put(newOutVar, entry.getKey());
			} else {
				// In this case, the outVar is also an inVar. Thus, the corresponding inVar needs to be added to the
				// renaming map to be able to restore the state after the application of abstract post.
				final Entry<IProgramVarOrConst, IProgramVarOrConst> correspondingInVar = renamedInVars.entrySet()
						.stream().filter(e -> e.getValue().getGloballyUniqueId().equals(entry.getValue().getName()))
						.findFirst().orElseGet(() -> {
							throw new UnsupportedOperationException();
						});
				outVarRenaming.put(correspondingInVar.getValue(), correspondingInVar.getKey());
			}
		}

		// Collect the auxVars of the transformula that are to be added to the abstract state.
		for (final TermVariable auxVar : transformula.getAuxVars()) {
			if (!renamedInVars.values().stream().anyMatch(var -> var.getGloballyUniqueId().equals(auxVar.getName()))
					&& !newOutVars.stream().anyMatch(var -> var.getGloballyUniqueId().equals(auxVar.getName()))) {
				final IProgramVarOrConst newAuxVar = getFreshProgramVar(auxVar);
				newAuxVars.add(newAuxVar);
			}
		}

		addedVariables.addAll(Stream.concat(newOutVars.stream(), newAuxVars.stream()).collect(Collectors.toSet()));

		// Add temporary variables to the symbol table
		final Set<IProgramVarOrConst> tempVars = new HashSet<>();
		tempVars.addAll(renamedInVars.values());
		tempVars.addAll(newOutVars);
		tempVars.addAll(newAuxVars);
		mBoogie2SmtSymbolTable
				.addTemporaryVariables(tempVars.stream().map(var -> (IProgramVar) var).collect(Collectors.toSet()));

		// Collect in and aux vars that are removed later from the abstract state
		inAuxVars
				.addAll(renamedInVars.values().stream()
						.filter(var -> !transformula.getOutVars().values().stream()
								.anyMatch(out -> out.getName().equals(var.getGloballyUniqueId())))
						.collect(Collectors.toSet()));
		inAuxVars.addAll(newAuxVars);

		// Some logging output to debug renaming
		if (mLogger.isDebugEnabled()) {
			mLogger.debug("   Will rename the following variables in the pre state:");
			renamedInVars.entrySet().stream()
					.forEach(entry -> mLogger.debug("     " + entry.getKey().getGloballyUniqueId() + " ("
							+ entry.getKey().hashCode() + ") --> " + entry.getValue().getGloballyUniqueId() + " ("
							+ entry.getValue().hashCode() + ")"));
			mLogger.debug("   Will add the following variables to the pre state: " + addedVariables);

			mLogger.debug("   Will remove the following variables from the post state(s): " + inAuxVars);
			mLogger.debug("   Will rename the following variables from the post state(s): " + outVarRenaming);
		}
	}

	/**
	 * Constructs a Boogie {@link CodeBlock} in which the transformula is converted into an assume statement.
	 *
	 * @param transformula
	 *            The transformula to construct an assume statement for.
	 * @param variableRetainmentSet
	 *            The set of variables whose names should be looked up in the temporary variable set of the symbol table
	 *            instead of the Boogie symbol table itself.
	 * @return A {@link CodeBlock} containing the conversion of the transformula to an assume statement.
	 */
	private AssumeStatement constructBoogieAssumeStatement(final UnmodifiableTransFormula transformula,
			final Set<TermVariable> variableRetainmentSet) {
		final Expression termExpression =
				mMappedTerm2Expression.translate(transformula.getFormula(), variableRetainmentSet);
		final AssumeStatement assume = new AssumeStatement(termExpression.getLoc(), termExpression);

		if (mLogger.isDebugEnabled()) {
			mLogger.debug("Transformula: " + transformula);
			mLogger.debug("Constructed assume expression: " + BoogiePrettyPrinter.print(termExpression));
		}

		return assume;
	}

	private IProgramVarOrConst getFreshProgramVar(final TermVariable var) {
		return new IProgramVar() {

			private static final long serialVersionUID = 4924620166368141045L;

			private TermVariable mTerm;

			@Override
			public String getGloballyUniqueId() {
				return var.getName();
			}

			@Override
			public boolean isGlobal() {
				return false;
			}

			@Override
			public Term getTerm() {
				return getTermVariable();
			}

			@Override
			public boolean isOldvar() {
				return false;
			}

			@Override
			public TermVariable getTermVariable() {
				if (mTerm == null) {
					mTerm = mManagedScript.variable(getGloballyUniqueId(), var.getSort());
				}
				return mTerm;
			}

			@Override
			public String getProcedure() {
				return null;
			}

			@Override
			public ApplicationTerm getPrimedConstant() {
				return null;
			}

			@Override
			public ApplicationTerm getDefaultConstant() {
				return null;
			}

			@Override
			public String toString() {
				return getGloballyUniqueId();
			}
		};
	}
}