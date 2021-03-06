/*
 * Copyright (C) 2013-2015 Alexander Nutz (nutz@informatik.uni-freiburg.de)
 * Copyright (C) 2012-2015 Markus Lindenmann (lindenmm@informatik.uni-freiburg.de)
 * Copyright (C) 2012-2015 Matthias Heizmann (heizmann@informatik.uni-freiburg.de)
 * Copyright (C) 2015 University of Freiburg
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
/**
 * Class that handles translation of memory related operations.
 */
package de.uni_freiburg.informatik.ultimate.cdt.translation.implementation.base.chandler;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import org.eclipse.cdt.core.dom.ast.IASTBinaryExpression;
import org.eclipse.cdt.core.dom.ast.IASTNode;

import de.uni_freiburg.informatik.ultimate.boogie.DeclarationInformation;
import de.uni_freiburg.informatik.ultimate.boogie.DeclarationInformation.StorageClass;
import de.uni_freiburg.informatik.ultimate.boogie.ExpressionFactory;
import de.uni_freiburg.informatik.ultimate.boogie.StatementFactory;
import de.uni_freiburg.informatik.ultimate.boogie.ast.ASTType;
import de.uni_freiburg.informatik.ultimate.boogie.ast.ArrayAccessExpression;
import de.uni_freiburg.informatik.ultimate.boogie.ast.ArrayLHS;
import de.uni_freiburg.informatik.ultimate.boogie.ast.ArrayStoreExpression;
import de.uni_freiburg.informatik.ultimate.boogie.ast.ArrayType;
import de.uni_freiburg.informatik.ultimate.boogie.ast.AssertStatement;
import de.uni_freiburg.informatik.ultimate.boogie.ast.AssignmentStatement;
import de.uni_freiburg.informatik.ultimate.boogie.ast.AssumeStatement;
import de.uni_freiburg.informatik.ultimate.boogie.ast.Attribute;
import de.uni_freiburg.informatik.ultimate.boogie.ast.BinaryExpression;
import de.uni_freiburg.informatik.ultimate.boogie.ast.BinaryExpression.Operator;
import de.uni_freiburg.informatik.ultimate.boogie.ast.Body;
import de.uni_freiburg.informatik.ultimate.boogie.ast.CallStatement;
import de.uni_freiburg.informatik.ultimate.boogie.ast.Declaration;
import de.uni_freiburg.informatik.ultimate.boogie.ast.EnsuresSpecification;
import de.uni_freiburg.informatik.ultimate.boogie.ast.Expression;
import de.uni_freiburg.informatik.ultimate.boogie.ast.HavocStatement;
import de.uni_freiburg.informatik.ultimate.boogie.ast.IdentifierExpression;
import de.uni_freiburg.informatik.ultimate.boogie.ast.LeftHandSide;
import de.uni_freiburg.informatik.ultimate.boogie.ast.LoopInvariantSpecification;
import de.uni_freiburg.informatik.ultimate.boogie.ast.ModifiesSpecification;
import de.uni_freiburg.informatik.ultimate.boogie.ast.PrimitiveType;
import de.uni_freiburg.informatik.ultimate.boogie.ast.Procedure;
import de.uni_freiburg.informatik.ultimate.boogie.ast.QuantifierExpression;
import de.uni_freiburg.informatik.ultimate.boogie.ast.RequiresSpecification;
import de.uni_freiburg.informatik.ultimate.boogie.ast.Specification;
import de.uni_freiburg.informatik.ultimate.boogie.ast.Statement;
import de.uni_freiburg.informatik.ultimate.boogie.ast.StructAccessExpression;
import de.uni_freiburg.informatik.ultimate.boogie.ast.StructConstructor;
import de.uni_freiburg.informatik.ultimate.boogie.ast.UnaryExpression;
import de.uni_freiburg.informatik.ultimate.boogie.ast.VarList;
import de.uni_freiburg.informatik.ultimate.boogie.ast.VariableDeclaration;
import de.uni_freiburg.informatik.ultimate.boogie.ast.VariableLHS;
import de.uni_freiburg.informatik.ultimate.boogie.ast.WhileStatement;
import de.uni_freiburg.informatik.ultimate.boogie.type.BoogieArrayType;
import de.uni_freiburg.informatik.ultimate.boogie.type.BoogieType;
import de.uni_freiburg.informatik.ultimate.cdt.translation.implementation.CACSLLocation;
import de.uni_freiburg.informatik.ultimate.cdt.translation.implementation.LocationFactory;
import de.uni_freiburg.informatik.ultimate.cdt.translation.implementation.base.CTranslationUtil;
import de.uni_freiburg.informatik.ultimate.cdt.translation.implementation.base.HandlerHandler;
import de.uni_freiburg.informatik.ultimate.cdt.translation.implementation.base.TypeHandler;
import de.uni_freiburg.informatik.ultimate.cdt.translation.implementation.base.chandler.AMemoryModel.ReadWriteDefinition;
import de.uni_freiburg.informatik.ultimate.cdt.translation.implementation.base.expressiontranslation.ExpressionTranslation;
import de.uni_freiburg.informatik.ultimate.cdt.translation.implementation.container.AuxVarInfo;
import de.uni_freiburg.informatik.ultimate.cdt.translation.implementation.container.c.CArray;
import de.uni_freiburg.informatik.ultimate.cdt.translation.implementation.container.c.CEnum;
import de.uni_freiburg.informatik.ultimate.cdt.translation.implementation.container.c.CNamed;
import de.uni_freiburg.informatik.ultimate.cdt.translation.implementation.container.c.CPointer;
import de.uni_freiburg.informatik.ultimate.cdt.translation.implementation.container.c.CPrimitive;
import de.uni_freiburg.informatik.ultimate.cdt.translation.implementation.container.c.CPrimitive.CPrimitiveCategory;
import de.uni_freiburg.informatik.ultimate.cdt.translation.implementation.container.c.CPrimitive.CPrimitives;
import de.uni_freiburg.informatik.ultimate.cdt.translation.implementation.container.c.CStruct;
import de.uni_freiburg.informatik.ultimate.cdt.translation.implementation.container.c.CType;
import de.uni_freiburg.informatik.ultimate.cdt.translation.implementation.exception.UnsupportedSyntaxException;
import de.uni_freiburg.informatik.ultimate.cdt.translation.implementation.result.ExpressionResult;
import de.uni_freiburg.informatik.ultimate.cdt.translation.implementation.result.ExpressionResultBuilder;
import de.uni_freiburg.informatik.ultimate.cdt.translation.implementation.result.HeapLValue;
import de.uni_freiburg.informatik.ultimate.cdt.translation.implementation.result.LRValue;
import de.uni_freiburg.informatik.ultimate.cdt.translation.implementation.result.LRValueFactory;
import de.uni_freiburg.informatik.ultimate.cdt.translation.implementation.result.LocalLValue;
import de.uni_freiburg.informatik.ultimate.cdt.translation.implementation.result.RValue;
import de.uni_freiburg.informatik.ultimate.cdt.translation.implementation.util.SFO;
import de.uni_freiburg.informatik.ultimate.cdt.translation.interfaces.Dispatcher;
import de.uni_freiburg.informatik.ultimate.cdt.translation.interfaces.handler.INameHandler;
import de.uni_freiburg.informatik.ultimate.cdt.translation.interfaces.handler.ITypeHandler;
import de.uni_freiburg.informatik.ultimate.core.lib.models.annotation.Check;
import de.uni_freiburg.informatik.ultimate.core.lib.models.annotation.Check.Spec;
import de.uni_freiburg.informatik.ultimate.core.lib.models.annotation.Overapprox;
import de.uni_freiburg.informatik.ultimate.core.model.models.ILocation;
import de.uni_freiburg.informatik.ultimate.core.model.preferences.IPreferenceProvider;
import de.uni_freiburg.informatik.ultimate.plugins.generator.cacsl2boogietranslator.preferences.CACSLPreferenceInitializer;
import de.uni_freiburg.informatik.ultimate.plugins.generator.cacsl2boogietranslator.preferences.CACSLPreferenceInitializer.MemoryModel;
import de.uni_freiburg.informatik.ultimate.plugins.generator.cacsl2boogietranslator.preferences.CACSLPreferenceInitializer.PointerCheckMode;
import de.uni_freiburg.informatik.ultimate.util.datastructures.LinkedScopedHashMap;

/**
 * @author Markus Lindenmann
 */
public class MemoryHandler {

	private static final boolean SUPPORT_FLOATS_ON_HEAP = true;
	private static final String FLOAT_ON_HEAP_UNSOUND_MESSAGE =
			"Analysis for floating types on heap by default disabled (soundness first).";

	/**
	 * The "~size" variable identifier.
	 */
	private static final String SIZE = "~size";
	/**
	 * The "~addr" variable identifier.
	 */
	private static final String ADDR = "~addr";

	/**
	 * Add also implementations of malloc, free, write and read functions. TODO: details
	 */
	private static final boolean ADD_IMPLEMENTATIONS = false;

	private final PointerCheckMode mPointerBaseValidity;
	private final PointerCheckMode mCheckPointerSubtractionAndComparisonValidity;
	private final PointerCheckMode mPointerTargetFullyAllocated;
	// private final boolean mCheckFreeValid;

	// needed for adding modifies clauses
	private final ITypeHandler mTypeHandler;

	/**
	 * This set contains those pointers that we have to malloc at the beginning of the current scope;
	 */
	private final LinkedScopedHashMap<LocalLValueILocationPair, Integer> mVariablesToBeMalloced;
	/**
	 * This set contains those pointers that we have to free at the end of the current scope;
	 */
	private final LinkedScopedHashMap<LocalLValueILocationPair, Integer> mVariablesToBeFreed;

	private final ExpressionTranslation mExpressionTranslation;

	private final TypeSizeAndOffsetComputer mTypeSizeAndOffsetComputer;
	private final TypeSizes mTypeSizes;
	private final RequiredMemoryModelFeatures mRequiredMemoryModelFeatures;
	private final AMemoryModel mMemoryModel;
	private final INameHandler mNameHandler;
	private final MemoryModel mMemoryModelPreference;
	private final IBooleanArrayHelper mBooleanArrayHelper;
	private final boolean mFpToIeeeBvExtension;
	private final IPreferenceProvider mPreferences;
	private final BoogieTypeHelper mBoogieTypeHelper;
	private final ProcedureManager mProcedureManager;
	private final FunctionHandler mFunctionHandler;
	public Map<MemoryModelDeclarations, MemoryModelDeclarationInfo> mMemoryModelDeclarationInfos;

	HandlerHandler mHandlerHandler;

	/**
	 * Constructor.
	 *
	 * @param typeHandler
	 * @param checkPointerValidity
	 * @param typeSizeComputer
	 * @param bitvectorTranslation
	 * @param nameHandler
	 * @param boogieTypeHelper
	 */
	public MemoryHandler(final HandlerHandler handlerHandler, final boolean checkPointerValidity,
			final TypeSizes typeSizes,
			final boolean bitvectorTranslation, final INameHandler nameHandler, final boolean smtBoolArrayWorkaround,
			final IPreferenceProvider prefs) {
		mHandlerHandler = handlerHandler;
		handlerHandler.setMemoryHandler(this);

		mTypeHandler = handlerHandler.getTypeHandler();
		mTypeSizes = typeSizes;
		mFunctionHandler = handlerHandler.getFunctionHandler();
		mExpressionTranslation = handlerHandler.getExpressionTranslation();
		mNameHandler = nameHandler;
		mRequiredMemoryModelFeatures = new RequiredMemoryModelFeatures();
		if (smtBoolArrayWorkaround) {
			if (bitvectorTranslation) {
				mBooleanArrayHelper = new BooleanArrayHelper_Bitvector();
			} else {
				mBooleanArrayHelper = new BooleanArrayHelper_Integer();
			}
		} else {
			mBooleanArrayHelper = new BooleanArrayHelper_Bool();
		}

		mPreferences = prefs;

		// read preferences from settings
		mPointerBaseValidity =
				prefs.getEnum(CACSLPreferenceInitializer.LABEL_CHECK_POINTER_VALIDITY, PointerCheckMode.class);
		mPointerTargetFullyAllocated =
				prefs.getEnum(CACSLPreferenceInitializer.LABEL_CHECK_POINTER_ALLOC, PointerCheckMode.class);
		// mCheckFreeValid = prefs.getBoolean(CACSLPreferenceInitializer.LABEL_CHECK_FREE_VALID);
		mCheckPointerSubtractionAndComparisonValidity =
				prefs.getEnum(CACSLPreferenceInitializer.LABEL_CHECK_POINTER_SUBTRACTION_AND_COMPARISON_VALIDITY,
						PointerCheckMode.class);
		mMemoryModelPreference = prefs.getEnum(CACSLPreferenceInitializer.LABEL_MEMORY_MODEL, MemoryModel.class);
		mFpToIeeeBvExtension = prefs.getBoolean(CACSLPreferenceInitializer.LABEL_FP_TO_IEEE_BV_EXTENSION);

		final MemoryModel memoryModelPreference = mMemoryModelPreference;
		final AMemoryModel memoryModel = getMemoryModel(bitvectorTranslation, memoryModelPreference);
		mMemoryModel = memoryModel;
		mVariablesToBeMalloced = new LinkedScopedHashMap<>();
		mVariablesToBeFreed = new LinkedScopedHashMap<>();

		mTypeSizeAndOffsetComputer = handlerHandler.getTypeSizeAndOffsetComputer();

		mBoogieTypeHelper = handlerHandler.getBoogieTypeHelper();
		mProcedureManager = handlerHandler.getProcedureManager();

		mMemoryModelDeclarationInfos = new LinkedHashMap<>();
	}

	private AMemoryModel getMemoryModel(final boolean bitvectorTranslation, final MemoryModel memoryModelPreference)
			throws AssertionError {
		final AMemoryModel memoryModel;
		if (bitvectorTranslation) {
			switch (memoryModelPreference) {
			case HoenickeLindenmann_1ByteResolution:
				memoryModel = new MemoryModel_SingleBitprecise(1, mTypeSizes, (TypeHandler) mTypeHandler,
						mExpressionTranslation);
				break;
			case HoenickeLindenmann_2ByteResolution:
				memoryModel = new MemoryModel_SingleBitprecise(2, mTypeSizes, (TypeHandler) mTypeHandler,
						mExpressionTranslation);
				break;
			case HoenickeLindenmann_4ByteResolution:
				memoryModel = new MemoryModel_SingleBitprecise(4, mTypeSizes, (TypeHandler) mTypeHandler,
						mExpressionTranslation);
				break;
			case HoenickeLindenmann_8ByteResolution:
				memoryModel = new MemoryModel_SingleBitprecise(8, mTypeSizes, (TypeHandler) mTypeHandler,
						mExpressionTranslation);
				break;
			case HoenickeLindenmann_Original:
				memoryModel = new MemoryModel_MultiBitprecise(mTypeSizes, mTypeHandler, mExpressionTranslation);
				break;
			default:
				throw new AssertionError("unknown value");
			}
		} else {
			switch (memoryModelPreference) {
			case HoenickeLindenmann_Original:
				memoryModel = new MemoryModel_Unbounded(mTypeSizes, mTypeHandler, mExpressionTranslation);
				break;
			case HoenickeLindenmann_1ByteResolution:
			case HoenickeLindenmann_2ByteResolution:
			case HoenickeLindenmann_4ByteResolution:
			case HoenickeLindenmann_8ByteResolution:
				throw new UnsupportedOperationException(
						"Memory model " + mMemoryModelPreference + " only available in bitprecise translation");
			default:
				throw new AssertionError("unknown value");
			}
		}
		return memoryModel;
	}

	public RequiredMemoryModelFeatures getRequiredMemoryModelFeatures() {
		return mRequiredMemoryModelFeatures;
	}

	public AMemoryModel getMemoryModel() {
		return mMemoryModel;
	}

	public Expression calculateSizeOf(final ILocation loc, final CType cType, final IASTNode hook) {
		return mTypeSizeAndOffsetComputer.constructBytesizeExpression(loc, cType, hook);
	}

	/**
	 * Returns declarations needed for the memory model (right now we use the Hoenicke-Lindenmann memory model).
	 * Depending on the translated program this may include any or all of the following:
	 * <li>declarations of the arrays #valid, #length, #memory_int, etc.
	 * <li>declarations of the procedures Ultimate.alloc, Ultimate.dealloc, read_int, write_int, etc.
	 *
	 * Note that this method only returns procedure implementations (if there are any). The corresponding declarations
	 * are introduced by registering the procedures in the FunctionHandler. The FunctionHandler will add them to the
	 * program.
	 *
	 * @param main
	 *            a reference to the main dispatcher.
	 * @param tuLoc
	 *            location to use for declarations. Usually this will be the location of the TranslationUnit.
	 * @return a set of declarations.
	 */
	public ArrayList<Declaration> declareMemoryModelInfrastructure(final Dispatcher main, final ILocation tuLoc,
			final IASTNode hook) {
		final ArrayList<Declaration> decl = new ArrayList<>();
		if (!mRequiredMemoryModelFeatures.isMemoryModelInfrastructureRequired()
				&& mRequiredMemoryModelFeatures.getRequiredMemoryModelDeclarations().isEmpty()) {
			return decl;
		}

		decl.add(constructNullPointerConstant());
		// TODO should we introduce the commented out conditions -- right now it seems safe to always declare the base
		// arrays and functions
		// if
		// (getRequiredMemoryModelFeatures().getRequiredMemoryModelDeclarations().contains(MemoryModelDeclarations.Ultimate_Valid))
		// {
		decl.add(constructValidArrayDeclaration());
		// }
		// if
		// (getRequiredMemoryModelFeatures().getRequiredMemoryModelDeclarations().contains(MemoryModelDeclarations.Ultimate_Length))
		// {
		decl.add(constuctLengthArrayDeclaration());
		// }

		final Collection<HeapDataArray> heapDataArrays = mMemoryModel.getDataHeapArrays(mRequiredMemoryModelFeatures);

		{// add memory arrays and read/write procedures
			for (final HeapDataArray heapDataArray : heapDataArrays) {
				decl.add(constructMemoryArrayDeclaration(tuLoc, heapDataArray.getName(), heapDataArray.getASTType()));
				// create and add read and write procedure
				decl.addAll(constructWriteProcedures(main, tuLoc, heapDataArrays, heapDataArray, hook));
				decl.addAll(constructReadProcedures(main, tuLoc, heapDataArray, hook));
			}
		}

		// decl.addAll(declareFree(main, tuLoc));
		decl.addAll(declareDeallocation(main, tuLoc, hook));

		if (mRequiredMemoryModelFeatures.getRequiredMemoryModelDeclarations()
				.contains(MemoryModelDeclarations.Ultimate_Alloc)) {
			decl.addAll(declareMalloc(main, mTypeHandler, tuLoc, hook));
			// mProcedureManager.addCallGraphNode(MemoryModelDeclarations.Ultimate_Alloc.getName());
			// mProcedureManager.addModifiedGlobalEntry(MemoryModelDeclarations.Ultimate_Alloc.getName());
		}

		if (mRequiredMemoryModelFeatures.getRequiredMemoryModelDeclarations()
				.contains(MemoryModelDeclarations.C_Memset)) {
			decl.addAll(declareMemset(main, heapDataArrays, hook));
			// mProcedureManager.addCallGraphNode(MemoryModelDeclarations.C_Memset.getName());
			// mProcedureManager.addModifiedGlobalEntry(MemoryModelDeclarations.C_Memset.getName());
		}

		if (mRequiredMemoryModelFeatures.getRequiredMemoryModelDeclarations()
				.contains(MemoryModelDeclarations.Ultimate_MemInit)) {
			decl.addAll(declareUltimateMeminit(main, heapDataArrays, hook));
			// mProcedureManager.addCallGraphNode(MemoryModelDeclarations.Ultimate_MemInit.getName());
			// mProcedureManager.addModifiedGlobalEntry(MemoryModelDeclarations.Ultimate_MemInit.getName());
		}

		if (mRequiredMemoryModelFeatures.getRequiredMemoryModelDeclarations()
				.contains(MemoryModelDeclarations.C_Memcpy)) {
			decl.addAll(declareMemcpyOrMemmove(main, heapDataArrays, MemoryModelDeclarations.C_Memcpy, hook));
			// mProcedureManager.addCallGraphNode(MemoryModelDeclarations.C_Memcpy.getName());
			// mProcedureManager.addModifiedGlobalEntry(MemoryModelDeclarations.C_Memcpy.getName());
		}

		if (mRequiredMemoryModelFeatures.getRequiredMemoryModelDeclarations()
				.contains(MemoryModelDeclarations.C_Memmove)) {
			decl.addAll(declareMemcpyOrMemmove(main, heapDataArrays, MemoryModelDeclarations.C_Memmove, hook));
			// mProcedureManager.addCallGraphNode(MemoryModelDeclarations.C_Memmove.getName());
			// mProcedureManager.addModifiedGlobalEntry(MemoryModelDeclarations.C_Memmove.getName());
		}
		assert assertContainsNodeProcedureDeclarations(decl) : "add procedure declarations via function handler!";
		return decl;
	}

