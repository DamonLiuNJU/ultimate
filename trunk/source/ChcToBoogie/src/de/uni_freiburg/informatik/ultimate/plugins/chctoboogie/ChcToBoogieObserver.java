/*
 * Copyright (C) 2018 Alexander Nutz (nutz@informatik.uni-freiburg.de)
 * Copyright (C) 2018 University of Freiburg
 *
 * This file is part of the ChcToBoogie plug-in.
 *
 * The ChcToBoogie plug-in is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * The ChcToBoogie plug-in is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with the ChcToBoogie plug-in. If not, see <http://www.gnu.org/licenses/>.
 *
 * Additional permission under GNU GPL version 3 section 7:
 * If you modify the ChcToBoogie plug-in, or any covered work, by linking
 * or combining it with Eclipse RCP (or a modified version of Eclipse RCP),
 * containing parts covered by the terms of the Eclipse Public License, the
 * licensors of the ChcToBoogie plug-in grant you additional permission
 * to convey the resulting work.
 */
package de.uni_freiburg.informatik.ultimate.plugins.chctoboogie;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import de.uni_freiburg.informatik.ultimate.boogie.BoogieLocation;
import de.uni_freiburg.informatik.ultimate.boogie.ExpressionFactory;
import de.uni_freiburg.informatik.ultimate.boogie.ast.ASTType;
import de.uni_freiburg.informatik.ultimate.boogie.ast.AssertStatement;
import de.uni_freiburg.informatik.ultimate.boogie.ast.AssumeStatement;
import de.uni_freiburg.informatik.ultimate.boogie.ast.Attribute;
import de.uni_freiburg.informatik.ultimate.boogie.ast.Body;
import de.uni_freiburg.informatik.ultimate.boogie.ast.CallStatement;
import de.uni_freiburg.informatik.ultimate.boogie.ast.Declaration;
import de.uni_freiburg.informatik.ultimate.boogie.ast.Expression;
import de.uni_freiburg.informatik.ultimate.boogie.ast.IfStatement;
import de.uni_freiburg.informatik.ultimate.boogie.ast.PrimitiveType;
import de.uni_freiburg.informatik.ultimate.boogie.ast.Procedure;
import de.uni_freiburg.informatik.ultimate.boogie.ast.Specification;
import de.uni_freiburg.informatik.ultimate.boogie.ast.Statement;
import de.uni_freiburg.informatik.ultimate.boogie.ast.Unit;
import de.uni_freiburg.informatik.ultimate.boogie.ast.VarList;
import de.uni_freiburg.informatik.ultimate.boogie.ast.VariableDeclaration;
import de.uni_freiburg.informatik.ultimate.boogie.ast.VariableLHS;
import de.uni_freiburg.informatik.ultimate.boogie.type.BoogieType;
import de.uni_freiburg.informatik.ultimate.core.lib.models.BasePayloadContainer;
import de.uni_freiburg.informatik.ultimate.core.model.models.IBoogieType;
import de.uni_freiburg.informatik.ultimate.core.model.models.IElement;
import de.uni_freiburg.informatik.ultimate.core.model.models.ILocation;
import de.uni_freiburg.informatik.ultimate.core.model.models.ModelType;
import de.uni_freiburg.informatik.ultimate.core.model.models.annotation.IAnnotations;
import de.uni_freiburg.informatik.ultimate.core.model.observers.IUnmanagedObserver;
import de.uni_freiburg.informatik.ultimate.core.model.services.ILogger;
import de.uni_freiburg.informatik.ultimate.core.model.services.IUltimateServiceProvider;
import de.uni_freiburg.informatik.ultimate.lib.treeautomizer.HCSymbolTable;
import de.uni_freiburg.informatik.ultimate.lib.treeautomizer.HcBodyVar;
import de.uni_freiburg.informatik.ultimate.lib.treeautomizer.HcHeadVar;
import de.uni_freiburg.informatik.ultimate.lib.treeautomizer.HornAnnot;
import de.uni_freiburg.informatik.ultimate.lib.treeautomizer.HornClause;
import de.uni_freiburg.informatik.ultimate.lib.treeautomizer.HornClausePredicateSymbol;
import de.uni_freiburg.informatik.ultimate.lib.treeautomizer.HornUtilConstants;
import de.uni_freiburg.informatik.ultimate.logic.Sort;
import de.uni_freiburg.informatik.ultimate.logic.Term;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.boogie.Boogie2SmtSymbolTable;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.boogie.Term2Expression;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.boogie.TypeSortTranslator;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.smt.managedscript.ManagedScript;
import de.uni_freiburg.informatik.ultimate.util.datastructures.relation.HashRelation;

