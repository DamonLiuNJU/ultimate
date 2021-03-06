/*
 * Copyright (C) 2017 Alexander Nutz (nutz@informatik.uni-freiburg.de)
 * Copyright (C) 2017 University of Freiburg
 *
 * This file is part of the ULTIMATE CACSL2BoogieTranslator plug-in.
 *
 * The ULTIMATE CACSL2BoogieTranslator plug-in is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * The ULTIMATE CACSL2BoogieTranslator plug-in is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with the ULTIMATE CACSL2BoogieTranslator plug-in. If not, see <http://www.gnu.org/licenses/>.
 *
 * Additional permission under GNU GPL version 3 section 7:
 * If you modify the ULTIMATE CACSL2BoogieTranslator plug-in, or any covered work, by linking
 * or combining it with Eclipse RCP (or a modified version of Eclipse RCP),
 * containing parts covered by the terms of the Eclipse Public License, the
 * licensors of the ULTIMATE CACSL2BoogieTranslator plug-in grant you additional permission
 * to convey the resulting work.
 */
package de.uni_freiburg.informatik.ultimate.cdt.translation.implementation.result;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import de.uni_freiburg.informatik.ultimate.boogie.ast.Declaration;
import de.uni_freiburg.informatik.ultimate.boogie.ast.Statement;
import de.uni_freiburg.informatik.ultimate.cdt.translation.implementation.container.AuxVarInfo;
import de.uni_freiburg.informatik.ultimate.core.lib.models.annotation.Overapprox;

public class ExpressionResultBuilder {

	private final List<Statement> mStatements = new ArrayList<>();
	private LRValue mLrVal;
	private final List<Declaration> mDeclarations = new ArrayList<>();
	private final List<Overapprox> mOverappr = new ArrayList<>();
//	private final Map<VariableDeclaration, ILocation> mAuxVars = new LinkedHashMap<>();
	private final Set<AuxVarInfo> mAuxVars = new HashSet<>();
	private final List<ExpressionResult> mNeighbourUnionFields = new ArrayList<>();


//	public ExpressionResultBuilder(final ExpressionResult assignmentExprRes) {
//		mStatements.addAll(assignmentExprRes.mStmt);
//		mDeclarations.addAll(assignmentExprRes.mDecl);
//		mOverappr.addAll(assignmentExprRes.mOverappr);
//		mAuxVars.putAll(assignmentExprRes.mAuxVars);
//		mNeighbourUnionFields.addAll(assignmentExprRes.mOtherUnionFields);
//		mLrVal = assignmentExprRes.mLrVal;
//	}

	/**
	 * Creates an ExpressionResultBuidler with empty fields.
	 */
	public ExpressionResultBuilder() {
		// do nothing
	}

	/**
	 * copy constructor
	 *
	 * @param builderIn
	 */
	public ExpressionResultBuilder(final ExpressionResultBuilder original) {
		mStatements.addAll(original.mStatements);
		mDeclarations.addAll(original.mDeclarations);
		mOverappr.addAll(original.mOverappr);
//		mAuxVars.putAll(original.mAuxVars);
		mAuxVars.addAll(original.mAuxVars);
		mNeighbourUnionFields.addAll(original.mNeighbourUnionFields);
		mLrVal = original.mLrVal;
	}

	public ExpressionResultBuilder setLrVal(final LRValue val) {
		if (mLrVal != null) {
			throw new IllegalStateException("LRValue has already been set");
		}
		mLrVal = val;
		return this;
	}

	public ExpressionResultBuilder addStatement(final Statement stm) {
		mStatements.add(stm);
		return this;
	}
	public <T extends Statement> ExpressionResultBuilder addStatements(final Collection<T> stms) {
		mStatements.addAll(stms);
		return this;
	}

	public ExpressionResultBuilder addDeclaration(final Declaration stm) {
		mDeclarations.add(stm);
		return this;
	}
	public <T extends Declaration> ExpressionResultBuilder addDeclarations(final Collection<T> stms) {
		mDeclarations.addAll(stms);
		return this;
	}

	public ExpressionResultBuilder addOverapprox(final Overapprox oa) {
		mOverappr.add(oa);
		return this;
	}
	public ExpressionResultBuilder addOverapprox(final Collection<Overapprox> oas) {
		mOverappr.addAll(oas);
		return this;
	}
	public ExpressionResultBuilder addAuxVar(final AuxVarInfo auxvar) {
		mAuxVars.add(auxvar);
		return this;
	}

	public ExpressionResultBuilder addAuxVars(final Collection<AuxVarInfo> auxvar) {
		mAuxVars.addAll(auxvar);
		return this;
	}

//	public ExpressionResultBuilder putAuxVar(final VariableDeclaration avDecl, final ILocation avLoc) {
//		mAuxVars.put(avDecl, avLoc);
//		return this;
//	}
//	public ExpressionResultBuilder putAuxVars(final Map<VariableDeclaration, ILocation> auxVars) {
//		mAuxVars.putAll(auxVars);
//		return this;
//	}

	public ExpressionResultBuilder addNeighbourUnionField(final ExpressionResult unionField) {
		mNeighbourUnionFields.add(unionField);
		return this;
	}
	public ExpressionResultBuilder addNeighbourUnionFields(final Collection<ExpressionResult> unionFields) {
		mNeighbourUnionFields.addAll(unionFields);
		return this;
	}

	/**
	 *
	 * @param exprResult
	 * @return "this", i.e., the object it is called upon
	 */
	public ExpressionResultBuilder addAllExceptLrValue(final ExpressionResult exprResult) {
		addStatements(exprResult.getStatements());
		addAllExceptLrValueAndStatements(exprResult);
		return this;
	}

	public ExpressionResultBuilder addAllExceptLrValueAndStatements(final ExpressionResult exprResult) {
		addDeclarations(exprResult.getDeclarations());
		addOverapprox(exprResult.getOverapprs());
		addAuxVars(exprResult.getAuxVars());
		addNeighbourUnionFields(exprResult.getNeighbourUnionFields());
		return this;
	}

	public ExpressionResult build() {
		return new ExpressionResult(mStatements, mLrVal, mDeclarations, mAuxVars, mOverappr, mNeighbourUnionFields);
	}

	public LRValue getLrVal() {
		return mLrVal;
	}

	public List<Statement> getStatements() {
		return Collections.unmodifiableList(mStatements);
	}

	public List<Declaration> getDeclarations() {
		return Collections.unmodifiableList(mDeclarations);
	}

	public List<Overapprox> getOverappr() {
		return Collections.unmodifiableList(mOverappr);
	}

//	public Map<VariableDeclaration, ILocation> getAuxVars() {
	public Set<AuxVarInfo> getAuxVars() {
		return Collections.unmodifiableSet(mAuxVars);
	}

	public List<ExpressionResult> getNeighbourUnionFields() {
		return Collections.unmodifiableList(mNeighbourUnionFields);
	}

	public void resetLrVal(final LRValue rVal) {
		if (mLrVal == null) {
			throw new IllegalStateException("use setLrVal instead");
		}
		mLrVal = rVal;
	}

	public void setOrResetLrVal(final LRValue lrVal) {
		if (mLrVal == null) {
			setLrVal(lrVal);
		} else {
			resetLrVal(lrVal);
		}

	}

	/**
	 * Clears the current list of statements and puts the given list in its place.
	 *
	 * @param newStatements
	 */
	public void resetStatements(final List<Statement> newStatements) {
		mStatements.clear();
		mStatements.addAll(newStatements);
	}
}