	/**
	 * Check that there is no procedure declaration (i.e. a Procedure without a body) in the given set of Declarations.
	 *
	 * @param decl
	 * @return
	 */
	private boolean assertContainsNodeProcedureDeclarations(final Collection<Declaration> decl) {
		for (final Declaration d : decl) {
			if (d instanceof Procedure && ((Procedure) d).getBody() == null) {
				assert false : "found a procedure declaration";
				return false;
			}
		}
		return true;
	}

	private VariableDeclaration constuctLengthArrayDeclaration() {
		// var #length : [int]int;
		final ILocation ignoreLoc = LocationFactory.createIgnoreCLocation();
		final ASTType pointerComponentType =
				mTypeHandler.cType2AstType(ignoreLoc, mExpressionTranslation.getCTypeOfPointerComponents());
		final BoogieType boogieType =
				BoogieType.createArrayType(0, new BoogieType[] { (BoogieType) pointerComponentType.getBoogieType() },
						(BoogieType) pointerComponentType.getBoogieType());
		final ASTType lengthType = new ArrayType(ignoreLoc, boogieType, new String[0],
				new ASTType[] { pointerComponentType }, pointerComponentType);
		final VarList vlL = new VarList(ignoreLoc, new String[] { SFO.LENGTH }, lengthType);
		return new VariableDeclaration(ignoreLoc, new Attribute[0], new VarList[] { vlL });
	}

	private VariableDeclaration constructValidArrayDeclaration() {
		// var #valid : [int]bool;
		final ILocation ignoreLoc = LocationFactory.createIgnoreCLocation();
		final ASTType pointerComponentType =
				mTypeHandler.cType2AstType(ignoreLoc, mExpressionTranslation.getCTypeOfPointerComponents());
		final BoogieType boogieType =
				BoogieType.createArrayType(0, new BoogieType[] { (BoogieType) pointerComponentType.getBoogieType() },
						(BoogieType) mBooleanArrayHelper.constructBoolReplacementType().getBoogieType());
		final ASTType validType = new ArrayType(ignoreLoc, boogieType, new String[0],
				new ASTType[] { pointerComponentType }, mBooleanArrayHelper.constructBoolReplacementType());
		final VarList vlV =
				new VarList(ignoreLoc, new String[] { MemoryModelDeclarations.Ultimate_Valid.getName() }, validType);
		return new VariableDeclaration(ignoreLoc, new Attribute[0], new VarList[] { vlV });
	}

	private VariableDeclaration constructNullPointerConstant() {
		// NULL Pointer
		final ILocation ignoreLoc = LocationFactory.createIgnoreCLocation();
		final VariableDeclaration result = new VariableDeclaration(ignoreLoc, new Attribute[0], new VarList[] {
				new VarList(ignoreLoc, new String[] { SFO.NULL }, mTypeHandler.constructPointerType(ignoreLoc)) });
		return result;
	}

	private List<Declaration> declareUltimateMeminit(final Dispatcher main,
			final Collection<HeapDataArray> heapDataArrays, final IASTNode hook) {
		final ArrayList<Declaration> decls = new ArrayList<>();
		final ILocation ignoreLoc = LocationFactory.createIgnoreCLocation();

		final String inParamPtr = "#ptr";
		final String inParamAmountOfFields = "#amountOfFields";
		final String inParamSizeOfFields = "#sizeOfFields";
		final String inParamProduct = "#product";
		final String procName = MemoryModelDeclarations.Ultimate_MemInit.getName();

		final VarList[] inParams;
		final VarList[] outParams;
		{
			final VarList inParamPtrVl =
					new VarList(ignoreLoc, new String[] { inParamPtr }, mTypeHandler.constructPointerType(ignoreLoc));
			final VarList inParamAmountOfFieldsVl = new VarList(ignoreLoc, new String[] { inParamAmountOfFields },
					mTypeHandler.cType2AstType(ignoreLoc, mTypeSizeAndOffsetComputer.getSizeT()));
			final VarList inParamSizeOfFieldsVl = new VarList(ignoreLoc, new String[] { inParamSizeOfFields },
					mTypeHandler.cType2AstType(ignoreLoc, mTypeSizeAndOffsetComputer.getSizeT()));
			final VarList inParamProductVl = new VarList(ignoreLoc, new String[] { inParamProduct },
					mTypeHandler.cType2AstType(ignoreLoc, mTypeSizeAndOffsetComputer.getSizeT()));

			inParams = new VarList[] { inParamPtrVl, inParamAmountOfFieldsVl, inParamSizeOfFieldsVl, inParamProductVl };
			outParams = new VarList[] {};
		}

		{

			final Procedure memCpyProcDecl = new Procedure(ignoreLoc, new Attribute[0], procName, new String[0],
					inParams, outParams, new Specification[0], null);

			mProcedureManager.beginCustomProcedure(main, ignoreLoc, procName, memCpyProcDecl);
		}

		final List<VariableDeclaration> decl = new ArrayList<>();
		final CPrimitive sizeT = mTypeSizeAndOffsetComputer.getSizeT();
		// final String loopCtr = mNameHandler.getTempVarUID(SFO.AUXVAR.LOOPCTR, sizeT);
		// final ASTType astType = mTypeHandler.cType2AstType(ignoreLoc, sizeT);
		// final VarList lcvl = new VarList(ignoreLoc, new String[] { loopCtr }, astType);
		// final VariableDeclaration loopCtrDec =
		// new VariableDeclaration(ignoreLoc, new Attribute[0], new VarList[] { lcvl });
		final AuxVarInfo loopCtrAux = AuxVarInfo.constructAuxVarInfo(ignoreLoc, main, sizeT, SFO.AUXVAR.LOOPCTR);
		decl.add(loopCtrAux.getVarDec());

		final Expression zero = mExpressionTranslation.constructLiteralForIntegerType(ignoreLoc,
				new CPrimitive(CPrimitives.UCHAR), BigInteger.ZERO);
		final List<Statement> loopBody =
				constructMemsetLoopBody(heapDataArrays, loopCtrAux, inParamPtr, zero, procName, hook);

		final IdentifierExpression inParamProductExpr = // new IdentifierExpression(ignoreLoc, inParamProduct);
				ExpressionFactory.constructIdentifierExpression(ignoreLoc, mBoogieTypeHelper.getBoogieTypeForSizeT(),
						inParamProduct, new DeclarationInformation(StorageClass.IMPLEMENTATION_INPARAM, procName));

		final Expression stepsize;
		if (mMemoryModel instanceof MemoryModel_SingleBitprecise) {
			final int resolution = ((MemoryModel_SingleBitprecise) mMemoryModel).getResolution();
			stepsize = mExpressionTranslation.constructLiteralForIntegerType(ignoreLoc, sizeT,
					BigInteger.valueOf(resolution));
		} else {
			final IdentifierExpression inParamSizeOfFieldsExpr =
					// new IdentifierExpression(ignoreLoc, inParamSizeOfFields);
					ExpressionFactory.constructIdentifierExpression(ignoreLoc, BoogieType.TYPE_INT, inParamSizeOfFields,
							new DeclarationInformation(StorageClass.IMPLEMENTATION_INPARAM, procName));

			stepsize = inParamSizeOfFieldsExpr;
		}

		final List<Statement> stmt =
				constructCountingLoop(inParamProductExpr, loopCtrAux, stepsize, loopBody, procName);

		final Body procBody = mProcedureManager.constructBody(ignoreLoc,
				decl.toArray(new VariableDeclaration[decl.size()]), stmt.toArray(new Statement[stmt.size()]), procName);

		// make the specifications
		// final ArrayList<Specification> specs = new ArrayList<>();

		// EDIT: the function handler should completely deal with modifies clauses if we announce them correctly
		// add modifies spec
		// final ModifiesSpecification modifiesSpec = announceModifiedGlobals(proc, heapDataArrays);
		// specs.add(modifiesSpec);
		// announceModifiedGlobals(procName, heapDataArrays);
		// heapDataArrays.forEach(
		// heapDataArray -> mProcedureManager.addModifiedGlobal(procName, heapDataArray.getVariableLHS()));

		// add the procedure declaration
		// final Procedure memCpyProcDecl = new Procedure(ignoreLoc, new Attribute[0], procName, new String[0],
		// inParams,
		// outParams, new Specification[0], null);
		// outParams, specs.toArray(new Specification[specs.size()]), null);
		// decls.add(memCpyProcDecl);
		// mProcedureManager.registerProcedureDeclaration(procName, memCpyProcDecl);

		// add the procedure implementation
		final Procedure memCpyProc = new Procedure(ignoreLoc, new Attribute[0], procName, new String[0], inParams,
				outParams, null, procBody);
		decls.add(memCpyProc);

		mProcedureManager.endCustomProcedure(main, procName);
		return decls;
	}

	public CallStatement constructUltimateMeminitCall(final ILocation loc, final Expression amountOfFields,
			final Expression sizeOfFields, final Expression product, final Expression pointer) {
		requireMemoryModelFeature(MemoryModelDeclarations.Ultimate_MemInit);
		return StatementFactory.constructCallStatement(loc, false, new VariableLHS[0],
				MemoryModelDeclarations.Ultimate_MemInit.getName(),
				new Expression[] { pointer, amountOfFields, sizeOfFields, product });
	}

	// /**
	// * Tell mProcedureManager that procedure proc modifies all heapDataArrays. Retruns modifies specification.
	// */
	// private ModifiesSpecification announceModifiedGlobals(final String proc,
	// final Collection<HeapDataArray> heapDataArrays) {
	// final ILocation ignoreLoc = LocationFactory.createIgnoreCLocation();
	// final ArrayList<VariableLHS> modifiesLHSs = new ArrayList<>();
	// for (final HeapDataArray hda : heapDataArrays) {
	// final String memArrayName = hda.getVariableName();
	// modifiesLHSs.add(new VariableLHS(ignoreLoc, memArrayName));
	//
	//// mProcedureManager.addCallGraphNode(proc);
	// mProcedureManager.addModifiedGlobal(proc, memArrayName);
	// }
	// return new ModifiesSpecification(ignoreLoc, false, modifiesLHSs.toArray(new VariableLHS[modifiesLHSs.size()]));
	// }

	/**
	 * Construct specification and implementation for our Boogie representation of the memcpy and memmove functions
	 * defined in 7.24.2.1 of C11.
	 *
	 * void *memcpy(void * restrict s1, const void * restrict s2, size_t n);
	 *
	 * void* memmove( void* dest, const void* src, size_t count );
	 *
	 * @param main
	 * @param heapDataArrays
	 * @return
	 */
	private List<Declaration> declareMemcpyOrMemmove(final Dispatcher main,
			final Collection<HeapDataArray> heapDataArrays, final MemoryModelDeclarations memCopyOrMemMove,
			final IASTNode hook) {
		assert memCopyOrMemMove == MemoryModelDeclarations.C_Memcpy
				|| memCopyOrMemMove == MemoryModelDeclarations.C_Memmove;

		final List<Declaration> memCpyDecl = new ArrayList<>();
		final ILocation ignoreLoc = LocationFactory.createIgnoreCLocation();

		final VarList inPDest =
				new VarList(ignoreLoc, new String[] { SFO.MEMCPY_DEST }, mTypeHandler.constructPointerType(ignoreLoc));
		final VarList inPSrc =
				new VarList(ignoreLoc, new String[] { SFO.MEMCPY_SRC }, mTypeHandler.constructPointerType(ignoreLoc));
		final VarList inPSize = new VarList(ignoreLoc, new String[] { SFO.MEMCPY_SIZE },
				mTypeHandler.cType2AstType(ignoreLoc, mTypeSizeAndOffsetComputer.getSizeT()));
		final VarList outP =
				new VarList(ignoreLoc, new String[] { SFO.RES }, mTypeHandler.constructPointerType(ignoreLoc));
		final VarList[] inParams = new VarList[] { inPDest, inPSrc, inPSize };
		final VarList[] outParams = new VarList[] { outP };

		{
			final Procedure memCpyProcDecl = new Procedure(ignoreLoc, new Attribute[0], memCopyOrMemMove.getName(),
					new String[0], inParams, outParams, new Specification[0], null);
			mProcedureManager.beginCustomProcedure(main, ignoreLoc, memCopyOrMemMove.getName(), memCpyProcDecl);
		}

		final List<VariableDeclaration> decl = new ArrayList<>();
		final CPrimitive sizeT = mTypeSizeAndOffsetComputer.getSizeT();

		// final String loopCtr = mNameHandler.getTempVarUID(SFO.AUXVAR.LOOPCTR, sizeT);
		// final ASTType astType = mTypeHandler.cType2AstType(ignoreLoc, sizeT);
		// final VarList lcvl = new VarList(ignoreLoc, new String[] { loopCtr }, astType);
		// final VariableDeclaration loopCtrDec =
		// new VariableDeclaration(ignoreLoc, new Attribute[0], new VarList[] { lcvl });
		final AuxVarInfo loopCtrAux = AuxVarInfo.constructAuxVarInfo(ignoreLoc, main, sizeT, SFO.AUXVAR.LOOPCTR);
		decl.add(loopCtrAux.getVarDec());

		final List<Statement> loopBody = constructMemcpyOrMemmoveLoopBody(heapDataArrays, loopCtrAux, SFO.MEMCPY_DEST,
				SFO.MEMCPY_SRC, memCopyOrMemMove.getName(), hook);

		final IdentifierExpression sizeIdExprBody = // new IdentifierExpression(ignoreLoc, SFO.MEMCPY_SIZE);
				ExpressionFactory.constructIdentifierExpression(ignoreLoc, mBoogieTypeHelper.getBoogieTypeForSizeT(),
						SFO.MEMCPY_SIZE,
						new DeclarationInformation(StorageClass.IMPLEMENTATION_INPARAM, memCopyOrMemMove.getName()));

		final Expression one = mExpressionTranslation.constructLiteralForIntegerType(ignoreLoc,
				mExpressionTranslation.getCTypeOfPointerComponents(), BigInteger.ONE);
		final List<Statement> stmt =
				constructCountingLoop(sizeIdExprBody, loopCtrAux, one, loopBody, memCopyOrMemMove.getName());

		final Body procBody =
				mProcedureManager.constructBody(ignoreLoc, decl.toArray(new VariableDeclaration[decl.size()]),
						stmt.toArray(new Statement[stmt.size()]), memCopyOrMemMove.getName());

		// make the specifications
		final ArrayList<Specification> specs = new ArrayList<>();

		// add modifies spec

		// EDIT: the function handler should completely deal with modifies clauses if we announce them correctly
		// final ModifiesSpecification modifiesSpec = announceModifiedGlobals(memModelDecl.getName(), heapDataArrays);
		// specs.add(modifiesSpec);
		// announceModifiedGlobals(memModelDecl.getName(), heapDataArrays);

		// heapDataArrays.forEach(heapDataArray -> mProcedureManager.addModifiedGlobal(memCopyOrMemMove.getName(),
		// heapDataArray.getVariableLHS()));

		final IdentifierExpression sizeIdExprDecl = // new IdentifierExpression(ignoreLoc, SFO.MEMCPY_SIZE);
				ExpressionFactory.constructIdentifierExpression(ignoreLoc, mBoogieTypeHelper.getBoogieTypeForSizeT(),
						SFO.MEMCPY_SIZE,
						new DeclarationInformation(StorageClass.PROC_FUNC_INPARAM, memCopyOrMemMove.getName()));

		// add requires #valid[dest!base];
		specs.addAll(constructPointerBaseValidityCheck(ignoreLoc, SFO.MEMCPY_DEST, memCopyOrMemMove.getName()));
		// add requires #valid[src!base];
		specs.addAll(constructPointerBaseValidityCheck(ignoreLoc, SFO.MEMCPY_SRC, memCopyOrMemMove.getName()));

		// add requires (#size + #dest!offset <= #length[#dest!base] && 0 <= #dest!offset)
		specs.addAll(constructPointerTargetFullyAllocatedCheck(ignoreLoc, sizeIdExprDecl, SFO.MEMCPY_DEST,
				memCopyOrMemMove.getName()));

		// add requires (#size + #src!offset <= #length[#src!base] && 0 <= #src!offset)
		specs.addAll(constructPointerTargetFullyAllocatedCheck(ignoreLoc, sizeIdExprDecl, SFO.MEMCPY_SRC,
				memCopyOrMemMove.getName()));

		if (memCopyOrMemMove == MemoryModelDeclarations.C_Memcpy && false) {
			// disabled because underapprox. for undefined behavior is ok
			final RequiresSpecification noOverlapping = constructRequiresSourceDestNoOverlap(ignoreLoc, sizeIdExprDecl);
			specs.add(noOverlapping);
		}

		// free ensures #res == dest;
		final EnsuresSpecification returnValue =
				mProcedureManager
						.constructEnsuresSpecification(
								ignoreLoc, true, ExpressionFactory.newBinaryExpression(ignoreLoc, Operator.COMPEQ,
										// new IdentifierExpression(ignoreLoc, SFO.RES),
										ExpressionFactory
												.constructIdentifierExpression(ignoreLoc,
														mBoogieTypeHelper.getBoogieTypeForPointerType(), SFO.RES,
														new DeclarationInformation(StorageClass.PROC_FUNC_OUTPARAM,
																memCopyOrMemMove.getName())),
										// new IdentifierExpression(ignoreLoc, SFO.MEMCPY_DEST)));
										ExpressionFactory.constructIdentifierExpression(
												ignoreLoc, mBoogieTypeHelper.getBoogieTypeForPointerType(),
												SFO.MEMCPY_DEST, new DeclarationInformation(
														StorageClass.PROC_FUNC_INPARAM, memCopyOrMemMove.getName()))),
								Collections.emptySet());
		specs.add(returnValue);

		// add the procedure declaration
		// final Procedure memCpyProcDecl = new Procedure(ignoreLoc, new Attribute[0], memCopyOrMemMove.getName(),
		// new String[0], inParams, outParams, specs.toArray(new Specification[specs.size()]), null);
		// memCpyDecl.add(memCpyProcDecl);
		// mProcedureManager.registerProcedureDeclaration(memCopyOrMemMove.getName(), memCpyProcDecl);
		mProcedureManager.addSpecificationsToCurrentProcedure(specs);

		// add the procedure implementation
		final Procedure memCpyProc = new Procedure(ignoreLoc, new Attribute[0], memCopyOrMemMove.getName(),
				new String[0], inParams, outParams, null, procBody);
		memCpyDecl.add(memCpyProc);

		mProcedureManager.endCustomProcedure(main, memCopyOrMemMove.getName());

		return memCpyDecl;
	}