/**
 *
 * @author Alexander Nutz (nutz@informatik.uni-freiburg.de)
 *
 */
public class ChcToBoogieObserver implements IUnmanagedObserver {

	private final ILogger mLogger;
	private final IUltimateServiceProvider mServices;

	private Unit mBoogieUnit;

	private Term2Expression mTerm2Expression;
	private HornClausePredicateSymbol mBottomPredSym;
	private final String mNameOfMainEntryPointProc;
	private ManagedScript mManagedScript;
	private TypeSortTranslator mTypeSortTanslator;
	private HCSymbolTable mHcSymbolTable;
	private Boogie2SmtSymbolTable mBoogie2SmtSymbolTable;
	private final ILocation mLocation;

	public ChcToBoogieObserver(final ILogger logger, final IUltimateServiceProvider services) {
			//final ManagedScript managedScript) {
		mLogger = logger;
		mServices = services;
		mNameOfMainEntryPointProc = "Ultimate.START";
		mLocation = new BoogieLocation(this.getClass().getName(), 0, 0, 0, 0);
	}

	@Override
	public void init(final ModelType modelType, final int currentModelIndex, final int numberOfModels) {
		// no initialization needed
	}

	@Override
	public void finish() throws Throwable {
		// not needed
	}

	@Override
	public boolean performedChanges() {
		return false;
	}

	public IElement getModel() {
		return mBoogieUnit;
//		return mResult;
	}

	@Override
	public boolean process(final IElement root) throws Exception {
		final BasePayloadContainer rootNode = (BasePayloadContainer) root;

		final Map<String, IAnnotations> st = rootNode.getPayload().getAnnotations();
		final HornAnnot annot = (HornAnnot) st.get(HornUtilConstants.HORN_ANNOT_NAME);
		mLogger.debug(annot.getAnnotationsAsMap().get(HornUtilConstants.HORN_ANNOT_NAME));


		final List<HornClause> hornClausesRaw =
				(List<HornClause>) annot.getAnnotationsAsMap().get(HornUtilConstants.HORN_ANNOT_NAME);
		mHcSymbolTable = annot.getSymbolTable();

		mBottomPredSym = mHcSymbolTable.getFalseHornClausePredicateSymbol();


		mManagedScript = annot.getScript();

		{
			final HashRelation<Sort, IBoogieType> sortToType = new HashRelation<>();
			sortToType.addPair(mManagedScript.getScript().sort("Int"), BoogieType.TYPE_INT);
			sortToType.addPair(mManagedScript.getScript().sort("Real"), BoogieType.TYPE_REAL);
			sortToType.addPair(mManagedScript.getScript().sort("Bool"), BoogieType.TYPE_BOOL);
			mTypeSortTanslator = new TypeSortTranslator(sortToType, mManagedScript.getScript(), mServices);
		}


//		{
//			mBoogie2SmtSymbolTable = new Boogie2SmtSymbolTable(boogieDeclarations,
//					mManagedScript, mTypeSortTanslator);
//		}


//		mTerm2Expression = new Term2Expression(mTypeSortTanslator, mBoogie2SmtSymbolTable, mManagedScript);
		mTerm2Expression = new Term2Expression(mTypeSortTanslator, mHcSymbolTable, mManagedScript);



		final HashRelation<HornClausePredicateSymbol, HornClause> hornClauseHeadPredicateToHornClauses =
				sortHornClausesByHeads(hornClausesRaw);

		generateBoogieAst(hornClauseHeadPredicateToHornClauses);

		return true;
	}