	/**
	 * Construct a requires-clause that states that {@link SFO#MEMCPY_SRC} and {@link SFO#MEMCPY_DEST} do not overlap.
	 * The clause is marked as {@link Check} for {@link Spec#UNDEFINED_BEHAVIOR}.
	 *
	 * @param loc
	 *            The location of all expressions used in this requires-clause
	 * @param sizeIdExpr
	 *            an identifier expression pointing to the size variable that determines the interval of
	 *            {@link SFO#MEMCPY_SRC} that should not overlap with {@link SFO#MEMCPY_DEST}.
	 */
	private RequiresSpecification constructRequiresSourceDestNoOverlap(final ILocation loc,
			final IdentifierExpression sizeIdExpr) {
		// memcpy does not allow overlapping:
		// add requires dest.base != src.base || src.offset + size < dest.offset || dest.offset + size < src.offset
		final List<Expression> noOverlapExprs = new ArrayList<>(3);
		final IdentifierExpression srcpointer =
				ExpressionFactory.constructIdentifierExpression(loc, mBoogieTypeHelper.getBoogieTypeForPointerType(),
						SFO.MEMCPY_SRC, new DeclarationInformation(StorageClass.IMPLEMENTATION_INPARAM, SFO.MEMCPY));
		final IdentifierExpression destpointer =
				ExpressionFactory.constructIdentifierExpression(loc, mBoogieTypeHelper.getBoogieTypeForPointerType(),
						SFO.MEMCPY_DEST, new DeclarationInformation(StorageClass.IMPLEMENTATION_INPARAM, SFO.MEMCPY));
		final Expression srcbase = getPointerBaseAddress(srcpointer, loc);
		final Expression destbase = getPointerBaseAddress(destpointer, loc);
		final Expression srcoffset = getPointerOffset(srcpointer, loc);
		final Expression destoffset = getPointerOffset(destpointer, loc);

		// dest.base != src.base
		noOverlapExprs.add(ExpressionFactory.newBinaryExpression(loc, Operator.COMPNEQ, srcbase, destbase));
		// src.offset + size < dest.offset

		noOverlapExprs.add(constructPointerBinaryComparisonExpression(loc, IASTBinaryExpression.op_lessThan,
				constructPointerBinaryArithmeticExpression(loc, IASTBinaryExpression.op_plus, srcoffset, sizeIdExpr),
				destoffset));

		// dest.offset + size < src.offset
		noOverlapExprs.add(constructPointerBinaryComparisonExpression(loc, IASTBinaryExpression.op_lessThan,
				constructPointerBinaryArithmeticExpression(loc, IASTBinaryExpression.op_plus, destoffset, sizeIdExpr),
				srcoffset));

		// || over all three
		final RequiresSpecification noOverlapping =
				new RequiresSpecification(loc, false, ExpressionFactory.or(loc, noOverlapExprs));
		new Check(Spec.UNDEFINED_BEHAVIOR).annotate(noOverlapping);
		return noOverlapping;
	}

	/**
	 * Construct loop of the following form, where loopBody is a List of statements and the variables loopConterVariable
	 * and loopBoundVariable have the translated type of size_t.
	 *
	 * loopConterVariable := 0; while (#t~loopctr4 < loopBoundVariable) { ___loopBody___ loopConterVariable :=
	 * loopConterVariable + 1; }
	 *
	 * @param loopBoundVariableExpr
	 * @param loopCounterVariableId
	 * @param loopBody
	 * @return
	 */
	private ArrayList<Statement> constructCountingLoop(final Expression loopBoundVariableExpr,
			final AuxVarInfo loopCounterAux, final Expression loopCounterIncrementExpr, final List<Statement> loopBody,
			final String surroundingProcedure) {
		final CACSLLocation ignoreLoc = LocationFactory.createIgnoreCLocation();
		final ArrayList<Statement> stmt = new ArrayList<>();

		// initialize the counter to 0
		final Expression zero = mExpressionTranslation.constructLiteralForIntegerType(ignoreLoc,
				mTypeSizeAndOffsetComputer.getSizeT(), BigInteger.ZERO);
		stmt.add(StatementFactory.constructAssignmentStatement(ignoreLoc,
				new LeftHandSide[] { loopCounterAux.getLhs() }, new Expression[] { zero }));

		// final IdentifierExpression loopCounterVariableExpr =
		// ExpressionFactory.constructIdentifierExpression(ignoreLoc, BoogieType.TYPE_INT, loopCounterVariableId,
		// new DeclarationInformation(StorageClass.LOCAL, surroundingProcedure));

		final Expression condition = mExpressionTranslation.constructBinaryComparisonExpression(ignoreLoc,
				IASTBinaryExpression.op_lessThan, loopCounterAux.getExp(), mTypeSizeAndOffsetComputer.getSizeT(),
				loopBoundVariableExpr, mTypeSizeAndOffsetComputer.getSizeT());

		final ArrayList<Statement> bodyStmt = new ArrayList<>();
		bodyStmt.addAll(loopBody);

		// increment counter
		final VariableLHS ctrLHS = loopCounterAux.getLhs();
		final Expression counterPlusOne =
				mExpressionTranslation.constructArithmeticExpression(ignoreLoc, IASTBinaryExpression.op_plus,
						loopCounterAux.getExp(), mExpressionTranslation.getCTypeOfPointerComponents(),
						loopCounterIncrementExpr, mExpressionTranslation.getCTypeOfPointerComponents());
		bodyStmt.add(StatementFactory.constructAssignmentStatement(ignoreLoc, new LeftHandSide[] { ctrLHS },
				new Expression[] { counterPlusOne }));

		final Statement[] whileBody = bodyStmt.toArray(new Statement[bodyStmt.size()]);

		final WhileStatement whileStm =
				new WhileStatement(ignoreLoc, condition, new LoopInvariantSpecification[0], whileBody);
		stmt.add(whileStm);
		return stmt;
	}

	/**
	 * Return the assignments that we do in the loop body of our memcpy implementation.
	 *
	 * #memory_int[{ base: dest!base, offset: dest!offset + #t~loopctr6 * 1 }] := #memory_int[{ base: src!base, offset:
	 * src!offset + #t~loopctr6 * 1 }];
	 *
	 * @param heapDataArrays
	 * @param loopCtr
	 * @param destPtrName
	 * @param srcPtrName
	 * @return
	 */
	private ArrayList<Statement> constructMemcpyOrMemmoveLoopBody(final Collection<HeapDataArray> heapDataArrays,
			final AuxVarInfo loopCtr, final String destPtrName, final String srcPtrName,
			final String surroundingProcedure, final IASTNode hook) {

		final ILocation ignoreLoc = LocationFactory.createIgnoreCLocation();
		final ArrayList<Statement> result = new ArrayList<>();

		// final IdentifierExpression loopCtrExpr = //new IdentifierExpression(ignoreLoc, loopCtr);
		// ExpressionFactory.constructIdentifierExpression(ignoreLoc, BoogieType.TYPE_INT, loopCtr,
		// new DeclarationInformation(StorageClass.LOCAL, surroundingProcedure));

		final IdentifierExpression destPtrExpr = // new IdentifierExpression(ignoreLoc, destPtr);
				ExpressionFactory.constructIdentifierExpression(ignoreLoc,
						mBoogieTypeHelper.getBoogieTypeForPointerType(), destPtrName,
						new DeclarationInformation(StorageClass.IMPLEMENTATION_INPARAM, surroundingProcedure));
		final IdentifierExpression srcPtrExpr = // new IdentifierExpression(ignoreLoc, srcPtrName);
				ExpressionFactory.constructIdentifierExpression(ignoreLoc,
						mBoogieTypeHelper.getBoogieTypeForPointerType(), srcPtrName,
						new DeclarationInformation(StorageClass.IMPLEMENTATION_INPARAM, surroundingProcedure));

		final Expression currentDest = doPointerArithmetic(IASTBinaryExpression.op_plus, ignoreLoc, destPtrExpr,
				new RValue(loopCtr.getExp(), mExpressionTranslation.getCTypeOfPointerComponents()),
				new CPrimitive(CPrimitives.VOID), hook);
		final Expression currentSrc = doPointerArithmetic(IASTBinaryExpression.op_plus, ignoreLoc, srcPtrExpr,
				new RValue(loopCtr.getExp(), mExpressionTranslation.getCTypeOfPointerComponents()),
				new CPrimitive(CPrimitives.VOID), hook);
		for (final HeapDataArray hda : heapDataArrays) {
			final ArrayAccessExpression srcAcc = ExpressionFactory.constructNestedArrayAccessExpression(ignoreLoc,
					hda.getIdentifierExpression(), new Expression[] { currentSrc });
			final ArrayLHS destAcc = ExpressionFactory.constructNestedArrayLHS(ignoreLoc, hda.getVariableLHS(),
					new Expression[] { currentDest });
			result.add(StatementFactory.constructAssignmentStatement(ignoreLoc, new LeftHandSide[] { destAcc },
					new Expression[] { srcAcc }));

		}
		return result;
	}

	private ArrayList<Statement> constructMemsetLoopBody(final Collection<HeapDataArray> heapDataArrays,
			final AuxVarInfo loopCtr, final String ptr, final Expression valueExpr,
			final String surroundingProcedureName, final IASTNode hook) {

		final ILocation ignoreLoc = LocationFactory.createIgnoreCLocation();
		final ArrayList<Statement> result = new ArrayList<>();

		// final IdentifierExpression loopCtrExpr =
		// ExpressionFactory.constructIdentifierExpression(ignoreLoc, BoogieType.TYPE_INT,
		// loopCtr, new DeclarationInformation(StorageClass.LOCAL, surroundingProcedureName));

		final IdentifierExpression ptrExpr = ExpressionFactory.constructIdentifierExpression(ignoreLoc,
				mBoogieTypeHelper.getBoogieTypeForPointerType(), ptr,
				new DeclarationInformation(StorageClass.IMPLEMENTATION_INPARAM, surroundingProcedureName));

		final Expression currentPtr = doPointerArithmetic(IASTBinaryExpression.op_plus, ignoreLoc, ptrExpr,
				new RValue(loopCtr.getExp(), mExpressionTranslation.getCTypeOfPointerComponents()),
				new CPrimitive(CPrimitives.VOID), hook);
		for (final HeapDataArray hda : heapDataArrays) {
			final Expression convertedValue;
			final ExpressionResult exprRes =
					new ExpressionResult(new RValue(valueExpr, new CPrimitive(CPrimitives.UCHAR)));
			if (hda.getName().equals(SFO.POINTER)) {
				mExpressionTranslation.convertIntToInt(ignoreLoc, exprRes,
						mExpressionTranslation.getCTypeOfPointerComponents());
				final Expression zero = mExpressionTranslation.constructLiteralForIntegerType(ignoreLoc,
						mExpressionTranslation.getCTypeOfPointerComponents(), BigInteger.ZERO);
				convertedValue = constructPointerFromBaseAndOffset(zero, exprRes.mLrVal.getValue(), ignoreLoc);
			} else {
				// convert to smallest
				final List<ReadWriteDefinition> rwds =
						mMemoryModel.getReadWriteDefinitionForHeapDataArray(hda, getRequiredMemoryModelFeatures());
				// PRIMITIVE primitive = getCprimitiveThatFitsBest(rwds);
				final CPrimitives primitive = getCprimitiveThatFitsBest(hda.getSize());
				mExpressionTranslation.convertIntToInt(ignoreLoc, exprRes, new CPrimitive(primitive));
				convertedValue = exprRes.mLrVal.getValue();
			}
			final ArrayLHS destAcc = ExpressionFactory.constructNestedArrayLHS(ignoreLoc, hda.getVariableLHS(),
					new Expression[] { currentPtr });

			result.add(StatementFactory.constructAssignmentStatement(ignoreLoc, new LeftHandSide[] { destAcc },
					new Expression[] { convertedValue }));
		}
		return result;
	}

	/**
	 * Returns an CPrimitive which is unsigned, integer and not bool that has the smallest bytesize.
	 */
	private CPrimitives getCprimitiveThatFitsBest(final List<ReadWriteDefinition> test) {
		int smallestBytesize = Integer.MAX_VALUE;
		for (final ReadWriteDefinition rwd : test) {
			if (rwd.getBytesize() < smallestBytesize) {
				smallestBytesize = rwd.getBytesize();
			}
		}
		if (smallestBytesize == 0) {
			// we only have unbounded data types
			return CPrimitives.UCHAR;
		}
		for (final CPrimitives primitive : new CPrimitives[] { CPrimitives.UCHAR, CPrimitives.USHORT, CPrimitives.UINT,
				CPrimitives.ULONG, CPrimitives.ULONGLONG }) {
			if (mTypeSizes.getSize(primitive) == smallestBytesize) {
				return primitive;
			}
		}
		throw new AssertionError("don't know how to store value on heap");
	}

	/**
	 * Returns an CPrimitive which is unsigned, integer and not bool that has the smallest bytesize.
	 */
	private CPrimitives getCprimitiveThatFitsBest(final int byteSize) {
		if (byteSize == 0) {
			// we only have unbounded data types
			return CPrimitives.UCHAR;
		}
		for (final CPrimitives primitive : new CPrimitives[] { CPrimitives.UCHAR, CPrimitives.USHORT, CPrimitives.UINT,
				CPrimitives.ULONG, CPrimitives.ULONGLONG }) {
			if (mTypeSizes.getSize(primitive) == byteSize) {
				return primitive;
			}
		}
		throw new AssertionError("don't know how to store value on heap");
	}

	/**
	 * Construct specification and implementation for our Boogie representation of the memset function defined in
	 * 7.24.6.1 of C11. void *memset(void *s, int c, size_t n);
	 *
	 * @param main
	 * @param heapDataArrays
	 * @return
	 */
	private List<Declaration> declareMemset(final Dispatcher main, final Collection<HeapDataArray> heapDataArrays,
			final IASTNode hook) {
		final ArrayList<Declaration> decls = new ArrayList<>();
		final ILocation ignoreLoc = LocationFactory.createIgnoreCLocation();

		final String inParamPtr = "#ptr";
		final String inParamValue = "#value";
		final String inParamAmount = "#amount";
		final String outParamResult = "#res";
		final String procName = MemoryModelDeclarations.C_Memset.getName();

		final VarList inParamPtrVl =
				new VarList(ignoreLoc, new String[] { inParamPtr }, mTypeHandler.constructPointerType(ignoreLoc));
		final VarList inParamValueVl = new VarList(ignoreLoc, new String[] { inParamValue },
				mTypeHandler.cType2AstType(ignoreLoc, new CPrimitive(CPrimitives.INT)));
		final VarList inParamAmountVl = new VarList(ignoreLoc, new String[] { inParamAmount },
				mTypeHandler.cType2AstType(ignoreLoc, mTypeSizeAndOffsetComputer.getSizeT()));
		final VarList outParamResultVl =
				new VarList(ignoreLoc, new String[] { outParamResult }, mTypeHandler.constructPointerType(ignoreLoc));

		final VarList[] inParams = new VarList[] { inParamPtrVl, inParamValueVl, inParamAmountVl };
		final VarList[] outParams = new VarList[] { outParamResultVl };

		{
			final Procedure procDecl = new Procedure(ignoreLoc, new Attribute[0], procName, new String[0], inParams,
					outParams, new Specification[0], null);
			mProcedureManager.beginCustomProcedure(main, ignoreLoc, procName, procDecl);
		}

		final List<VariableDeclaration> decl = new ArrayList<>();
		final CPrimitive sizeT = mTypeSizeAndOffsetComputer.getSizeT();
		// final String loopCtr = mNameHandler.getTempVarUID(SFO.AUXVAR.LOOPCTR, sizeT);
		// final ASTType astType = mTypeHandler.cType2AstType(ignoreLoc, sizeT);
		// final VarList lcvl = new VarList(ignoreLoc, new String[] { loopCtr }, astType);
		// final VariableDeclaration loopCtrDec =
		// new VariableDeclaration(ignoreLoc, new Attribute[0], new VarList[] { lcvl });
		final AuxVarInfo loopCtrAux = AuxVarInfo.constructAuxVarInfo(ignoreLoc, main, sizeT, SFO.AUXVAR.LOOPCTR);
		decl.add(loopCtrAux.getVarDec());

		// converted value to unsigned char
		final IdentifierExpression inParamValueExpr = // new IdentifierExpression(ignoreLoc, inParamValue);
				ExpressionFactory.constructIdentifierExpression(ignoreLoc, BoogieType.TYPE_INT, inParamValue,
						new DeclarationInformation(StorageClass.IMPLEMENTATION_INPARAM, procName));

		final ExpressionResult exprRes =
				new ExpressionResult(new RValue(inParamValueExpr, new CPrimitive(CPrimitives.INT)));
		mExpressionTranslation.convertIntToInt(ignoreLoc, exprRes, new CPrimitive(CPrimitives.UCHAR));
		final Expression convertedValue = exprRes.mLrVal.getValue();

		final List<Statement> loopBody =
				constructMemsetLoopBody(heapDataArrays, loopCtrAux, inParamPtr, convertedValue, procName, hook);

		final Expression one = mExpressionTranslation.constructLiteralForIntegerType(ignoreLoc,
				mTypeSizeAndOffsetComputer.getSizeT(), BigInteger.ONE);
		final IdentifierExpression inParamAmountExprImpl =
				ExpressionFactory.constructIdentifierExpression(ignoreLoc, mBoogieTypeHelper.getBoogieTypeForSizeT(),
						inParamAmount, new DeclarationInformation(StorageClass.IMPLEMENTATION_INPARAM, procName));

		final List<Statement> stmt = constructCountingLoop(inParamAmountExprImpl, loopCtrAux, one, loopBody, procName);

		final Body procBody = mProcedureManager.constructBody(ignoreLoc,
				decl.toArray(new VariableDeclaration[decl.size()]), stmt.toArray(new Statement[stmt.size()]), procName);

		// make the specifications
		final ArrayList<Specification> specs = new ArrayList<>();

		// EDIT: the function handler should completely deal with modifies clauses if we announce them correctly
		// add modifies spec
		// final ModifiesSpecification modifiesSpec = announceModifiedGlobals(proc, heapDataArrays);
		// specs.add(modifiesSpec);
		// announceModifiedGlobals(procName, heapDataArrays);
		// heapDataArrays.forEach(
		// heapDataArray -> mProcedureManager.addModifiedGlobal(procName, heapDataArray.getVariableLHS()));

		// add requires #valid[#ptr!base];
		specs.addAll(constructPointerBaseValidityCheck(ignoreLoc, inParamPtr, procName));

		final IdentifierExpression inParamAmountExprDecl =
				ExpressionFactory.constructIdentifierExpression(ignoreLoc, mBoogieTypeHelper.getBoogieTypeForSizeT(),
						inParamAmount, new DeclarationInformation(StorageClass.PROC_FUNC_INPARAM, procName));
		// add requires (#size + #ptr!offset <= #length[#ptr!base] && 0 <= #ptr!offset);
		specs.addAll(constructPointerTargetFullyAllocatedCheck(ignoreLoc, inParamAmountExprDecl, inParamPtr, procName));

		// free ensures #res == dest;
		final EnsuresSpecification returnValue = mProcedureManager.constructEnsuresSpecification(
				ignoreLoc, true, ExpressionFactory.newBinaryExpression(ignoreLoc, Operator.COMPEQ,
						// new IdentifierExpression(ignoreLoc, outParamResult),
						ExpressionFactory.constructIdentifierExpression(ignoreLoc,
								mBoogieTypeHelper.getBoogieTypeForPointerType(), outParamResult,
								new DeclarationInformation(StorageClass.PROC_FUNC_OUTPARAM, procName)),
						// new IdentifierExpression(ignoreLoc, inParamPtr)));
						ExpressionFactory.constructIdentifierExpression(ignoreLoc,
								mBoogieTypeHelper.getBoogieTypeForPointerType(), inParamPtr,
								new DeclarationInformation(StorageClass.PROC_FUNC_INPARAM, procName))),
				Collections.emptySet());

		specs.add(returnValue);

		// add the procedure declaration
		// final Procedure procDecl = new Procedure(ignoreLoc, new Attribute[0], procName, new String[0], inParams,
		// outParams,
		// specs.toArray(new Specification[specs.size()]), null);
		// decls.add(procDecl);
		// mProcedureManager.registerProcedureDeclaration(procName, procDecl);
		mProcedureManager.addSpecificationsToCurrentProcedure(specs);

		// add the procedure implementation
		final Procedure procImpl = new Procedure(ignoreLoc, new Attribute[0], procName, new String[0], inParams,
				outParams, null, procBody);
		decls.add(procImpl);

		mProcedureManager.endCustomProcedure(main, procName);

		return decls;
	}

	/**
	 * Returns call to our memset procedure and announces that memset is required by our memory model.
	 */
	public CallStatement constructUltimateMemsetCall(final ILocation loc, final Expression pointer,
			final Expression value, final Expression amount, final VariableLHS resVar) {
		requireMemoryModelFeature(MemoryModelDeclarations.C_Memset);
		return StatementFactory.constructCallStatement(loc, false, new VariableLHS[] { resVar },
				MemoryModelDeclarations.C_Memset.getName(), new Expression[] { pointer, value, amount });
	}

	private VariableDeclaration constructMemoryArrayDeclaration(final ILocation loc, final String typeName,
			final ASTType astType) {
		final BoogieArrayType boogieType = BoogieType.createArrayType(0,
				new BoogieType[] { mTypeHandler.getBoogiePointerType() }, (BoogieType) astType.getBoogieType());
		final ASTType memoryArrayType = new ArrayType(loc, boogieType, new String[0],
				new ASTType[] { mTypeHandler.constructPointerType(loc) }, astType);
		final VarList varList = new VarList(loc, new String[] { SFO.MEMORY + "_" + typeName }, memoryArrayType);
		return new VariableDeclaration(loc, new Attribute[0], new VarList[] { varList });
	}

	private List<Declaration> constructWriteProcedures(final Dispatcher main, final ILocation loc,
			final Collection<HeapDataArray> heapDataArrays, final HeapDataArray heapDataArray, final IASTNode hook) {
		final List<Declaration> result = new ArrayList<>();
		for (final ReadWriteDefinition rda : mMemoryModel.getReadWriteDefinitionForHeapDataArray(heapDataArray,
				mRequiredMemoryModelFeatures)) {
			// result.add(constructWriteProcedure(loc, heapDataArrays, heapDataArray, rda));
			final Collection<Procedure> writeDeclaration =
					constructWriteProcedure(main, loc, heapDataArrays, heapDataArray, rda, hook);
			result.addAll(writeDeclaration);
			// assert writeDeclaration.getBody() == null : "if it has a body we should add it to the result here "
			// + "(only the declaration goes to the FucntionHanlder).";
			// mProcedureManager.registerProcedureDeclaration(rda.getWriteProcedureName(), writeDeclaration);
		}
		return result;
	}

	private List<Declaration> constructReadProcedures(final Dispatcher main, final ILocation loc,
			final HeapDataArray heapDataArray, final IASTNode hook) {
		final List<Declaration> result = new ArrayList<>();
		for (final ReadWriteDefinition rda : mMemoryModel.getReadWriteDefinitionForHeapDataArray(heapDataArray,
				mRequiredMemoryModelFeatures)) {
			// result.add(constructReadProcedure(loc, heapDataArray, rda));
			final List<Procedure> readDeclaration = constructReadProcedure(main, loc, heapDataArray, rda, hook);
			result.addAll(readDeclaration);
			// assert readDeclaration.getBody() == null : "if it has a body we should add it to the result here "
			// + "(only the declaration goes to the FucntionHanlder).";
			// mProcedureManager.registerProcedureDeclaration(rda.getReadProcedureName(), readDeclaration);

		}
		return result;
	}

	/**
	 * Note that we do not return a Procedure declaration here anymore because procedure declarations are handled by the
	 * FunctionHandler (DD: Do you mean {@link ProcedureManager} ??) directly. So the return value will be an empty set,
	 * or perhaps in the future an implementation, should we ever want one.
	 *
	 * @param main
	 * @param loc
	 * @param heapDataArrays
	 * @param heapDataArray
	 * @param rda
	 * @return
	 */
	/**
	 * Note that we do not return a Procedure declaration here anymore because procedure declarations are handled by the
	 * FunctionHandler (DD: Do you mean {@link ProcedureManager} ??) directly. So the return value will be an empty set,
	 * or perhaps in the future an implementation, should we ever want one.
	 *
	 * @param main
	 * @param loc
	 * @param heapDataArrays
	 * @param heapDataArray
	 * @param rda
	 * @return
	 */
	private Collection<Procedure> constructWriteProcedure(final Dispatcher main, final ILocation loc,
			final Collection<HeapDataArray> heapDataArrays, final HeapDataArray heapDataArray,
			final ReadWriteDefinition rda, final IASTNode hook) {
		if (rda.alsoUnchecked()) {
			constructSingleWriteProcedure(main, loc, heapDataArrays, heapDataArray, rda, true);
		}
		constructSingleWriteProcedure(main, loc, heapDataArrays, heapDataArray, rda, false);
		return Collections.emptySet();
	}

	private void constructSingleWriteProcedure(final Dispatcher main, final ILocation loc,
			final Collection<HeapDataArray> heapDataArrays, final HeapDataArray heapDataArray,
			final ReadWriteDefinition rda, final boolean unchecked) {
		final String inPtr = "#ptr";
		final String writtenTypeSize = "#sizeOfWrittenType";
		final ASTType valueAstType = rda.getASTType();

		// create procedure signature
		final String procName;
		if (unchecked) {
			procName = rda.getUncheckedWriteProcedureName();
		} else {
			procName = rda.getWriteProcedureName();
		}

		final IdentifierExpression inPtrExp =
				ExpressionFactory.constructIdentifierExpression(loc, mBoogieTypeHelper.getBoogieTypeForPointerType(),
						inPtr, new DeclarationInformation(StorageClass.PROC_FUNC_INPARAM, procName));

		final ASTType sizetType = mTypeHandler.cType2AstType(loc, mTypeSizeAndOffsetComputer.getSizeT());
		final VarList[] inWrite = new VarList[] { new VarList(loc, new String[] { "#value" }, valueAstType),
				new VarList(loc, new String[] { inPtr }, mTypeHandler.constructPointerType(loc)),
				new VarList(loc, new String[] { writtenTypeSize }, sizetType) };

		final Procedure proc = new Procedure(loc, new Attribute[0], procName, new String[0], inWrite, new VarList[0],
				new Specification[0], null);
		mProcedureManager.beginCustomProcedure(main, loc, procName, proc);

		// specification for memory writes
		final ArrayList<Specification> swrite = new ArrayList<>();
		if (!unchecked) {
			swrite.addAll(constructPointerBaseValidityCheck(loc, inPtr, procName));

			final Expression sizeWrite = ExpressionFactory.constructIdentifierExpression(loc, BoogieType.TYPE_INT,
					writtenTypeSize, new DeclarationInformation(StorageClass.PROC_FUNC_INPARAM, procName));
			swrite.addAll(constructPointerTargetFullyAllocatedCheck(loc, sizeWrite, inPtr, procName));
		}

		final boolean floating2bitvectorTransformationNeeded = mMemoryModel instanceof MemoryModel_SingleBitprecise
				&& rda.getCPrimitiveCategory().contains(CPrimitiveCategory.FLOATTYPE);

		final Expression nonFPBVReturnValue = ExpressionFactory.constructIdentifierExpression(loc,
				mBoogieTypeHelper.getBoogieTypeForBoogieASTType(valueAstType), "#value",
				new DeclarationInformation(StorageClass.PROC_FUNC_INPARAM, procName));
		final CPrimitives cprimitive;
		final Expression returnValue;
		if (floating2bitvectorTransformationNeeded) {
			cprimitive = rda.getPrimitives().iterator().next();
			if (mFpToIeeeBvExtension) {
				returnValue = mExpressionTranslation.transformFloatToBitvector(loc, nonFPBVReturnValue, cprimitive);
			} else {
				returnValue = ExpressionFactory.constructIdentifierExpression(loc,
						mBoogieTypeHelper.getBoogieTypeForBoogieASTType(valueAstType), "#valueAsBitvector",
						new DeclarationInformation(StorageClass.PROC_FUNC_OUTPARAM, procName));
			}
		} else {
			cprimitive = null;
			returnValue = ExpressionFactory.constructIdentifierExpression(loc,
					mBoogieTypeHelper.getBoogieTypeForBoogieASTType(valueAstType), "#value",
					new DeclarationInformation(StorageClass.PROC_FUNC_INPARAM, procName));
		}

		final List<Expression> conjuncts = new ArrayList<>();
		if (rda.getBytesize() == heapDataArray.getSize()) {
			conjuncts.addAll(constructConjunctsForWriteEnsuresSpecification(loc, heapDataArrays, heapDataArray,
					returnValue, x -> x, inPtrExp, x -> x));
		} else if (rda.getBytesize() < heapDataArray.getSize()) {
			final Function<Expression, Expression> valueExtension =
					x -> mExpressionTranslation.signExtend(loc, x, rda.getBytesize() * 8, heapDataArray.getSize() * 8);
			conjuncts.addAll(constructConjunctsForWriteEnsuresSpecification(loc, heapDataArrays, heapDataArray,
					returnValue, valueExtension, inPtrExp, x -> x));
		} else {
			assert rda.getBytesize() % heapDataArray.getSize() == 0 : "incompatible sizes";
			for (int i = 0; i < rda.getBytesize() / heapDataArray.getSize(); i++) {
				final Function<Expression, Expression> extractBits;
				final int currentI = i;
				extractBits = x -> mExpressionTranslation.extractBits(loc, x,
						heapDataArray.getSize() * (currentI + 1) * 8, heapDataArray.getSize() * currentI * 8);
				if (i == 0) {
					conjuncts.addAll(constructConjunctsForWriteEnsuresSpecification(loc, heapDataArrays, heapDataArray,
							returnValue, extractBits, inPtrExp, x -> x));
				} else {
					final BigInteger additionalOffset = BigInteger.valueOf(i * heapDataArray.getSize());
					final Function<Expression, Expression> pointerAddition =
							x -> addIntegerConstantToPointer(loc, x, additionalOffset);
					conjuncts.addAll(constructConjunctsForWriteEnsuresSpecification(loc, heapDataArrays, heapDataArray,
							returnValue, extractBits, inPtrExp, pointerAddition));
				}
			}
		}
		if (floating2bitvectorTransformationNeeded && !mFpToIeeeBvExtension) {
			// TODO: not sure about the storage class here
			final Expression returnValueAsBitvector = ExpressionFactory.constructIdentifierExpression(loc,
					mBoogieTypeHelper.getBoogieTypeForBoogieASTType(valueAstType), "#valueAsBitvector",
					new DeclarationInformation(StorageClass.LOCAL, procName));

			final Expression transformedToFloat =
					mExpressionTranslation.transformBitvectorToFloat(loc, returnValueAsBitvector, cprimitive);
			final Expression inputValue = // new IdentifierExpression(loc, "#value");
					ExpressionFactory.constructIdentifierExpression(loc, (BoogieType) transformedToFloat.getType(),
							"#value", new DeclarationInformation(StorageClass.LOCAL, procName));

			final Expression eq =
					ExpressionFactory.newBinaryExpression(loc, Operator.COMPEQ, transformedToFloat, inputValue);
			conjuncts.add(eq);
			final Expression conjunction = ExpressionFactory.and(loc, conjuncts);
			final ASTType type = ((TypeHandler) mTypeHandler).bytesize2asttype(loc, cprimitive.getPrimitiveCategory(),
					mTypeSizes.getSize(cprimitive));
			final VarList[] parameters = new VarList[] { new VarList(loc, new String[] { "#valueAsBitvector" }, type) };
			final QuantifierExpression qe =
					new QuantifierExpression(loc, false, new String[0], parameters, new Attribute[0], conjunction);
			swrite.add(mProcedureManager.constructEnsuresSpecification(loc, false, qe,
					Collections.singleton(heapDataArray.getVariableLHS())));
		} else {
			swrite.add(mProcedureManager.constructEnsuresSpecification(loc, false,
					ExpressionFactory.and(loc, conjuncts), Collections.singleton(heapDataArray.getVariableLHS())));
		}

		mProcedureManager.addSpecificationsToCurrentProcedure(swrite);
		mProcedureManager.endCustomProcedure(main, procName);
	}

	private static List<Expression> constructConjunctsForWriteEnsuresSpecification(final ILocation loc,
			final Collection<HeapDataArray> heapDataArrays, final HeapDataArray heapDataArray, final Expression value,
			final Function<Expression, Expression> valueModification, final IdentifierExpression inPtrExp,
			final Function<Expression, Expression> ptrModification) {
		final List<Expression> conjuncts = new ArrayList<>();
		for (final HeapDataArray other : heapDataArrays) {
			if (heapDataArray == other) {
				conjuncts.add(ensuresHeapArrayUpdate(loc, value, valueModification, inPtrExp, ptrModification, other));
			} else {
				conjuncts.add(ensuresHeapArrayHardlyModified(loc, inPtrExp, ptrModification, other));
			}

		}
		return conjuncts;
	}

	/**
	 * Note: Currently this returns an empty list, see {@link #constructWriteProcedure} on this topic.
	 *
	 * @param main
	 * @param loc
	 * @param hda
	 * @param rda
	 * @return
	 */
	private List<Procedure> constructReadProcedure(final Dispatcher main, final ILocation loc, final HeapDataArray hda,
			final ReadWriteDefinition rda, final IASTNode hook) {
		// specification for memory reads
		final String returnValue = "#value";
		final ASTType valueAstType = rda.getASTType();
		final String ptrId = "#ptr";
		final String readTypeSize = "#sizeOfReadType";

		// create procedure signature
		{
			final ASTType sizetType = mTypeHandler.cType2AstType(loc, mTypeSizeAndOffsetComputer.getSizeT());
			final VarList[] inRead =
					new VarList[] { new VarList(loc, new String[] { ptrId }, mTypeHandler.constructPointerType(loc)),
							new VarList(loc, new String[] { readTypeSize }, sizetType) };

			final VarList[] outRead = new VarList[] { new VarList(loc, new String[] { returnValue }, valueAstType) };
			final Procedure decl = new Procedure(loc, new Attribute[0], rda.getReadProcedureName(), new String[0],
					inRead, outRead, new Specification[0], null);
			mProcedureManager.beginCustomProcedure(main, loc, rda.getReadProcedureName(), decl);

		}

		// create procedure specifications
		final ArrayList<Specification> sread = new ArrayList<>();

		sread.addAll(constructPointerBaseValidityCheck(loc, ptrId, rda.getReadProcedureName()));

		final Expression sizeRead = ExpressionFactory.constructIdentifierExpression(loc, BoogieType.TYPE_INT,
				readTypeSize, new DeclarationInformation(StorageClass.PROC_FUNC_INPARAM, rda.getReadProcedureName()));

		sread.addAll(constructPointerTargetFullyAllocatedCheck(loc, sizeRead, ptrId, rda.getReadProcedureName()));

		final Expression arr = hda.getIdentifierExpression();
		// mBoogieTypeHelper.constructIdentifierExpressionForHeapDataArray(loc, hda, rda.getReadProcedureName());
		final Expression ptrExpr = // new IdentifierExpression(loc, ptrId);
				ExpressionFactory.constructIdentifierExpression(loc, mBoogieTypeHelper.getBoogieTypeForPointerType(),
						ptrId, new DeclarationInformation(StorageClass.PROC_FUNC_INPARAM, rda.getReadProcedureName()));

		Expression dataFromHeap;
		if (rda.getBytesize() == hda.getSize()) {
			dataFromHeap = constructOneDimensionalArrayAccess(loc, arr, ptrExpr);
		} else if (rda.getBytesize() < hda.getSize()) {
			dataFromHeap = mExpressionTranslation.extractBits(loc,
					constructOneDimensionalArrayAccess(loc, arr, ptrExpr), rda.getBytesize() * 8, 0);
		} else {
			assert rda.getBytesize() % hda.getSize() == 0 : "incompatible sizes";
			final Expression[] dataChunks = new Expression[rda.getBytesize() / hda.getSize()];
			for (int i = 0; i < dataChunks.length; i++) {
				if (i == 0) {
					dataChunks[dataChunks.length - 1 - 0] = constructOneDimensionalArrayAccess(loc, arr, ptrExpr);
				} else {
					final Expression index =
							addIntegerConstantToPointer(loc, ptrExpr, BigInteger.valueOf(i * hda.getSize()));
					dataChunks[dataChunks.length - 1 - i] = constructOneDimensionalArrayAccess(loc, arr, index);
				}
			}
			dataFromHeap = mExpressionTranslation.concatBits(loc, Arrays.asList(dataChunks), hda.getSize());
		}

		if (mMemoryModel instanceof MemoryModel_SingleBitprecise
				&& rda.getCPrimitiveCategory().contains(CPrimitiveCategory.FLOATTYPE)) {
			final CPrimitives cprimitive = rda.getPrimitives().iterator().next();
			dataFromHeap = mExpressionTranslation.transformBitvectorToFloat(loc, dataFromHeap, cprimitive);
		}

		final Expression valueExpr = ExpressionFactory.constructIdentifierExpression(loc,
				mBoogieTypeHelper.getBoogieTypeForBoogieASTType(valueAstType), returnValue,
				new DeclarationInformation(StorageClass.PROC_FUNC_OUTPARAM, rda.getReadProcedureName()));
		final Expression equality =
				ExpressionFactory.newBinaryExpression(loc, Operator.COMPEQ, valueExpr, dataFromHeap);
		sread.add(mProcedureManager.constructEnsuresSpecification(loc, false, equality, Collections.emptySet()));
		// sread.add(StatementFactory.constructEnsuresSpecification(loc, false, equality));
		// final Procedure result = new Procedure(loc, new Attribute[0], rda.getReadProcedureName(), new String[0],
		// inRead,
		// outRead, sread.toArray(new Specification[sread.size()]), null);

		mProcedureManager.addSpecificationsToCurrentProcedure(sread);
		mProcedureManager.endCustomProcedure(main, rda.getReadProcedureName());

		return Collections.emptyList();
	}