	public HashRelation<HornClausePredicateSymbol, HornClause> sortHornClausesByHeads(final List<HornClause> hornClausesRaw) {
		final HashRelation<HornClausePredicateSymbol, HornClause> hornClauseHeadPredicateToHornClauses =
				new HashRelation<>();

		for (final HornClause hc : hornClausesRaw) {
			if (hc.isHeadFalse()) {
				hornClauseHeadPredicateToHornClauses.addPair(mBottomPredSym, hc);
			} else {
				hornClauseHeadPredicateToHornClauses.addPair(hc.getHeadPredicate(), hc);
			}
		}
		return hornClauseHeadPredicateToHornClauses;
	}

	private void generateBoogieAst(
			final HashRelation<HornClausePredicateSymbol, HornClause> hornClauseHeadPredicateToHornClauses) {

		final List<Declaration> declarations = new ArrayList<>();
		final ILocation loc = getLoc();

		for (final HornClausePredicateSymbol headPredSymbol : hornClauseHeadPredicateToHornClauses.getDomain()) {

			List<Statement> nondetSwitch = null;

			final Set<HcBodyVar> allBodyPredVariables = new HashSet<>();

			for (final HornClause hornClause : hornClauseHeadPredicateToHornClauses.getImage(headPredSymbol)) {

				allBodyPredVariables.addAll(hornClause.getBodyPredVariables());

				final List<Statement> branchBody = new ArrayList<>();
				final Statement assume =
						new AssumeStatement(loc, mTerm2Expression.translate(hornClause.getConstraintFormula()));
				branchBody.add(assume);

				for (int i = 0; i < hornClause.getNoBodyPredicates(); i++) {
					final HornClausePredicateSymbol bodyPredSym = hornClause.getBodyPredicates().get(i);
					final List<Term> bodyPredArgs = hornClause.getBodyPredToArgs().get(i);

					final CallStatement call = new CallStatement(loc, false, new VariableLHS[0],
							predSymToMethodName(bodyPredSym),
							bodyPredArgs.stream().map(t -> mTerm2Expression.translate(t)).collect(Collectors.toList())
								.toArray(new Expression[bodyPredArgs.size()]));
					branchBody.add(call);
				}

				nondetSwitch = addIteBranch(loc, nondetSwitch, branchBody);
			}

			final VarList[] inParams = getInParamsForHeadPredSymbol(loc, headPredSymbol);


			final List<VariableDeclaration> localVarDecs = new ArrayList<>();
			updateLocalVarDecs(localVarDecs, allBodyPredVariables, loc);

			final VariableDeclaration[] localVars;
			{
				localVars = localVarDecs == null
						? new VariableDeclaration[0]
						: localVarDecs.toArray(new VariableDeclaration[localVarDecs.size()]);
			}

			assert !nondetSwitch.stream().anyMatch(Objects::isNull);
			final Statement[] block = nondetSwitch.toArray(new Statement[nondetSwitch.size()]);
			final Body body = new Body(loc, localVars, block);

			final Procedure proc =
					new Procedure(loc, new Attribute[0], predSymToMethodName(headPredSymbol), new String[0],
							inParams, new VarList[0],
							new Specification[0], body);
			declarations.add(proc);
		}

		// add the main entry point
		declarations.add(constructMainEntryPointProcedure(loc));

		mBoogieUnit = new Unit(loc,
				declarations.toArray(new Declaration[declarations.size()]));
	}

	private ILocation getLoc() {
		return mLocation;
	}

	private void updateLocalVarDecs(final List<VariableDeclaration> localVarDecs, final Set<HcBodyVar> bpvs,
			final ILocation loc) {
		for (final HcBodyVar bodyPredVar : bpvs) {
//			final String boogieVarName = bodyPredVar.getName();
			final String boogieVarName = bodyPredVar.getGloballyUniqueId();
			final Sort sort = bodyPredVar.getSort();
			final VarList varList = new VarList(loc, new String[] { boogieVarName }, getCorrespondingAstType(loc, sort));

//			mManagedScript.lock(mBoogie2SmtSymbolTable);
//			final LocalBoogieVar boogieVar = mBoogie2SmtSymbolTable.constructLocalBoogieVar(boogieVarName, procName,
//					mTypeSortTanslator.getType(sort), varList,
//					new DeclarationInformation(StorageClass.LOCAL, procName));
//			mManagedScript.unlock(mBoogie2SmtSymbolTable);

			localVarDecs.add(new VariableDeclaration(loc, new Attribute[0], new VarList[] { varList }));
		}
	}