	private Expression addIntegerConstantToPointer(final ILocation loc, final Expression ptrExpr,
			final BigInteger integerConstant) {
		final Expression base = getPointerBaseAddress(ptrExpr, loc);
		final Expression offset = getPointerOffset(ptrExpr, loc);
		final Expression addition = mExpressionTranslation.constructLiteralForIntegerType(loc,
				mTypeSizeAndOffsetComputer.getSizeT(), integerConstant);
		final Expression offsetPlus =
				mExpressionTranslation.constructArithmeticExpression(loc, IASTBinaryExpression.op_plus, offset,
						mTypeSizeAndOffsetComputer.getSizeT(), addition, mTypeSizeAndOffsetComputer.getSizeT());
		return constructPointerFromBaseAndOffset(base, offsetPlus, loc);
	}

	private static Expression constructOneDimensionalArrayAccess(final ILocation loc, final Expression arr,
			final Expression index) {
		final Expression[] singletonIndex = new Expression[] { index };
		return ExpressionFactory.constructNestedArrayAccessExpression(loc, arr, singletonIndex);
	}

	private static Expression constructOneDimensionalArrayStore(final ILocation loc, final Expression arr,
			final Expression index, final Expression newValue) {
		final Expression[] singletonIndex = new Expression[] { index };
		// return new ArrayStoreExpression(loc, arr, singletonIndex, newValue);
		return ExpressionFactory.constructArrayStoreExpression(loc, arr, singletonIndex, newValue);
	}

	/**
	 * Construct a Boogie statement of the following form. arrayIdentifier[index] := value; TODO 2017-01-07 Matthias:
	 * This method is not directly related to the MemoryHandler and should probably moved to a some class for utility
	 * functions.
	 */
	public static AssignmentStatement constructOneDimensionalArrayUpdate(final Dispatcher main, final ILocation loc,
			final Expression index, final VariableLHS arrayLhs, final Expression value) {
		final LeftHandSide[] lhs = new LeftHandSide[] {
				ExpressionFactory.constructNestedArrayLHS(loc, arrayLhs, new Expression[] { index }) };
		final Expression[] rhs = new Expression[] { value };
		final AssignmentStatement assignment = StatementFactory.constructAssignmentStatement(loc, lhs, rhs);
		return assignment;
	}

	// ensures #memory_X == old(#memory_X)[#ptr := #value];
	private static Expression ensuresHeapArrayUpdate(final ILocation loc, final Expression valueExpr,
			final Function<Expression, Expression> valueModification, final IdentifierExpression ptrExpr,
			final Function<Expression, Expression> ptrModification, final HeapDataArray hda) {
		final Expression memArray = hda.getIdentifierExpression();
		// ExpressionFactory.constructIdentifierExpression(loc, hda.getBoogieType(),
		// hda.getVariableName(), new DeclarationInformation(StorageClass.GLOBAL, null));
		return ensuresArrayUpdate(loc, valueModification.apply(valueExpr), ptrModification.apply(ptrExpr), memArray);
	}

	// #memory_$Pointer$ == old(#memory_X)[#ptr := #memory_X[#ptr]];
	private static Expression ensuresHeapArrayHardlyModified(final ILocation loc, final IdentifierExpression ptrExpr, // final
																														// String
																														// ptrId,
			final Function<Expression, Expression> ptrModification, final HeapDataArray hda) {
		final Expression memArray = // new IdentifierExpression(loc, hda.getVariableName());
				// ExpressionFactory.constructIdentifierExpression(loc,
				// hda.getBoogieType(), hda.getVariableName(),
				// new DeclarationInformation(StorageClass.GLOBAL, null));
				hda.getIdentifierExpression();
		// final Expression ptrExpr = //new IdentifierExpression(loc, ptrId);
		// ExpressionFactory.constructIdentifierExpression(loc,
		// BoogieTypeHelper.getBoogieTypeForPointerType(), ptrId,
		// new DeclarationInformation(StorageClass.LOCAL, procName));

		final Expression aae = constructOneDimensionalArrayAccess(loc, memArray, ptrExpr);
		return ensuresArrayUpdate(loc, aae, ptrModification.apply(ptrExpr), memArray);
	}

	private static Expression ensuresArrayUpdate(final ILocation loc, final Expression newValue, final Expression index,
			final Expression arrayExpr) {
		final Expression oldArray =
				ExpressionFactory.constructUnaryExpression(loc, UnaryExpression.Operator.OLD, arrayExpr);
		final Expression ase = constructOneDimensionalArrayStore(loc, oldArray, index, newValue);
		final Expression eq = ExpressionFactory.newBinaryExpression(loc, Operator.COMPEQ, arrayExpr, ase);
		return eq;
	}

	/**
	 *
	 * @param loc
	 *            location of translation unit
	 * @param vars
	 * @return ModifiesSpecification which says that all variables of the set vars can be modified.
	 */
	private static <T> ModifiesSpecification constructModifiesSpecification(final ILocation loc,
			final Collection<T> vars, final Function<T, VariableLHS> varToLHS) {
		final VariableLHS[] modifie = new VariableLHS[vars.size()];
		int i = 0;
		for (final T variable : vars) {
			modifie[i] = varToLHS.apply(variable);
			i++;
		}
		return new ModifiesSpecification(loc, false, modifie);
	}

	/**
	 * Constructs specification that target of pointer is fully allocated. The specification checks that the address of
	 * the pointer plus the size of the type that we read/write is smaller than or equal to the size of the allocated
	 * memory at the base address of the pointer. Furthermore, we check that the offset is greater than or equal to
	 * zero.
	 *
	 * In case mPointerBaseValidity is ASSERTandASSUME, we construct the requires specification
	 * <code>requires (#size + #ptr!offset <= #length[#ptr!base] && 0 <= #ptr!offset)</code>.
	 *
	 * In case mPointerBaseValidity is ASSERTandASSUME, we construct the <b>free</b> requires specification
	 * <code>free requires (#size + #ptr!offset <= #length[#ptr!base] && 0 <= #ptr!offset)</code>.
	 *
	 * In case mPointerBaseValidity is IGNORE, we construct nothing.
	 *
	 * @param loc
	 *            location of translation unit
	 * @param size
	 *            Expression that represents the size of the data type that we read/write at the address of the pointer.
	 * @param ptrName
	 *            name of pointer whose base address is checked
	 * @return A list containing the created specification
	 */
	private List<Specification> constructPointerTargetFullyAllocatedCheck(final ILocation loc, final Expression size,
			final String ptrName, final String procedureName) {
		if (mPointerTargetFullyAllocated == PointerCheckMode.IGNORE) {
			// add nothing
			return Collections.emptyList();
		}
		Expression leq;
		{
			final Expression ptrExpr = ExpressionFactory.constructIdentifierExpression(loc,
					mBoogieTypeHelper.getBoogieTypeForPointerType(), ptrName,
					new DeclarationInformation(StorageClass.PROC_FUNC_INPARAM, procedureName));

			final Expression ptrBase = getPointerBaseAddress(ptrExpr, loc);
			final Expression aae = ExpressionFactory.constructNestedArrayAccessExpression(loc, getLengthArray(loc),
					new Expression[] { ptrBase });
			final Expression ptrOffset = getPointerOffset(ptrExpr, loc);
			final Expression sum =
					constructPointerBinaryArithmeticExpression(loc, IASTBinaryExpression.op_plus, size, ptrOffset);
			leq = constructPointerBinaryComparisonExpression(loc, IASTBinaryExpression.op_lessEqual, sum, aae);
		}
		final Expression offsetGeqZero;
		{
			final Expression ptrExpr = ExpressionFactory.constructIdentifierExpression(loc,
					mBoogieTypeHelper.getBoogieTypeForPointerType(), ptrName,
					new DeclarationInformation(StorageClass.PROC_FUNC_INPARAM, procedureName));

			final Expression ptrOffset = getPointerOffset(ptrExpr, loc);
			final Expression nr0 = mExpressionTranslation.constructLiteralForIntegerType(loc,
					mExpressionTranslation.getCTypeOfPointerComponents(), BigInteger.ZERO);
			offsetGeqZero =
					constructPointerBinaryComparisonExpression(loc, IASTBinaryExpression.op_lessEqual, nr0, ptrOffset);

		}

		if (mTypeHandler.isBitvectorTranslation()) {
			/*
			 * Check that "#ptr!offset <= #ptr!offset + #sizeOf[Written|Read]Type", i.e., the sum does not overflow.
			 * (This works because #size.. is positive.)
			 */
			final Expression ptrExpr = ExpressionFactory.constructIdentifierExpression(loc,
					mBoogieTypeHelper.getBoogieTypeForPointerType(), ptrName,
					new DeclarationInformation(StorageClass.PROC_FUNC_INPARAM, procedureName));
			final Expression ptrOffset = getPointerOffset(ptrExpr, loc);
			final Expression sum =
					constructPointerBinaryArithmeticExpression(loc, IASTBinaryExpression.op_plus, size, ptrOffset);
			final Expression noOverFlowInSum = constructPointerBinaryComparisonExpression(loc,
					IASTBinaryExpression.op_lessEqual, ptrOffset, sum);
			leq = ExpressionFactory.newBinaryExpression(loc, BinaryExpression.Operator.LOGICAND, leq, noOverFlowInSum);
		}

		final Expression offsetInAllocatedRange =
				ExpressionFactory.newBinaryExpression(loc, BinaryExpression.Operator.LOGICAND, leq, offsetGeqZero);
		final boolean isFreeRequires;
		if (mPointerTargetFullyAllocated == PointerCheckMode.ASSERTandASSUME) {
			isFreeRequires = false;
		} else {
			assert mPointerTargetFullyAllocated == PointerCheckMode.ASSUME;
			isFreeRequires = true;
		}
		final RequiresSpecification spec = new RequiresSpecification(loc, isFreeRequires, offsetInAllocatedRange);
		final Check check = new Check(Spec.MEMORY_DEREFERENCE);
		check.annotate(spec);
		return Collections.singletonList(spec);
	}

	/**
	 * Construct specification that the pointer base address is valid. In case
	 * {@link #getPointerBaseValidityCheckMode()} is ASSERTandASSUME, we add the requires specification
	 * <code>requires #valid[#ptr!base]</code>. In case {@link #getPointerBaseValidityCheckMode()} is ASSERTandASSUME,
	 * we add the <b>free</b> requires specification <code>free requires #valid[#ptr!base]</code>. In case
	 * {@link #getPointerBaseValidityCheckMode()} is IGNORE, we add nothing.
	 *
	 * @param loc
	 *            location of translation unit
	 * @param ptrName
	 *            name of pointer whose base address is checked
	 * @param procedureName
	 *            name of the procedure where the specifications will be added
	 * @return A list containing the created specifications.
	 */
	private List<Specification> constructPointerBaseValidityCheck(final ILocation loc, final String ptrName,
			final String procedureName) {
		if (mPointerBaseValidity == PointerCheckMode.IGNORE) {
			// add nothing
			return Collections.emptyList();
		}
		final Expression ptrExpr =
				ExpressionFactory.constructIdentifierExpression(loc, mBoogieTypeHelper.getBoogieTypeForPointerType(),
						ptrName, new DeclarationInformation(StorageClass.PROC_FUNC_INPARAM, procedureName));
		final Expression isValid = constructPointerBaseValidityCheckExpr(loc, ptrExpr);
		final boolean isFreeRequires;
		if (mPointerBaseValidity == PointerCheckMode.ASSERTandASSUME) {
			isFreeRequires = false;
		} else {
			assert mPointerBaseValidity == PointerCheckMode.ASSUME;
			isFreeRequires = true;
		}
		final RequiresSpecification spec = new RequiresSpecification(loc, isFreeRequires, isValid);
		final Check check = new Check(Spec.MEMORY_DEREFERENCE);
		check.annotate(spec);
		return Collections.singletonList(spec);
	}

	/**
	 * Construct expression that states that the base address of ptr is valid. Depending on the settings this expression
	 * is one of the following
	 * <ul>
	 * <li>#valid[#ptr!base]
	 * <li>#valid[#ptr!base] == 1
	 * <li>#valid[#ptr!base] == 1bv1
	 * </ul>
	 *
	 *
	 *
	 */
	public Expression constructPointerBaseValidityCheckExpr(final ILocation loc, final Expression ptr) {
		final Expression ptrBase = getPointerBaseAddress(ptr, loc);
		final ArrayAccessExpression aae = ExpressionFactory.constructNestedArrayAccessExpression(loc,
				getValidArray(loc), new Expression[] { ptrBase });
		final Expression isValid = mBooleanArrayHelper.compareWithTrue(aae);
		return isValid;
	}

	/**
	 * @param loc
	 *            location of translation unit
	 * @return new IdentifierExpression that represents the <em>#length array</em>
	 */
	public Expression getLengthArray(final ILocation loc) {
		requireMemoryModelFeature(MemoryModelDeclarations.Ultimate_Length);

		// final BoogieArrayType lengthArrayType =
		// BoogieType.createArrayType(0, new BoogieType[] { BoogieType.TYPE_INT }, BoogieType.TYPE_INT);
		// final DeclarationInformation lengthArrayDeclarationInfo = new DeclarationInformation(StorageClass.GLOBAL,
		// null);
		//
		// // return new IdentifierExpression(loc, SFO.LENGTH);
		// return ExpressionFactory.constructIdentifierExpression(loc, lengthArrayType,
		// MemoryModelDeclarations.Ultimate_Length.getName(), lengthArrayDeclarationInfo);

		final MemoryModelDeclarationInfo validMmfInfo =
				getMemoryModelDeclarationInfo(MemoryModelDeclarations.Ultimate_Length);
		return validMmfInfo.constructIdentiferExpression(loc);

	}

	/**
	 * @param loc
	 *            location of translation unit
	 * @return new IdentifierExpression that represents the <em>#length array</em>
	 */
	public VariableLHS getLengthArrayLhs(final ILocation loc) {
		requireMemoryModelFeature(MemoryModelDeclarations.Ultimate_Length);
		//
		// final BoogieArrayType lengthArrayType =
		// BoogieType.createArrayType(0, new BoogieType[] { BoogieType.TYPE_INT }, BoogieType.TYPE_INT);
		// final DeclarationInformation lengthArrayDeclarationInfo = new DeclarationInformation(StorageClass.GLOBAL,
		// null);
		//
		// // return new IdentifierExpression(loc, SFO.LENGTH);
		// return ExpressionFactory.constructVariableLHS(loc, lengthArrayType,
		// MemoryModelDeclarations.Ultimate_Length.getName(), lengthArrayDeclarationInfo);

		final MemoryModelDeclarationInfo validMmfInfo =
				getMemoryModelDeclarationInfo(MemoryModelDeclarations.Ultimate_Length);
		return validMmfInfo.constructVariableLHS(loc);

	}

	/**
	 * @param loc
	 *            location of translation unit
	 * @return new IdentifierExpression that represents the <em>#valid array</em>
	 */
	public Expression getValidArray(final ILocation loc) {
		requireMemoryModelFeature(MemoryModelDeclarations.Ultimate_Valid);
		// final BoogieArrayType validArrayType =
		// BoogieType.createArrayType(0, new BoogieType[] { BoogieType.TYPE_INT }, BoogieType.TYPE_INT);
		// final DeclarationInformation validArrayDeclarationInfo = new DeclarationInformation(StorageClass.GLOBAL,
		// null);

		final MemoryModelDeclarationInfo validMmfInfo =
				getMemoryModelDeclarationInfo(MemoryModelDeclarations.Ultimate_Valid);
		return validMmfInfo.constructIdentiferExpression(loc);

		// return ExpressionFactory.constructIdentifierExpression(loc, validArrayType,
		// MemoryModelDeclarations.Ultimate_Valid.getName(), validArrayDeclarationInfo);
	}

	public VariableLHS getValidArrayLhs(final ILocation loc) {
		requireMemoryModelFeature(MemoryModelDeclarations.Ultimate_Valid);
		// final BoogieArrayType validArrayType =
		// BoogieType.createArrayType(0, new BoogieType[] { BoogieType.TYPE_INT }, BoogieType.TYPE_INT);
		// final DeclarationInformation validArrayDeclarationInfo = new DeclarationInformation(StorageClass.GLOBAL,
		// null);
		// return ExpressionFactory.constructVariableLHS(loc, validArrayType,
		// MemoryModelDeclarations.Ultimate_Valid.getName(), validArrayDeclarationInfo);

		final MemoryModelDeclarationInfo validMmfInfo =
				getMemoryModelDeclarationInfo(MemoryModelDeclarations.Ultimate_Valid);
		return validMmfInfo.constructVariableLHS(loc);
	}

	/**
	 * Compare a pointer component (base or offset) to another expression.
	 *
	 * @param op
	 *            One of the comparison operators defined in {@link IASTBinaryExpression}.
	 */
	private Expression constructPointerBinaryComparisonExpression(final ILocation loc, final int op,
			final Expression left, final Expression right) {
		return mExpressionTranslation.constructBinaryComparisonExpression(loc, op, left,
				mExpressionTranslation.getCTypeOfPointerComponents(), right,
				mExpressionTranslation.getCTypeOfPointerComponents());
	}

	/**
	 * Create an arithmetic expression from a pointer component (base or offset) and another expression.
	 *
	 * @param op
	 *            One of the arithmetic operators defined in {@link IASTBinaryExpression}.
	 */
	private Expression constructPointerBinaryArithmeticExpression(final ILocation loc, final int op,
			final Expression left, final Expression right) {
		return mExpressionTranslation.constructArithmeticExpression(loc, op, left,
				mExpressionTranslation.getCTypeOfPointerComponents(), right,
				mExpressionTranslation.getCTypeOfPointerComponents());
	}

	public Collection<Statement> getChecksForFreeCall(final ILocation loc, final RValue pointerToBeFreed) {
		assert pointerToBeFreed.getCType().getUnderlyingType() instanceof CPointer;

		final boolean checkIfFreedPointerIsValid =
				mPreferences.getBoolean(CACSLPreferenceInitializer.LABEL_CHECK_FREE_VALID);

		final Expression nr0 = mExpressionTranslation.constructLiteralForIntegerType(loc,
				mExpressionTranslation.getCTypeOfPointerComponents(), BigInteger.ZERO);
		// final Expression freedAddressExpr = new IdentifierExpression(loc, ADDR);
		final Expression valid = getValidArray(loc);
		// final Expression addrOffset = ExpressionFactory.constructStructAccessExpression(loc, freedAddressExpr,
		// SFO.POINTER_OFFSET);
		final Expression addrOffset = getPointerOffset(pointerToBeFreed.getValue(), loc);
		// final Expression addrBase = ExpressionFactory.constructStructAccessExpression(loc, freedAddressExpr,
		// SFO.POINTER_BASE);
		final Expression addrBase = getPointerBaseAddress(pointerToBeFreed.getValue(), loc);
		final Expression[] idcFree = new Expression[] { addrBase };

		final Collection<Statement> result = new ArrayList<>();

		if (checkIfFreedPointerIsValid) {
			/*
			 * creating the specification according to C99:7.20.3.2-2: The free function causes the space pointed to by
			 * ptr to be deallocated, that is, made available for further allocation. If ptr is a null pointer, no
			 * action occurs. Otherwise, if the argument does not match a pointer earlier returned by the calloc,
			 * malloc, or realloc function, or if the space has been deallocated by a call to free or realloc, the
			 * behavior is undefined.
			 */
			final Check check = new Check(Spec.MEMORY_FREE);
			// final boolean free = //!mCheckFreeValid;
			// mPreferences.getBoolean(CACSLPreferenceInitializer.LABEL_CHECK_FREE_VALID);
			// final RequiresSpecification offsetZero = new RequiresSpecification(loc, free,
			// ExpressionFactory.newBinaryExpression(loc, Operator.COMPEQ, addrOffset, nr0));
			final AssertStatement offsetZero = new AssertStatement(loc,
					ExpressionFactory.newBinaryExpression(loc, Operator.COMPEQ, addrOffset, nr0));
			check.annotate(offsetZero);
			// specFree.add(offsetZero);
			result.add(offsetZero);

			// ~addr!base == 0
			final Expression ptrBaseZero = mExpressionTranslation.constructLiteralForIntegerType(loc,
					mExpressionTranslation.getCTypeOfPointerComponents(), BigInteger.ZERO);
			final Expression isNullPtr =
					ExpressionFactory.newBinaryExpression(loc, Operator.COMPEQ, addrBase, ptrBaseZero);

			// requires ~addr!base == 0 || #valid[~addr!base];
			final Expression addrIsValid = mBooleanArrayHelper
					.compareWithTrue(ExpressionFactory.constructNestedArrayAccessExpression(loc, valid, idcFree));
			// final RequiresSpecification baseValid = new RequiresSpecification(loc, free,
			// ExpressionFactory.newBinaryExpression(loc, Operator.LOGICOR, isNullPtr, addrIsValid));
			final AssertStatement baseValid = new AssertStatement(loc,
					ExpressionFactory.newBinaryExpression(loc, Operator.LOGICOR, isNullPtr, addrIsValid));
			check.annotate(baseValid);
			result.add(baseValid);
		}

		return result;
	}

	/**
	 * Generate <code>procedure ULTIMATE.dealloc(~addr:$Pointer$) returns()</code>'s declaration and implementation.
	 * This procedure should be used for deallocations where do not want to check if given memory area is valid (because
	 * we already know this) which is the case, e.g., for arrays that we store on the heap or for alloca.
	 *
	 * @param tuLoc
	 *            the location for the new nodes.
	 * @return declaration and implementation of procedure <code>Ultimate_dealloc</code>
	 */
	private List<Declaration> declareDeallocation(final Dispatcher main, final ILocation tuLoc, final IASTNode hook) {
		final ArrayList<Declaration> decl = new ArrayList<>();
		// ensures #valid = old(valid)[~addr!base := false];
		final Expression bLFalse = mBooleanArrayHelper.constructFalse();
		final Expression addr = // new IdentifierExpression(tuLoc, ADDR);
				ExpressionFactory.constructIdentifierExpression(tuLoc, mBoogieTypeHelper.getBoogieTypeForPointerType(),
						ADDR, new DeclarationInformation(StorageClass.PROC_FUNC_INPARAM,
								MemoryModelDeclarations.Ultimate_Dealloc.getName()));
		final Expression valid = getValidArray(tuLoc);
		final Expression addrBase = ExpressionFactory.constructStructAccessExpression(tuLoc, addr, SFO.POINTER_BASE);
		final Expression[] idcFree = new Expression[] { addrBase };

		{
			final Procedure deallocDeclaration = new Procedure(tuLoc, new Attribute[0],
					MemoryModelDeclarations.Ultimate_Dealloc.getName(), new String[0],
					new VarList[] {
							new VarList(tuLoc, new String[] { ADDR }, mTypeHandler.constructPointerType(tuLoc)) },
					new VarList[0], new Specification[0], null);
			mProcedureManager.beginCustomProcedure(main, tuLoc, MemoryModelDeclarations.Ultimate_Dealloc.getName(),
					deallocDeclaration);
		}

		final ArrayList<Specification> specFree = new ArrayList<>();

		final ArrayStoreExpression arrayStore =
				// new ArrayStoreExpression(tuLoc,
				// ExpressionFactory.newUnaryExpression(tuLoc, UnaryExpression.Operator.OLD, valid), idcFree,
				// bLFalse);
				ExpressionFactory.constructArrayStoreExpression(tuLoc,
						ExpressionFactory.constructUnaryExpression(tuLoc, UnaryExpression.Operator.OLD, valid), idcFree,
						bLFalse);

		final Expression updateValidArray =
				ExpressionFactory.newBinaryExpression(tuLoc, Operator.COMPEQ, valid, arrayStore);

		specFree.add(mProcedureManager.constructEnsuresSpecification(tuLoc, true, updateValidArray,
				Collections.singleton((VariableLHS) CTranslationUtil.convertExpressionToLHS(valid))));
		// specFree.add(new ModifiesSpecification(tuLoc, false, new VariableLHS[] { getValidArrayLhs(tuLoc) }));

		// final Procedure deallocDeclaration = new Procedure(tuLoc, new Attribute[0],
		// MemoryModelDeclarations.Ultimate_Dealloc.getName(), new String[0],
		// new VarList[] { new VarList(tuLoc, new String[] { ADDR }, mTypeHandler.constructPointerType(tuLoc)) },
		// new VarList[0], specFree.toArray(new Specification[specFree.size()]), null);
		mProcedureManager.addSpecificationsToCurrentProcedure(specFree);
		mProcedureManager.endCustomProcedure(main, MemoryModelDeclarations.Ultimate_Dealloc.getName());

		// decl.add(deallocDeclaration);
		// mProcedureManager.registerProcedureDeclaration(MemoryModelDeclarations.Ultimate_Dealloc.getName(),
		// deallocDeclaration);

		return Collections.emptyList();
	}

	/**
	 * Generate <code>procedure ~Ultimate.alloc(~size:int) returns (#res:$Pointer$);</code>'s declaration and
	 * implementation.
	 *
	 * @param typeHandler
	 *
	 * @param tuLoc
	 *            the location for the new nodes.
	 * @return declaration and implementation of procedure <code>~malloc</code>
	 */
	private ArrayList<Declaration> declareMalloc(final Dispatcher main, final ITypeHandler typeHandler,
			final ILocation tuLoc, final IASTNode hook) {
		final ASTType intType = typeHandler.cType2AstType(tuLoc, mExpressionTranslation.getCTypeOfPointerComponents());
		final Expression nr0 = mExpressionTranslation.constructLiteralForIntegerType(tuLoc,
				mExpressionTranslation.getCTypeOfPointerComponents(), BigInteger.ZERO);
		final Expression valid = getValidArray(tuLoc);
		// procedure ~malloc(~size:int) returns (#res:$Pointer$);
		// requires ~size >= 0;
		// ensures old(#valid)[#res!base] = false;
		// ensures #valid = old(#valid)[#res!base := true];
		// ensures #res!offset == 0;
		// ensures #res!base != 0;
		// ensures #length = old(#length)[#res!base := ~size];
		// modifies #length, #valid;
		final Expression res = // new IdentifierExpression(tuLoc, SFO.RES);
				ExpressionFactory.constructIdentifierExpression(tuLoc, mBoogieTypeHelper.getBoogieTypeForPointerType(),
						SFO.RES, new DeclarationInformation(StorageClass.PROC_FUNC_OUTPARAM,
								MemoryModelDeclarations.Ultimate_Alloc.getName()));

		final Expression length = getLengthArray(tuLoc);
		// #res!base
		final Expression resBase = ExpressionFactory.constructStructAccessExpression(tuLoc, res, SFO.POINTER_BASE);
		// { #res!base }
		final Expression[] idcMalloc = new Expression[] { resBase };
		final Expression bLTrue = mBooleanArrayHelper.constructTrue();
		final Expression bLFalse = mBooleanArrayHelper.constructFalse();
		// ~size
		final IdentifierExpression size = // new IdentifierExpression(tuLoc, SIZE);
				ExpressionFactory.constructIdentifierExpression(tuLoc, BoogieType.TYPE_INT, SIZE,
						new DeclarationInformation(StorageClass.PROC_FUNC_INPARAM,
								MemoryModelDeclarations.Ultimate_Alloc.getName()));

		{
			final Procedure allocDeclaration = new Procedure(tuLoc, new Attribute[0],
					MemoryModelDeclarations.Ultimate_Alloc.getName(), new String[0],
					new VarList[] { new VarList(tuLoc, new String[] { SIZE }, intType) },
					new VarList[] {
							new VarList(tuLoc, new String[] { SFO.RES }, typeHandler.constructPointerType(tuLoc)) },
					new Specification[0], null);
			mProcedureManager.beginCustomProcedure(main, tuLoc, MemoryModelDeclarations.Ultimate_Alloc.getName(),
					allocDeclaration);
		}

		final List<Specification> specMalloc = new ArrayList<>();

		// old(#valid)[#res!base] == false
		specMalloc
				.add(mProcedureManager
						.constructEnsuresSpecification(tuLoc, false,
								ExpressionFactory.newBinaryExpression(tuLoc, Operator.COMPEQ,
										ExpressionFactory.constructNestedArrayAccessExpression(tuLoc,
												ExpressionFactory.constructUnaryExpression(tuLoc,
														UnaryExpression.Operator.OLD, valid),
												idcMalloc),
										bLFalse),
								Collections.emptySet()));
		// #valid[#res!base] == true
		specMalloc.add(mProcedureManager.constructEnsuresSpecification(tuLoc, false,
				ensuresArrayUpdate(tuLoc, bLTrue, resBase, valid),
				Collections.singleton((VariableLHS) CTranslationUtil.convertExpressionToLHS(valid))));
		// #res!offset == 0
		specMalloc.add(mProcedureManager.constructEnsuresSpecification(tuLoc, false,
				ExpressionFactory.newBinaryExpression(tuLoc, Operator.COMPEQ,
						ExpressionFactory.constructStructAccessExpression(tuLoc, res, SFO.POINTER_OFFSET), nr0),
				Collections.emptySet()));
		// #res!base != 0
		specMalloc.add(mProcedureManager.constructEnsuresSpecification(tuLoc, false,
				ExpressionFactory.newBinaryExpression(tuLoc, Operator.COMPNEQ,
						ExpressionFactory.constructStructAccessExpression(tuLoc, res, SFO.POINTER_BASE), nr0),
				Collections.emptySet()));
		// #length == old(#length)[#res!base := ~size]
		specMalloc
				.add(mProcedureManager.constructEnsuresSpecification(tuLoc, false,
						ExpressionFactory.newBinaryExpression(tuLoc, Operator.COMPEQ, length,
								ExpressionFactory.constructArrayStoreExpression(tuLoc,
										ExpressionFactory.constructUnaryExpression(tuLoc, UnaryExpression.Operator.OLD,
												length),
										idcMalloc, size)),
						Collections.singleton((VariableLHS) CTranslationUtil.convertExpressionToLHS(length))));
		// specMalloc.add(new ModifiesSpecification(tuLoc, false,
		// new VariableLHS[] { getValidArrayLhs(tuLoc), getLengthArrayLhs(tuLoc) }));
		// final Procedure allocDeclaration = new Procedure(tuLoc, new Attribute[0],
		// MemoryModelDeclarations.Ultimate_Alloc.getName(), new String[0],
		// new VarList[] { new VarList(tuLoc, new String[] { SIZE }, intType) },
		// new VarList[] { new VarList(tuLoc, new String[] { SFO.RES }, typeHandler.constructPointerType(tuLoc)) },
		// specMalloc.toArray(new Specification[specMalloc.size()]), null);
		mProcedureManager.addSpecificationsToCurrentProcedure(specMalloc);
		// decl.add(allocDeclaration);
		// mProcedureManager.registerProcedureDeclaration(MemoryModelDeclarations.Ultimate_Alloc.getName(),
		// allocDeclaration);

		final ArrayList<Declaration> result = new ArrayList<>();
		if (ADD_IMPLEMENTATIONS) {
			final Expression addr = // new IdentifierExpression(tuLoc, ADDR);
					ExpressionFactory.constructIdentifierExpression(tuLoc,
							mBoogieTypeHelper.getBoogieTypeForPointerType(), ADDR, new DeclarationInformation(
									StorageClass.LOCAL, MemoryModelDeclarations.Ultimate_Alloc.getName()));
			final Expression addrOffset =
					ExpressionFactory.constructStructAccessExpression(tuLoc, addr, SFO.POINTER_OFFSET);
			final Expression addrBase =
					ExpressionFactory.constructStructAccessExpression(tuLoc, addr, SFO.POINTER_BASE);
			// procedure ~malloc(~size:int) returns (#res:pointer) {
			// var ~addr : pointer;
			//
			// assume ~addr!offset = 0;
			// assume ~addr!base != 0;
			// assume !#valid[~addr!base];
			// // #valid setzen
			// #valid = #valid[~addr!base := true];
			// #length = #length[~addr!base := size];
			// // return pointer
			// #res := ~addr;
			// }
			final Expression[] idcAddrBase = new Expression[] { addrBase };
			final VariableDeclaration[] localVars =
					new VariableDeclaration[] { new VariableDeclaration(tuLoc, new Attribute[0], new VarList[] {
							new VarList(tuLoc, new String[] { ADDR }, typeHandler.constructPointerType(tuLoc)) }) };

			final VariableLHS resLhs =
					ExpressionFactory.constructVariableLHS(tuLoc, mBoogieTypeHelper.getBoogieTypeForPointerType(),
							SFO.RES, new DeclarationInformation(StorageClass.IMPLEMENTATION_OUTPARAM,
									MemoryModelDeclarations.Ultimate_Alloc.getName()));
			final Statement[] block = new Statement[6];
			block[0] = new AssumeStatement(tuLoc,
					ExpressionFactory.newBinaryExpression(tuLoc, Operator.COMPEQ, addrOffset, nr0));
			block[1] = new AssumeStatement(tuLoc,
					ExpressionFactory.newBinaryExpression(tuLoc, Operator.COMPNEQ, addrBase, nr0));
			block[2] = new AssumeStatement(tuLoc,
					ExpressionFactory.constructUnaryExpression(tuLoc, UnaryExpression.Operator.LOGICNEG,
							ExpressionFactory.constructNestedArrayAccessExpression(tuLoc, valid, idcAddrBase)));
			block[3] =
					StatementFactory.constructAssignmentStatement(tuLoc, new LeftHandSide[] { getValidArrayLhs(tuLoc) },
							new Expression[] { new ArrayStoreExpression(tuLoc, valid, idcAddrBase, bLTrue) });
			block[4] = StatementFactory.constructAssignmentStatement(tuLoc,
					new LeftHandSide[] { getLengthArrayLhs(tuLoc) },
					new Expression[] { new ArrayStoreExpression(tuLoc, length, idcAddrBase, size) });
			block[5] = StatementFactory.constructAssignmentStatement(tuLoc, new LeftHandSide[] { resLhs },
					new Expression[] { addr });
			// final Body bodyMalloc = new Body(tuLoc, localVars, block);
			final Body bodyMalloc = mProcedureManager.constructBody(tuLoc, localVars, block,
					MemoryModelDeclarations.Ultimate_Alloc.getName());
			result.add(new Procedure(tuLoc, new Attribute[0], MemoryModelDeclarations.Ultimate_Alloc.getName(),
					new String[0], new VarList[] { new VarList(tuLoc, new String[] { SIZE }, intType) },
					new VarList[] {
							new VarList(tuLoc, new String[] { SFO.RES }, typeHandler.constructPointerType(tuLoc)) },
					null, bodyMalloc));
		}
		mProcedureManager.endCustomProcedure(main, MemoryModelDeclarations.Ultimate_Alloc.getName());
		return result;
	}

	/**
	 * Returns a call to our internal Ultimate.dealloc procedure. Also notifies the relevant handlers (MemoryHandler,
	 * FunctionHandler) about the call.
	 *
	 * Note that Ultimate.dealloc does not make check if the deallocated memory is #valid, this must be done outside of
	 * this procedure if we are translating a call to C's <code>free(p)</code> function for example.
	 */
	public CallStatement getDeallocCall(final Dispatcher main, final LRValue lrVal, final ILocation loc) {
		assert lrVal instanceof RValue || lrVal instanceof LocalLValue;
		requireMemoryModelFeature(MemoryModelDeclarations.Ultimate_Dealloc);
		// assert lrVal.cType instanceof CPointer;//TODO -> must be a pointer or onHeap -- add a complicated assertion
		// or let it be??

		// Further checks are done in the precondition of ~free()!
		final CallStatement freeCall = StatementFactory.constructCallStatement(loc, false, new VariableLHS[0],
				MemoryModelDeclarations.Ultimate_Dealloc.getName(), new Expression[] { lrVal.getValue() });
		// add required information to function handler.
		if (mProcedureManager.isGlobalScope()) {
			// mProcedureManager.addModifiedGlobal(MemoryModelDeclarations.Ultimate_Dealloc.getName(),
			// getValidArrayLhs(loc));
			// mProcedureManager.registerCall(MemoryModelDeclarations.Ultimate_Dealloc.getName());
		}
		return freeCall;
	}

	/**
	 *
	 * @param callerName
	 *            name of the calling procedure
	 */
	public CallStatement getMallocCall(final LocalLValue resultPointer, final ILocation loc, final IASTNode hook) {
		return getMallocCall(calculateSizeOf(loc, resultPointer.getCType(), hook), (VariableLHS) resultPointer.getLHS(),
				loc);
	}