	/**
	 * For each procedure we create here, the inParams are determined by the signature of the HornClausePredicateSymbol
	 * that is associated with the procedure.
	 * This methods computes those inParams in the right format.
	 *
	 * @param loc
	 * @param headPredSym
	 * @return
	 */
	private VarList[] getInParamsForHeadPredSymbol(final ILocation loc,
			final HornClausePredicateSymbol headPredSym) {
		final VarList[] result = new VarList[headPredSym.getArity()];
//		for (HcHeadVar hchv : mHcSymbolTable.getHcHeadVarsForPredSym(headPredSym)) {
		for (int i = 0; i < headPredSym.getArity(); i++) {
			final HcHeadVar hchv = mHcSymbolTable.getHcHeadVarsForPredSym(headPredSym).get(i);
			final Sort sort = hchv.getTermVariable().getSort();
			final ASTType correspondingType = getCorrespondingAstType(loc, sort);
			final VarList vl = new VarList(loc, new String[] { hchv.getGloballyUniqueId() }, correspondingType);
			result[i] = vl;
		}
		return result;

//		final VarList[] result = new VarList[headPredSym.getArity()];
//
//		for (int i = 0; i < headPredSym.getArity(); i++) {
//			final Sort sort = headPredSym.getParameterSorts().get(i);
//			final ASTType correspondingType = getCorrespondingAstType(loc, sort);
//			final String varName = getHeadVarName(i, sort);
//			final VarList vl = new VarList(loc, new String[] { varName }, correspondingType);
//			result[i] = vl;
//		}
//
//		return result;
	}

	private ASTType getCorrespondingAstType(final ILocation loc, final Sort sort) {
		if (sort.getName().equals("Int")) {
			return new PrimitiveType(loc, BoogieType.TYPE_INT, "int");
		} else if (sort.getName().equals("Real")) {
			return new PrimitiveType(loc, BoogieType.TYPE_REAL, "real");
		} else if (sort.getName().equals("Bool")) {
			return new PrimitiveType(loc, BoogieType.TYPE_BOOL, "bool");
		} else {
			throw new AssertionError("case not implemented");

		}
	}

	private String getHeadVarName(final int i, final Sort sort) {
		return mHcSymbolTable.getHeadVarName(i, sort);
	}

	private Declaration constructMainEntryPointProcedure(final ILocation loc) {

		final Statement callToBottomProc = new CallStatement(loc, false, new VariableLHS[0], predSymToMethodName(mBottomPredSym),
				new Expression[0]);

		final Statement assertFalse = new AssertStatement(loc,
				ExpressionFactory.createBooleanLiteral(loc, false));

		final Body body = new Body(loc, new VariableDeclaration[0],
				new Statement[] {
						callToBottomProc,
						assertFalse
				});

		return new Procedure(loc, new Attribute[0], mNameOfMainEntryPointProc, new String[0],
				new VarList[0], new VarList[0], new Specification[0], body);
	}

	private List<Statement> addIteBranch(final ILocation loc, final List<Statement> nondetSwitch,
			final List<Statement> branchBody) {
		if (nondetSwitch == null) {
			return branchBody;
		} else if (nondetSwitch.size() == 1 && nondetSwitch.get(0) instanceof IfStatement) {
			final Statement[] oldIfStm = new Statement[] { nondetSwitch.get(0)};

			final Statement newIfStm = new IfStatement(loc,
					ExpressionFactory.constructBooleanWildCardExpression(loc),
					oldIfStm,
					branchBody.toArray(new Statement[branchBody.size()]));

			return Collections.singletonList(newIfStm);
		} else {
			assert nondetSwitch.get(0) instanceof AssumeStatement || nondetSwitch.get(0) instanceof CallStatement;
			final Statement newIfStm = new IfStatement(loc,
					ExpressionFactory.constructBooleanWildCardExpression(loc),
					nondetSwitch.toArray(new Statement[nondetSwitch.size()]),
					branchBody.toArray(new Statement[branchBody.size()]));

			return Collections.singletonList(newIfStm);
		}
	}

	private String predSymToMethodName(final HornClausePredicateSymbol predSym) {
//		return predSym.getName();
		return mHcSymbolTable.getMethodNameForPredSymbol(predSym);
	}
}