	/**
	 *
	 * @param size
	 * @param returnedValue
	 * @param loc
	 * @param surroundingProcedure
	 *            name of the procedure that the generated statements will be added to.
	 * @return
	 */
	public CallStatement getMallocCall(final Expression size, final VariableLHS returnedValue, final ILocation loc) {
		requireMemoryModelFeature(MemoryModelDeclarations.Ultimate_Alloc);
		final CallStatement result =
				StatementFactory.constructCallStatement(loc, false, new VariableLHS[] { returnedValue },
						MemoryModelDeclarations.Ultimate_Alloc.getName(), new Expression[] { size });

		mProcedureManager.registerProcedure(MemoryModelDeclarations.Ultimate_Alloc.getName());
		return result;
	}

	/**
	 * Generates a call of the read procedure and writes the returned value to a temp variable, returned in the
	 * expression of the returned ResultExpression. Note that we only read simple types from the heap -- when reading
	 * e.g. an array, we have to make readCalls for each cell.
	 *
	 * @param tPointer
	 *            the address to read from.
	 * @param pointerCType
	 *            the CType of the pointer in tPointer
	 *
	 * @return all declarations and statements required to perform the read, plus an identifierExpression holding the
	 *         read value.
	 */
	// 2015-10
	public ExpressionResult getReadCall(final Dispatcher main, final Expression address, final CType resultType,
			final IASTNode hook) {
		final ILocation loc = address.getLocation();
		// CPrimitive cPrimitive = (CPrimitive) resultType.getUnderlyingType();
		// if (main.getTypeSizes().getSize(cPrimitive.getType()) >
		// main.getTypeSizes().getSize(PRIMITIVE.INT)) {
		// throw new UnsupportedSyntaxException(loc,
		// "cannot read " + cPrimitive + " from heap");
		// }
		// }
		// boolean bitvectorConversionNeeded = (((CHandler) main.cHandler).getExpressionTranslation() instanceof
		// BitvectorTranslation
		// && (resultType.getUnderlyingType() instanceof CPrimitive)
		// && main.getTypeSizes().getSize(((CPrimitive) resultType.getUnderlyingType()).getType()) <
		// main.getTypeSizes().getSize(PRIMITIVE.INT));
		final boolean bitvectorConversionNeeded = false;

		ExpressionResultBuilder resultBuilder = new ExpressionResultBuilder();

		final String readCallProcedureName;
		{

			final CType ut;
			if (resultType instanceof CNamed) {
				ut = ((CNamed) resultType).getUnderlyingType();
			} else {
				ut = resultType;
			}

			if (ut instanceof CPrimitive) {
				final CPrimitive cp = (CPrimitive) ut;
				if (!SUPPORT_FLOATS_ON_HEAP && cp.isFloatingType()) {
					throw new UnsupportedSyntaxException(loc, FLOAT_ON_HEAP_UNSOUND_MESSAGE);
				}
				mRequiredMemoryModelFeatures.reportDataOnHeapRequired(cp.getType());
				readCallProcedureName = mMemoryModel.getReadProcedureName(cp.getType());
			} else if (ut instanceof CPointer) {
				mRequiredMemoryModelFeatures.reportPointerOnHeapRequired();
				readCallProcedureName = mMemoryModel.getReadPointerProcedureName();
			} else if (ut instanceof CNamed) {
				throw new AssertionError("we took underlying type");
			} else if (ut instanceof CArray) {
				// we assume it is an Array on Heap
				// assert main.cHandler.isHeapVar(((IdentifierExpression) lrVal.getValue()).getIdentifier());
				// but it may not only be on heap, because it is addressoffed, but also because it is inside
				// a struct that is addressoffed..
				mRequiredMemoryModelFeatures.reportPointerOnHeapRequired();
				readCallProcedureName = mMemoryModel.getReadPointerProcedureName();
			} else if (ut instanceof CEnum) {
				// enum is treated like an int
				mRequiredMemoryModelFeatures.reportDataOnHeapRequired(CPrimitives.INT);
				readCallProcedureName = mMemoryModel.getReadProcedureName(CPrimitives.INT);
			} else {
				throw new UnsupportedOperationException("unsupported type " + ut);
			}
		}

		// TODO: bitvectorConversionNeeded switches between two identical branches --> what was the real intention??
		final ASTType returnedValueAstType;
		if (bitvectorConversionNeeded) {
			returnedValueAstType = mTypeHandler.cType2AstType(loc, resultType);
		} else {
			returnedValueAstType = mTypeHandler.cType2AstType(loc, resultType);
		}
		final AuxVarInfo auxvar = AuxVarInfo.constructAuxVarInfo(loc, main, resultType, SFO.AUXVAR.MEMREAD);
		resultBuilder.addDeclaration(auxvar.getVarDec());
		resultBuilder.addAuxVar(auxvar);

		final VariableLHS[] lhss = new VariableLHS[] { auxvar.getLhs() };
		final CallStatement call = StatementFactory.constructCallStatement(loc, false, lhss, readCallProcedureName, // heapType.toString(),
				new Expression[] { address, calculateSizeOf(loc, resultType, hook) });
		for (final Overapprox overapprItem : resultBuilder.getOverappr()) {
			overapprItem.annotate(call);
		}
		resultBuilder.addStatement(call);
		assert CTranslationUtil.isAuxVarMapComplete(mNameHandler, resultBuilder);

		// ExpressionResult result;
		if (bitvectorConversionNeeded) {
			final IdentifierExpression returnedValueIdExpr = auxvar.getExp();

			resultBuilder.setLrVal(new RValue(returnedValueIdExpr, resultType));

			final ExpressionResult intermediateResult = resultBuilder.build();
			mExpressionTranslation.convertIntToInt(loc, intermediateResult,
					(CPrimitive) resultType.getUnderlyingType());
			resultBuilder = new ExpressionResultBuilder().addAllExceptLrValue(intermediateResult)
					.setLrVal(intermediateResult.getLrValue());

			final AuxVarInfo bvReturnedValueAux =
					AuxVarInfo.constructAuxVarInfo(loc, main, resultType, SFO.AUXVAR.MEMREAD);
			resultBuilder.addDeclaration(bvReturnedValueAux.getVarDec());
			resultBuilder.addAuxVar(bvReturnedValueAux);

			final VariableLHS[] bvlhss = new VariableLHS[] { bvReturnedValueAux.getLhs() };
			final AssignmentStatement as =
					// mProcedureManager.constructAssignmentStatement(loc, bvlhss, new Expression[] {
					// result.mLrVal.getValue() });
					StatementFactory.constructAssignmentStatement(loc, bvlhss,
							new Expression[] { resultBuilder.getLrVal().getValue() });
			// stmt.add(as);
			resultBuilder.addStatement(as);
			// TODO is it correct to use returnedValueAstType here?
			// result.mLrVal = new RValue(bvReturnedValueAux.getExp(), resultType);
			resultBuilder.resetLrVal(new RValue(bvReturnedValueAux.getExp(), resultType));
		} else {
			final IdentifierExpression returnedValueIdExpr = ExpressionFactory.constructIdentifierExpression(loc,
					mBoogieTypeHelper.getBoogieTypeForBoogieASTType(returnedValueAstType),
					auxvar.getExp().getIdentifier(),
					new DeclarationInformation(StorageClass.LOCAL, mProcedureManager.getCurrentProcedureID()));
			resultBuilder.setLrVal(new RValue(returnedValueIdExpr, resultType));
		}
		// return result;
		return resultBuilder.build();
	}

	/**
	 * Generates a procedure call to writeT(val, ptr), writing val to the according memory array. (for the C-methode the
	 * argument order is value, target, for this method it's the other way around)
	 *
	 * @param hlv
	 *            the HeapLvalue containing the address to write to
	 * @param rval
	 *            the value to write.
	 * @param isStaticInitialization
	 *            If the write call is used during static initialization of global variables, we can use the unchecked
	 *            methods and omit various specifications.
	 *
	 * @return the required Statements to perform the write.
	 */
	public List<Statement> getWriteCall(final Dispatcher main, final ILocation loc, final HeapLValue hlv,
			final Expression value, final CType valueType, final boolean isStaticInitialization, final IASTNode hook) {
		// if (((CHandler) main.cHandler).getExpressionTranslation() instanceof BitvectorTranslation
		// && (valueType.getUnderlyingType() instanceof CPrimitive)) {
		// CPrimitive cPrimitive = (CPrimitive) valueType.getUnderlyingType();
		// if (main.getTypeSizes().getSize(cPrimitive.getType()) >
		// main.getTypeSizes().getSize(PRIMITIVE.INT)) {
		// throw new UnsupportedSyntaxException(loc,
		// "cannot write " + cPrimitive + " to heap");
		// }
		// }
		// boolean bitvectorConversionNeeded = (((CHandler) main.cHandler).getExpressionTranslation() instanceof
		// BitvectorTranslation
		// && (valueType.getUnderlyingType() instanceof CPrimitive)
		// && main.getTypeSizes().getSize(((CPrimitive) valueType.getUnderlyingType()).getType()) <
		// main.getTypeSizes().getSize(PRIMITIVE.INT));
		// if (bitvectorConversionNeeded) {
		// RValue tmpworkaroundrvalue = new RValue(value, valueType.getUnderlyingType(), false, false);
		// ExpressionResult tmpworkaround = new ExpressionResult(tmpworkaroundrvalue);
		// mExpressionTranslation.convertIntToInt(loc, tmpworkaround, new CPrimitive(PRIMITIVE.INT));
		// value = tmpworkaround.lrVal.getValue();
		// valueType = tmpworkaround.lrVal.getCType();
		// }

		final CType realValueType;
		if (valueType instanceof CNamed) {
			realValueType = ((CNamed) valueType).getUnderlyingType();
		} else {
			realValueType = valueType;
		}

		if (realValueType instanceof CPrimitive) {
			return getWriteCallPrimitive(loc, hlv, value, (CPrimitive) realValueType, isStaticInitialization, hook);
		} else if (realValueType instanceof CEnum) {
			return getWriteCallEnum(loc, hlv, value, hook);
		} else if (realValueType instanceof CPointer) {
			return getWriteCallPointer(loc, hlv, value, hook);
		} else if (realValueType instanceof CStruct) {
			return getWriteCallStruct(main, loc, hlv, value, (CStruct) realValueType, isStaticInitialization, hook);
		} else if (realValueType instanceof CArray) {
			return getWriteCallArray(main, loc, hlv, value, (CArray) realValueType, isStaticInitialization, hook);
		} else {
			throw new UnsupportedSyntaxException(loc, "we don't recognize this type: " + realValueType);
		}
	}

	private List<Statement> getWriteCallArray(final Dispatcher main, final ILocation loc, final HeapLValue hlv,
			final Expression value, final CArray valueType, final boolean isStaticInitialization,
			final IASTNode hook) {

		if (valueType.getValueType().getUnderlyingType() instanceof CArray) {
			throw new UnsupportedSyntaxException(loc,
					"we need to generalize this to nested and/or variable length arrays");
		}

		final BigInteger dimBigInteger = mExpressionTranslation.extractIntegerValue(valueType.getBound(), hook);
		if (dimBigInteger == null) {
			throw new UnsupportedSyntaxException(loc, "variable length arrays not yet supported by this method");
		}

		final Expression arrayStartAddress = hlv.getAddress();
		final Expression newStartAddressBase;
		final Expression newStartAddressOffset;
		if (arrayStartAddress instanceof StructConstructor) {
			newStartAddressBase = ((StructConstructor) arrayStartAddress).getFieldValues()[0];
			newStartAddressOffset = ((StructConstructor) arrayStartAddress).getFieldValues()[1];
		} else {
			newStartAddressBase = MemoryHandler.getPointerBaseAddress(arrayStartAddress, loc);
			newStartAddressOffset = MemoryHandler.getPointerOffset(arrayStartAddress, loc);
		}

		final Expression valueTypeSize = calculateSizeOf(loc, valueType.getValueType(), hook);
		final int dim = dimBigInteger.intValue();
		final List<Statement> stmt = new ArrayList<>();

		Expression arrayEntryAddressOffset = newStartAddressOffset;

		// Expression readArrayEntryAddressOffset = arrayType.isOnHeap() ? getPointerOffset(rval.getValue(),
		// loc) : null;

		for (int pos = 0; pos < dim; pos++) {

			// if (arrayType.isOnHeap()) {
			// arrayAccRVal = new RValue(
			// constructPointerFromBaseAndOffset(
			// getPointerBaseAddress(rval.getValue(), loc),
			// readArrayEntryAddressOffset, loc),
			// arrayType.getValueType());
			// readArrayEntryAddressOffset = CHandler.createArithmeticExpression(IASTBinaryExpression.op_plus,
			// readArrayEntryAddressOffset, valueTypeSize, loc);
			// } else {
			final Expression position = mExpressionTranslation.constructLiteralForIntegerType(loc,
					mExpressionTranslation.getCTypeOfPointerComponents(), BigInteger.valueOf(pos));
			final RValue arrayAccessRVal = new RValue(
					ExpressionFactory.constructNestedArrayAccessExpression(loc, value, new Expression[] { position }),
					valueType.getValueType());
			// }
			final HeapLValue arrayCellLValue = LRValueFactory.constructHeapLValue(main,
					constructPointerFromBaseAndOffset(newStartAddressBase, arrayEntryAddressOffset, loc),
					valueType.getValueType(), null);
			stmt.addAll(getWriteCall(main, loc, arrayCellLValue, arrayAccessRVal.getValue(), arrayAccessRVal.getCType(),
					isStaticInitialization, hook));
			// TODO 2015-10-11 Matthias: Why is there an addition of value Type size
			// and no multiplication? Check this more carefully.
			arrayEntryAddressOffset =
					mExpressionTranslation.constructArithmeticExpression(loc, IASTBinaryExpression.op_plus,
							arrayEntryAddressOffset, mExpressionTranslation.getCTypeOfPointerComponents(),
							valueTypeSize, mExpressionTranslation.getCTypeOfPointerComponents());

		}
		return stmt;

		// stmt.add(new CallStatement(loc, false, new VariableLHS[0], "write~" + SFO.POINTER,
		// new Expression[] { rval.getValue(), hlv.getAddress(), this.calculateSizeOf(hlv.cType, loc) }));
	}

	private List<Statement> getWriteCallStruct(final Dispatcher main, final ILocation loc, final HeapLValue hlv,
			final Expression value, final CStruct valueType, final boolean isStaticInitialization,
			final IASTNode hook) {
		final List<Statement> stmt = new ArrayList<>();
		for (final String fieldId : valueType.getFieldIds()) {
			final Expression startAddress = hlv.getAddress();
			// final Expression startAddress = hlv.getAddressAsPointerRValue(mTypeHandler.getBoogiePointerType())
			// .getValue();
			final Expression newStartAddressBase = MemoryHandler.getPointerBaseAddress(startAddress, loc);
			final Expression newStartAddressOffset = MemoryHandler.getPointerOffset(startAddress, loc);
			// if (startAddress instanceof StructConstructor) {
			// newStartAddressBase = ((StructConstructor) startAddress).getFieldValues()[0];
			// newStartAddressOffset = ((StructConstructor) startAddress).getFieldValues()[1];
			// } else {
			// newStartAddressBase = MemoryHandler.getPointerBaseAddress(startAddress, loc);
			// newStartAddressOffset = MemoryHandler.getPointerOffset(startAddress, loc);
			// }

			final CType fieldType = valueType.getFieldType(fieldId);
			final StructAccessExpression sae = ExpressionFactory.constructStructAccessExpression(loc, value, fieldId);
			final Expression fieldOffset = mTypeSizeAndOffsetComputer.constructOffsetForField(loc, valueType, fieldId,
					hook);
			final Expression newOffset =
					mExpressionTranslation.constructArithmeticExpression(loc, IASTBinaryExpression.op_plus,
							newStartAddressOffset, mExpressionTranslation.getCTypeOfPointerComponents(), fieldOffset,
							mExpressionTranslation.getCTypeOfPointerComponents());
			final HeapLValue fieldHlv = LRValueFactory.constructHeapLValue(main,
					constructPointerFromBaseAndOffset(newStartAddressBase, newOffset, loc), fieldType, null);
			stmt.addAll(getWriteCall(main, loc, fieldHlv, sae, fieldType, isStaticInitialization, hook));
		}
		return stmt;
	}

	private List<Statement> getWriteCallPointer(final ILocation loc, final HeapLValue hlv, final Expression value,
			final IASTNode hook) {
		mRequiredMemoryModelFeatures.reportPointerOnHeapRequired();
		final String writeCallProcedureName = mMemoryModel.getWritePointerProcedureName();
		final HeapDataArray dhp = mMemoryModel.getPointerHeapArray();
		// mProcedureManager.addModifiedGlobal(mProcedureManager.getCurrentProcedureID(), dhp.getVariableLHS());
		return Collections.singletonList(
				StatementFactory.constructCallStatement(loc, false, new VariableLHS[0], writeCallProcedureName,
						new Expression[] { value, hlv.getAddress(), calculateSizeOf(loc, hlv.getCType(), hook) }));
	}

	private List<Statement> getWriteCallEnum(final ILocation loc, final HeapLValue hlv, final Expression value,
			final IASTNode hook) {
		// treat like INT
		mRequiredMemoryModelFeatures.reportDataOnHeapRequired(CPrimitives.INT);
		final String writeCallProcedureName = mMemoryModel.getWriteProcedureName(CPrimitives.INT);
		final HeapDataArray dhp = mMemoryModel.getDataHeapArray(CPrimitives.INT);
		// mProcedureManager.addModifiedGlobal(mProcedureManager.getCurrentProcedureID(), dhp.getVariableLHS());
		return Collections.singletonList(
				StatementFactory.constructCallStatement(loc, false, new VariableLHS[0], writeCallProcedureName,
						new Expression[] { value, hlv.getAddress(), calculateSizeOf(loc, hlv.getCType(), hook) }));
	}

	private List<Statement> getWriteCallPrimitive(final ILocation loc, final HeapLValue hlv, final Expression value,
			final CPrimitive valueType, final boolean isStaticInitialization, final IASTNode hook) {
		if (!SUPPORT_FLOATS_ON_HEAP && valueType.isFloatingType()) {
			throw new UnsupportedSyntaxException(loc, FLOAT_ON_HEAP_UNSOUND_MESSAGE);
		}
		mRequiredMemoryModelFeatures.reportDataOnHeapRequired(valueType.getType());
		final String writeCallProcedureName;
		if (isStaticInitialization) {
			mRequiredMemoryModelFeatures.reportUncheckedWriteRequired(valueType.getType());
			writeCallProcedureName = mMemoryModel.getUncheckedWriteProcedureName(valueType.getType());
		} else {
			writeCallProcedureName = mMemoryModel.getWriteProcedureName(valueType.getType());
		}

		final HeapDataArray dhp = mMemoryModel.getDataHeapArray(valueType.getType());
		// mProcedureManager.addModifiedGlobal(mProcedureManager.getCurrentProcedureID(), dhp.getVariableLHS());
		return Collections.singletonList(
				StatementFactory.constructCallStatement(loc, false, new VariableLHS[0], writeCallProcedureName,
						new Expression[] { value, hlv.getAddress(), calculateSizeOf(loc, hlv.getCType(), hook) }));
	}

	/**
	 * Takes a pointer Expression and returns the pointers base address. If it is already given as a struct, then the
	 * first field is returned, otherwise a StructAccessExpression pointer!base is returned.
	 *
	 * @param pointer
	 */
	public static Expression getPointerBaseAddress(final Expression pointer, final ILocation loc) {
		if (pointer instanceof StructConstructor) {
			return ((StructConstructor) pointer).getFieldValues()[0];
		}
		return ExpressionFactory.constructStructAccessExpression(loc, pointer, "base");
	}

	/**
	 * Takes a pointer Expression and returns the pointers base address. If it is already given as a struct, then the
	 * second field is returned, otherwise a StructAccessExpression pointer!offset is returned.
	 *
	 * @param pointer
	 */
	public static Expression getPointerOffset(final Expression pointer, final ILocation loc) {
		if (pointer instanceof StructConstructor) {
			return ((StructConstructor) pointer).getFieldValues()[1];
		}
		return ExpressionFactory.constructStructAccessExpression(loc, pointer, "offset");
	}

	public static StructConstructor constructPointerFromBaseAndOffset(final Expression base, final Expression offset,
			final ILocation loc) {
		return ExpressionFactory.constructStructConstructor(loc, new String[] { "base", "offset" },
				new Expression[] { base, offset });
	}

	/**
	 * Takes a loop or function body and inserts mallocs and frees for all the identifiers in this.mallocedAuxPointers
	 *
	 * Note that this returns a statement block that is like the given block but with added statement in front
	 * <b>and</b>in the back!
	 */
	public List<Statement> insertMallocs(final Dispatcher main, final List<Statement> block, final IASTNode hook) {
		final List<Statement> mallocs = new ArrayList<>();
		for (final LocalLValueILocationPair llvp : mVariablesToBeMalloced.currentScopeKeys()) {
			mallocs.add(this.getMallocCall(llvp.llv, llvp.loc, hook));
		}
		final List<Statement> frees = new ArrayList<>();
		for (final LocalLValueILocationPair llvp : mVariablesToBeFreed.currentScopeKeys()) { // frees are inserted in
			// handleReturnStm
			frees.add(getDeallocCall(main, llvp.llv, llvp.loc));
			frees.add(new HavocStatement(llvp.loc, new VariableLHS[] { (VariableLHS) llvp.llv.getLHS() }));
		}
		final List<Statement> newBlockAL = new ArrayList<>();
		newBlockAL.addAll(mallocs);
		newBlockAL.addAll(block);
		newBlockAL.addAll(frees);
		return newBlockAL;
	}

	public void addVariableToBeFreed(final Dispatcher main, final LocalLValueILocationPair llvp) {
		mVariablesToBeFreed.put(llvp, mVariablesToBeFreed.getActiveScopeNum());
	}

	public Map<LocalLValueILocationPair, Integer> getVariablesToBeMalloced() {
		return Collections.unmodifiableMap(mVariablesToBeMalloced);
	}

	public Map<LocalLValueILocationPair, Integer> getVariablesToBeFreed() {
		return Collections.unmodifiableMap(mVariablesToBeFreed);
	}

	public PointerCheckMode getPointerSubtractionAndComparisonValidityCheckMode() {
		return mCheckPointerSubtractionAndComparisonValidity;
	}

	public TypeSizeAndOffsetComputer getTypeSizeAndOffsetComputer() {
		return mTypeSizeAndOffsetComputer;
	}

	public IBooleanArrayHelper getBooleanArrayHelper() {
		return mBooleanArrayHelper;
	}

	/**
	 * Add or subtract a Pointer and an integer. Use this method only if you are sure that the type of the integer is
	 * the same as the type that we use for our pointer components. Otherwise, use the method below.
	 *
	 * @param operator
	 *            Either plus or minus.
	 * @param integer
	 * @param valueType
	 *            The value type the pointer points to (we need it because we have to multiply with its size)
	 *
	 * @return a pointer of the form: {base: ptr.base, offset: ptr.offset + integer * sizeof(valueType)}
	 */
	public Expression doPointerArithmetic(final int operator, final ILocation loc, final Expression ptrAddress,
			final RValue integer, final CType valueType, final IASTNode hook) {
		if (mTypeSizes.getSize(((CPrimitive) integer.getCType().getUnderlyingType()).getType()) != mTypeSizes
				.getSize(mExpressionTranslation.getCTypeOfPointerComponents().getType())) {
			throw new UnsupportedOperationException("not yet implemented, conversion is needed");
		}
		final Expression pointerBase = MemoryHandler.getPointerBaseAddress(ptrAddress, loc);
		final Expression pointerOffset = MemoryHandler.getPointerOffset(ptrAddress, loc);
		final Expression timesSizeOf = multiplyWithSizeOfAnotherType(loc, valueType, integer.getValue(),
				mExpressionTranslation.getCTypeOfPointerComponents(), hook);
		final Expression sum = mExpressionTranslation.constructArithmeticExpression(loc, operator, pointerOffset,
				mExpressionTranslation.getCTypeOfPointerComponents(), timesSizeOf,
				mExpressionTranslation.getCTypeOfPointerComponents());
		final StructConstructor newPointer = MemoryHandler.constructPointerFromBaseAndOffset(pointerBase, sum, loc);
		return newPointer;
	}

	/**
	 * Multiply an integerExpresion with the size of another type.
	 *
	 * @param integerExpresionType
	 *            {@link CType} whose translation is the Boogie type of integerExpression and the result.
	 * @return An {@link Expression} that represents <i>integerExpression * sizeof(valueType)</i>
	 */
	public Expression multiplyWithSizeOfAnotherType(final ILocation loc, final CType valueType,
			final Expression integerExpression, final CPrimitive integerExpresionType, final IASTNode hook) {
		final Expression timesSizeOf;
		timesSizeOf = mExpressionTranslation.constructArithmeticExpression(loc, IASTBinaryExpression.op_multiply,
				integerExpression, integerExpresionType, calculateSizeOf(loc, valueType, hook), integerExpresionType);
		return timesSizeOf;
	}

	MemoryModelDeclarationInfo getMemoryModelDeclarationInfo(final MemoryModelDeclarations mmd) {
		final MemoryModelDeclarationInfo result = mMemoryModelDeclarationInfos.get(mmd);
		if (result == null) {
			throw new AssertionError("call  requireMemoryModelFeature first!");
		}
		return result;
	}

	public interface IBooleanArrayHelper {
		ASTType constructBoolReplacementType();

		Expression constructTrue();

		Expression constructFalse();

		Expression compareWithTrue(Expression expr);
	}

	public static final class BooleanArrayHelper_Bool implements IBooleanArrayHelper {

		@Override
		public ASTType constructBoolReplacementType() {
			final ILocation ignoreLoc = LocationFactory.createIgnoreCLocation();
			return new PrimitiveType(ignoreLoc, BoogieType.TYPE_BOOL, "bool");
		}

		@Override
		public Expression constructTrue() {
			final ILocation ignoreLoc = LocationFactory.createIgnoreCLocation();
			return ExpressionFactory.createBooleanLiteral(ignoreLoc, true);
		}

		@Override
		public Expression constructFalse() {
			final ILocation ignoreLoc = LocationFactory.createIgnoreCLocation();
			return ExpressionFactory.createBooleanLiteral(ignoreLoc, false);
		}

		@Override
		public Expression compareWithTrue(final Expression expr) {
			return expr;
		}

	}

	public static final class BooleanArrayHelper_Integer implements IBooleanArrayHelper {

		@Override
		public ASTType constructBoolReplacementType() {
			final ILocation ignoreLoc = LocationFactory.createIgnoreCLocation();
			return new PrimitiveType(ignoreLoc, BoogieType.TYPE_INT, "int");
		}

		@Override
		public Expression constructTrue() {
			final ILocation ignoreLoc = LocationFactory.createIgnoreCLocation();
			return ExpressionFactory.createIntegerLiteral(ignoreLoc, "1");
		}

		@Override
		public Expression constructFalse() {
			final ILocation ignoreLoc = LocationFactory.createIgnoreCLocation();
			return ExpressionFactory.createIntegerLiteral(ignoreLoc, "0");
		}

		@Override
		public Expression compareWithTrue(final Expression expr) {
			final ILocation ignoreLoc = LocationFactory.createIgnoreCLocation();
			return ExpressionFactory.newBinaryExpression(ignoreLoc, Operator.COMPEQ, expr, constructTrue());
		}

	}

	public static final class BooleanArrayHelper_Bitvector implements IBooleanArrayHelper {

		@Override
		public ASTType constructBoolReplacementType() {
			final ILocation ignoreLoc = LocationFactory.createIgnoreCLocation();
			return new PrimitiveType(ignoreLoc, BoogieType.createBitvectorType(1), "bv1");
		}

		@Override
		public Expression constructTrue() {
			final ILocation ignoreLoc = LocationFactory.createIgnoreCLocation();
			return ExpressionFactory.createBitvecLiteral(ignoreLoc, "1", 1);
		}

		@Override
		public Expression constructFalse() {
			final ILocation ignoreLoc = LocationFactory.createIgnoreCLocation();
			return ExpressionFactory.createBitvecLiteral(ignoreLoc, "0", 1);
		}

		@Override
		public Expression compareWithTrue(final Expression expr) {
			final ILocation ignoreLoc = LocationFactory.createIgnoreCLocation();
			return ExpressionFactory.newBinaryExpression(ignoreLoc, Operator.COMPEQ, expr, constructTrue());
		}
	}

	public static final class RequiredMemoryModelFeatures {

		private final Set<CPrimitives> mDataOnHeapRequired;
		private final Set<CPrimitives> mUncheckedWriteRequired;
		private boolean mPointerOnHeapRequired;
		private final Set<MemoryModelDeclarations> mRequiredMemoryModelDeclarations;

		public RequiredMemoryModelFeatures() {
			mDataOnHeapRequired = new HashSet<>();
			mRequiredMemoryModelDeclarations = new HashSet<>();
			mUncheckedWriteRequired = new HashSet<>();
		}

		public void reportPointerOnHeapRequired() {
			mPointerOnHeapRequired = true;
		}

		public void reportUncheckedWriteRequired(final CPrimitives type) {
			assert mDataOnHeapRequired.contains(type);
			mUncheckedWriteRequired.add(type);
		}

		public void reportDataOnHeapRequired(final CPrimitives primitive) {
			mDataOnHeapRequired.add(primitive);
		}

		public boolean isPointerOnHeapRequired() {
			return mPointerOnHeapRequired;
		}

		public Set<CPrimitives> getDataOnHeapRequired() {
			return mDataOnHeapRequired;
		}

		public Set<CPrimitives> getUncheckedWriteRequired() {
			return mDataOnHeapRequired;
		}

		public boolean isMemoryModelInfrastructureRequired() {
			return isPointerOnHeapRequired() || !getDataOnHeapRequired().isEmpty()
					|| !getRequiredMemoryModelDeclarations().isEmpty();
		}

		public boolean require(final MemoryModelDeclarations mmdecl) {
			return mRequiredMemoryModelDeclarations.add(mmdecl);
		}

		public Set<MemoryModelDeclarations> getRequiredMemoryModelDeclarations() {
			return Collections.unmodifiableSet(mRequiredMemoryModelDeclarations);
		}
	}

	static class MemoryModelDeclarationInfo {

		private final MemoryModelDeclarations mMmd;
		private final BoogieType mBoogieType;

		public MemoryModelDeclarationInfo(final MemoryModelDeclarations mmd) {
			mMmd = mmd;
			mBoogieType = null;
		}

		public MemoryModelDeclarationInfo(final MemoryModelDeclarations mmd, final BoogieType boogieType) {
			mMmd = mmd;
			mBoogieType = boogieType;
		}

		IdentifierExpression constructIdentiferExpression(final ILocation loc) {
			return ExpressionFactory.constructIdentifierExpression(loc, mBoogieType, mMmd.getName(),
					DeclarationInformation.DECLARATIONINFO_GLOBAL);
		}

		VariableLHS constructVariableLHS(final ILocation loc) {
			return ExpressionFactory.constructVariableLHS(loc, mBoogieType, mMmd.getName(),
					DeclarationInformation.DECLARATIONINFO_GLOBAL);
		}

		BoogieType getBoogieType() {
			if (mBoogieType == null) {
				throw new IllegalStateException();
			}
			return mBoogieType;
		}

		static MemoryModelDeclarationInfo constructMemoryModelDeclarationInfo(final HandlerHandler handlerHandler,
				final MemoryModelDeclarations mmd) {

			switch (mmd) {
			case C_Memcpy:
				break;
			case C_Memmove:
				break;
			case C_Memset:
				break;
			case Ultimate_Alloc:
				break;
			case Ultimate_Dealloc:
				break;
			case Ultimate_Length:
				return new MemoryModelDeclarationInfo(mmd, BoogieType.createArrayType(0,
						new BoogieType[] { handlerHandler.getBoogieTypeHelper().getBoogieTypeForPointerComponents() },
						BoogieType.TYPE_INT));
			case Ultimate_MemInit:
				break;
			case Ultimate_Valid:
				return new MemoryModelDeclarationInfo(mmd, BoogieType.createArrayType(0,
						new BoogieType[] { handlerHandler.getBoogieTypeHelper().getBoogieTypeForPointerComponents() },
						handlerHandler.getBoogieTypeHelper().getBoogieTypeForBoogieASTType(handlerHandler
								.getMemoryHandler().getBooleanArrayHelper().constructBoolReplacementType())));
			default:
				break;
			}
			// construct empty mmdi
			return new MemoryModelDeclarationInfo(mmd);
		}
	}

	public static enum MemoryModelDeclarations {
		Ultimate_Alloc(SFO.ALLOC),

		Ultimate_Dealloc(SFO.DEALLOC),

		Ultimate_MemInit("#Ultimate.meminit"),

		C_Memcpy(SFO.C_MEMCPY),

		C_Memmove(SFO.C_MEMMOVE),

		C_Memset(SFO.C_MEMSET),

		Ultimate_Length(SFO.LENGTH),

		Ultimate_Valid(SFO.VALID);

		private final String mName;

		MemoryModelDeclarations(final String name) {
			mName = name;
		}

		public String getName() {
			return mName;
		}
	}

	public void beginScope() {
		mVariablesToBeMalloced.beginScope();
		mVariablesToBeFreed.beginScope();
	}

	public void endScope() {
		mVariablesToBeMalloced.endScope();
		mVariablesToBeFreed.endScope();
	}

	/**
	 * Construct the statements that write a string literal on the heap. (According to 6.4.5 of C11) The first statement
	 * is a call that allocates the memory The preceding statements write the (integer) values of the string literal to
	 * the appropriate heap array.
	 *
	 * call resultPointer := #Ultimate.alloc(value.length + 1); #memory_int[{ base: resultPointer!base, offset:
	 * resultPointer!offset + 0 }] := 78; #memory_int[{ base: resultPointer!base, offset: resultPointer!offset + 1 }] :=
	 * 101; #memory_int[{ base: resultPointer!base, offset: resultPointer!offset + 2 }] := 119; #memory_int[{ base:
	 * resultPointer!base, offset: resultPointer!offset + 3 }] := 0;
	 *
	 * 2017-01-06 Matthias: This works for our default memory model. I might not work for all our memory models.
	 *
	 * @param writeValues
	 *            if not set we omit to write values and just allocate memory
	 */
	public List<Statement> writeStringToHeap(final Dispatcher main, final ILocation loc,
			final VariableLHS resultPointer, final CStringLiteral stringLiteral, final boolean writeValues,
			final IASTNode hook) {
		final Expression size = mExpressionTranslation.constructLiteralForIntegerType(loc,
				mExpressionTranslation.getCTypeOfPointerComponents(),
				BigInteger.valueOf(stringLiteral.getByteValues().size()));
		final CallStatement ultimateAllocCall = getMallocCall(size, resultPointer, loc);
		final List<Statement> result = new ArrayList<>();
		result.add(ultimateAllocCall);
		if (writeValues) {
			for (int i = 0; i < stringLiteral.getByteValues().size(); i++) {
				final BigInteger valueBigInt = stringLiteral.getByteValues().get(i);
				final AssignmentStatement statement = writeCharToHeap(main, loc, resultPointer, i, valueBigInt, hook);
				result.add(statement);
			}
		}
		return result;
	}

	/**
	 *
	 * @param main
	 * @param loc
	 * @param resultPointer
	 * @param additionalOffset
	 * @param valueBigInt
	 * @param surroundingProcedure
	 *            the procedure where the AssignmentStatement that is created here will be added to
	 * @return
	 */
	private AssignmentStatement writeCharToHeap(final Dispatcher main, final ILocation loc,
			final VariableLHS resultPointer, final int additionalOffset, final BigInteger valueBigInt,
			final IASTNode hook) {
		mRequiredMemoryModelFeatures.reportDataOnHeapRequired(CPrimitives.CHAR);
		final HeapDataArray dhp = mMemoryModel.getDataHeapArray(CPrimitives.CHAR);
		// mProcedureManager.addModifiedGlobal(dhp.getVariableLHS());
		final Expression inputPointer = CTranslationUtil.convertLHSToExpression(resultPointer);
		final Expression additionalOffsetExpr = mExpressionTranslation.constructLiteralForIntegerType(loc,
				mExpressionTranslation.getCTypeOfPointerComponents(), BigInteger.valueOf(additionalOffset));
		final Expression pointer = doPointerArithmetic(IASTBinaryExpression.op_plus, loc, inputPointer,
				new RValue(additionalOffsetExpr, mExpressionTranslation.getCTypeOfPointerComponents()),
				new CPrimitive(CPrimitives.CHAR), hook);
		final Expression valueExpr = mExpressionTranslation.constructLiteralForIntegerType(loc,
				new CPrimitive(CPrimitives.CHAR), valueBigInt);
		final Expression possiblyExtendedValueExpr;
		if (dhp.getSize() != 0) {
			// if heap data array cannot store arbitrary sizes
			final Integer sizeOfChar = mTypeSizes.getSize(CPrimitives.CHAR);
			if (sizeOfChar > dhp.getSize()) {
				throw new AssertionError("char bigger than size of data array");
			}
			possiblyExtendedValueExpr =
					mExpressionTranslation.signExtend(loc, valueExpr, sizeOfChar * 8, dhp.getSize() * 8);
		} else {
			possiblyExtendedValueExpr = valueExpr;

		}

		final VariableLHS array = dhp.getVariableLHS();
		final AssignmentStatement statement =
				constructOneDimensionalArrayUpdate(main, loc, pointer, array, possiblyExtendedValueExpr);
		return statement;
	}

	public PointerCheckMode getPointerBaseValidityCheckMode() {
		return mPointerBaseValidity;
	}

	public PointerCheckMode getPointerTargetFullyAllocatedCheckMode() {
		return mPointerTargetFullyAllocated;
	}

	public void requireMemoryModelFeature(final MemoryModelDeclarations mmDecl) {
		mRequiredMemoryModelFeatures.require(mmDecl);

		MemoryModelDeclarationInfo mmdInfo = mMemoryModelDeclarationInfos.get(mmDecl);
		if (mmdInfo == null) {
			mmdInfo = MemoryModelDeclarationInfo.constructMemoryModelDeclarationInfo(mHandlerHandler, mmDecl);
			mMemoryModelDeclarationInfos.put(mmDecl, mmdInfo);
		}
	}

}
