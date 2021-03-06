/*
 * Copyright (C) 2013-2015 Alexander Nutz (nutz@informatik.uni-freiburg.de)
 * Copyright (C) 2015 Markus Lindenmann (lindenmm@informatik.uni-freiburg.de)
 * Copyright (C) 2013-2015 Matthias Heizmann (heizmann@informatik.uni-freiburg.de)
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
package de.uni_freiburg.informatik.ultimate.cdt.translation.implementation.base.chandler;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;

import org.eclipse.cdt.core.dom.ast.IASTNode;

import de.uni_freiburg.informatik.ultimate.boogie.DeclarationInformation;
import de.uni_freiburg.informatik.ultimate.boogie.DeclarationInformation.StorageClass;
import de.uni_freiburg.informatik.ultimate.boogie.ExpressionFactory;
import de.uni_freiburg.informatik.ultimate.boogie.StatementFactory;
import de.uni_freiburg.informatik.ultimate.boogie.ast.ASTType;
import de.uni_freiburg.informatik.ultimate.boogie.ast.AssignmentStatement;
import de.uni_freiburg.informatik.ultimate.boogie.ast.AssumeStatement;
import de.uni_freiburg.informatik.ultimate.boogie.ast.Attribute;
import de.uni_freiburg.informatik.ultimate.boogie.ast.BinaryExpression;
import de.uni_freiburg.informatik.ultimate.boogie.ast.Body;
import de.uni_freiburg.informatik.ultimate.boogie.ast.CallStatement;
import de.uni_freiburg.informatik.ultimate.boogie.ast.ConstDeclaration;
import de.uni_freiburg.informatik.ultimate.boogie.ast.Declaration;
import de.uni_freiburg.informatik.ultimate.boogie.ast.Expression;
import de.uni_freiburg.informatik.ultimate.boogie.ast.FunctionDeclaration;
import de.uni_freiburg.informatik.ultimate.boogie.ast.IdentifierExpression;
import de.uni_freiburg.informatik.ultimate.boogie.ast.IfStatement;
import de.uni_freiburg.informatik.ultimate.boogie.ast.LeftHandSide;
import de.uni_freiburg.informatik.ultimate.boogie.ast.NamedAttribute;
import de.uni_freiburg.informatik.ultimate.boogie.ast.NamedType;
import de.uni_freiburg.informatik.ultimate.boogie.ast.PrimitiveType;
import de.uni_freiburg.informatik.ultimate.boogie.ast.Procedure;
import de.uni_freiburg.informatik.ultimate.boogie.ast.RequiresSpecification;
import de.uni_freiburg.informatik.ultimate.boogie.ast.ReturnStatement;
import de.uni_freiburg.informatik.ultimate.boogie.ast.Specification;
import de.uni_freiburg.informatik.ultimate.boogie.ast.Statement;
import de.uni_freiburg.informatik.ultimate.boogie.ast.TypeDeclaration;
import de.uni_freiburg.informatik.ultimate.boogie.ast.VarList;
import de.uni_freiburg.informatik.ultimate.boogie.ast.VariableDeclaration;
import de.uni_freiburg.informatik.ultimate.boogie.ast.VariableLHS;
import de.uni_freiburg.informatik.ultimate.boogie.type.BoogieType;
import de.uni_freiburg.informatik.ultimate.cdt.translation.implementation.LocationFactory;
import de.uni_freiburg.informatik.ultimate.cdt.translation.implementation.base.CTranslationUtil;
import de.uni_freiburg.informatik.ultimate.cdt.translation.implementation.base.FunctionDeclarations;
import de.uni_freiburg.informatik.ultimate.cdt.translation.implementation.base.TypeHandler;
import de.uni_freiburg.informatik.ultimate.cdt.translation.implementation.base.chandler.MemoryHandler.MemoryModelDeclarations;
import de.uni_freiburg.informatik.ultimate.cdt.translation.implementation.base.expressiontranslation.BitvectorTranslation;
import de.uni_freiburg.informatik.ultimate.cdt.translation.implementation.base.expressiontranslation.ExpressionTranslation;
import de.uni_freiburg.informatik.ultimate.cdt.translation.implementation.container.AuxVarInfo;
import de.uni_freiburg.informatik.ultimate.cdt.translation.implementation.container.c.CFunction;
import de.uni_freiburg.informatik.ultimate.cdt.translation.implementation.container.c.CPrimitive;
import de.uni_freiburg.informatik.ultimate.cdt.translation.implementation.container.c.CPrimitive.CPrimitiveCategory;
import de.uni_freiburg.informatik.ultimate.cdt.translation.implementation.container.c.CType;
import de.uni_freiburg.informatik.ultimate.cdt.translation.implementation.exception.UnsupportedSyntaxException;
import de.uni_freiburg.informatik.ultimate.cdt.translation.implementation.result.CDeclaration;
import de.uni_freiburg.informatik.ultimate.cdt.translation.implementation.result.ExpressionResult;
import de.uni_freiburg.informatik.ultimate.cdt.translation.implementation.result.ExpressionResultBuilder;
import de.uni_freiburg.informatik.ultimate.cdt.translation.implementation.result.InitializerResult;
import de.uni_freiburg.informatik.ultimate.cdt.translation.implementation.result.LocalLValue;
import de.uni_freiburg.informatik.ultimate.cdt.translation.implementation.util.SFO;
import de.uni_freiburg.informatik.ultimate.cdt.translation.interfaces.Dispatcher;
import de.uni_freiburg.informatik.ultimate.core.model.models.ILocation;
import de.uni_freiburg.informatik.ultimate.core.model.services.ILogger;

/**
 * Class caring for some post processing steps, like creating an initializer procedure and the start procedure.
 *
 * @author Markus Lindenmann
 * @date 12.10.2012
 */
public class PostProcessor {
//	/**
//	 * Holds the Boogie identifiers of the initialized global variables. Used for filling the modifies clause of
//	 * Ultimate.start and Ultimate.init.
//	 */
//	private final LinkedHashSet<String> mInitializedGlobals;

	private final Dispatcher mDispatcher;
	private final ILogger mLogger;

	private final ExpressionTranslation mExpressionTranslation;
	private final boolean mOverapproximateFloatingPointOperations;

	/*
	 * Decides if the PostProcessor declares the special function that we use for converting Boogie-Real to a
	 * Boogie-Int. This is needed when we do a cast from float to int in C.
	 */
	public boolean mDeclareToIntFunction = false;

	/**
	 * Constructor.
	 *
	 * @param overapproximateFloatingPointOperations
	 */
	public PostProcessor(final Dispatcher dispatcher, final ILogger logger,
			final ExpressionTranslation expressionTranslation, final boolean overapproximateFloatingPointOperations) {
//		mInitializedGlobals = new LinkedHashSet<>();
		mDispatcher = dispatcher;
		mLogger = logger;
		mExpressionTranslation = expressionTranslation;
		mOverapproximateFloatingPointOperations = overapproximateFloatingPointOperations;
	}

	/**
	 * Start method for the post processing.
	 *
	 * @param main
	 *            a reference to the main dispatcher.
	 * @param loc
	 *            the location of the translation unit.
	 * @param memoryHandler
	 * @param arrayHandler
	 *            a reference to the arrayHandler.
	 * @param structHandler
	 * @param typeHandler
	 * @param initStatements
	 *            a list of all global init statements.
	 * @param procedures
	 *            a list of all procedures in the TU.
	 * @param modifiedGlobals
	 *            modified globals for all procedures.
	 * @param undefinedTypes
	 *            a list of used, but not declared types.
	 * @param functions
	 *            a list of functions to add to the TU.
	 * @param mDeclarationsGlobalInBoogie
	 * @param expressionTranslation
	 * @param uninitGlobalVars
	 *            a set of uninitialized global variables.
	 * @return a declaration list holding the init() and start() procedure.
	 */
	public ArrayList<Declaration> postProcess(final Dispatcher main, final ILocation loc, final IASTNode hook) {
//			final LinkedHashMap<Declaration, CDeclaration> mDeclarationsGlobalInBoogie) {
		final ArrayList<Declaration> decl = new ArrayList<>();


		final Set<String> undefinedTypes = main.mTypeHandler.getUndefinedTypes();
		decl.addAll(declareUndefinedTypes(loc, undefinedTypes));

		final String checkedMethod = main.getCheckedMethod();

		if (!checkedMethod.equals(SFO.EMPTY)
				&& main.mCHandler.getProcedureManager().hasProcedure(checkedMethod)) {
				mLogger.info("Settings: Checked method=" + checkedMethod);
				final UltimateInitProcedure initProcedure = new UltimateInitProcedure(loc, main, hook);//, mDeclarationsGlobalInBoogie);
				decl.add(initProcedure.getUltimateInitImplementation());

				final UltimateStartProcedure startProcedure = new UltimateStartProcedure(main, loc, hook);
				decl.add(startProcedure.getUltimateStartImplementation());
				//		decl.addAll(createUltimateStartProcedure(main, loc, functionHandler,
//				initProcedure.getUltimateInitModifiesClauseContents()));


		} else {
			// this would be done during createInit otherwise
			main.mCHandler.getStaticObjectsHandler().freeze();

			mLogger.info("Settings: Library mode!");
			if (main.mCHandler.getProcedureManager().hasProcedure(SFO.MAIN)) {
				final String msg =
						"You selected the library mode (i.e., each procedure can be starting procedure and global "
								+ "variables are not initialized). This program contains a \"main\" procedure. Maybe you "
								+ "wanted to select the \"main\" procedure as starting procedure.";
				mDispatcher.warn(loc, msg);
			}
		}


		decl.addAll(declareFunctionPointerProcedures(main));
		decl.addAll(declareConversionFunctions(main));

		final TypeHandler typeHandler = (TypeHandler) main.mTypeHandler;

		if ((typeHandler).isBitvectorTranslation()) {
			final ExpressionTranslation expressionTranslation = main.mCHandler.getExpressionTranslation();

			decl.addAll(PostProcessor.declarePrimitiveDataTypeSynonyms(loc, main.getTypeSizes(), typeHandler));

			if ((typeHandler).areFloatingTypesNeeded()) {
				decl.addAll(PostProcessor.declareFloatDataTypes(loc, main.getTypeSizes(), typeHandler,
						mOverapproximateFloatingPointOperations, expressionTranslation));
			}

			final String[] importantFunctions = new String[] { "bvadd" };
			final BitvectorTranslation bitvectorTranslation = (BitvectorTranslation) expressionTranslation;
			bitvectorTranslation.declareBinaryBitvectorFunctionsForAllIntegerDatatypes(loc, importantFunctions);
		}
		assert decl.stream().allMatch(Objects::nonNull);
		return decl;
	}

	private ArrayList<Declaration> declareConversionFunctions(final Dispatcher main) {
//			final FunctionHandler functionHandler, final MemoryHandler memoryHandler,
//			final StructHandler structHandler) {

		final ILocation ignoreLoc = LocationFactory.createIgnoreCLocation();

		final ArrayList<Declaration> decls = new ArrayList<>();

		// function to_int
		final String inReal = "inReal";
		// IdentifierExpression inRealIdex = new IdentifierExpression(ignoreLoc, inReal);
		final String outInt = "outInt";
		// IdentifierExpression outIntIdex = new IdentifierExpression(ignoreLoc, outInt);
		final VarList realParam = new VarList(ignoreLoc, new String[] {},
				new PrimitiveType(ignoreLoc, BoogieType.TYPE_REAL, SFO.REAL));
		final VarList[] oneRealParam = new VarList[] { realParam };
		final VarList intParam = new VarList(ignoreLoc, new String[] { outInt },
				new PrimitiveType(ignoreLoc, BoogieType.TYPE_INT, SFO.INT));
		// VarList[] oneIntParam = new VarList[] { intParam };
		// Expression inRealGeq0 = new BinaryExpression(ignoreLoc,
		// BinaryExpression.Operator.COMPGEQ, inRealIdex, new IntegerLiteral(ignoreLoc, SFO.NR0));
		//
		// Expression roundDown = new BinaryExpression(ignoreLoc, BinaryExpression.Operator.LOGICAND,
		// new BinaryExpression(ignoreLoc, BinaryExpression.Operator.COMPLEQ,
		// new BinaryExpression(ignoreLoc, BinaryExpression.Operator.ARITHMINUS, inRealIdex, new
		// IntegerLiteral(ignoreLoc, SFO.NR1)),
		// outIntIdex),
		// new BinaryExpression(ignoreLoc, BinaryExpression.Operator.COMPLEQ,
		// outIntIdex,
		// inRealIdex));
		// Expression roundUp = new BinaryExpression(ignoreLoc, BinaryExpression.Operator.LOGICAND,
		// new BinaryExpression(ignoreLoc, BinaryExpression.Operator.COMPLEQ,
		// inRealIdex,
		// outIntIdex),
		// new BinaryExpression(ignoreLoc, BinaryExpression.Operator.COMPLEQ,
		// new BinaryExpression(ignoreLoc, BinaryExpression.Operator.ARITHPLUS, inRealIdex, new
		// IntegerLiteral(ignoreLoc, SFO.NR1)),
		// outIntIdex));
		//
		// Specification toIntSpec = new EnsuresSpecification(ignoreLoc, false, new IfThenElseExpression(ignoreLoc,
		// inRealGeq0, roundDown, roundUp));
		// decls.add(new Procedure(ignoreLoc, new Attribute[0], SFO.TO_INT, new String[0], oneRealParam, oneIntParam,
		// new Specification[] { toIntSpec }, null));

		if (mDeclareToIntFunction) {
			decls.add(new FunctionDeclaration(ignoreLoc, new Attribute[0], SFO.TO_INT, new String[0], oneRealParam,
					intParam));
		}

		return decls;
	}

	private ArrayList<Declaration> declareFunctionPointerProcedures(final Dispatcher main) {
		final FunctionHandler functionHandler = main.mCHandler.getFunctionHandler();
		final MemoryHandler memoryHandler = main.mCHandler.getMemoryHandler();
		final ProcedureManager procedureManager = main.mCHandler.getProcedureManager();


		final ILocation ignoreLoc = LocationFactory.createIgnoreCLocation();
		final ArrayList<Declaration> result = new ArrayList<>();
		for (final ProcedureSignature cFunc : functionHandler.getFunctionsSignaturesWithFunctionPointers()) {
			final String procName = cFunc.toString();

			final VarList[] inParams = procedureManager.getProcedureDeclaration(procName).getInParams();
			final VarList[] outParams = procedureManager.getProcedureDeclaration(procName).getOutParams();
			assert outParams.length <= 1;
			final Procedure functionPointerMuxProc =
					new Procedure(ignoreLoc, new Attribute[0], procName, new String[0], inParams, outParams,
							// FIXME: it seems an odd convention that giving it "null" as Specification makes it an
							// implementation
							// (instead of a procedure) in Boogie
							// new Specification[0],
							null,
							// functionHandler.getFunctionPointerFunctionBody(ignoreLoc, main, memoryHandler,
							// structHandler, procName, cFunc, inParams, outParams));
							getFunctionPointerFunctionBody(ignoreLoc, main, functionHandler, procedureManager,
									memoryHandler, procName, cFunc, inParams, outParams));
			result.add(functionPointerMuxProc);
		}
		return result;
	}

	/**
	 * Declares a type for each identifier in the set.
	 *
	 * @param loc
	 *            the location to be used for the declarations.
	 * @param undefinedTypes
	 *            a list of undefined, but used types.
	 * @return a list of type declarations.
	 */
	private static Collection<? extends Declaration> declareUndefinedTypes(final ILocation loc,
			final Set<String> undefinedTypes) {
		final ArrayList<Declaration> decl = new ArrayList<>();
		for (final String s : undefinedTypes) {
			decl.add(new TypeDeclaration(loc, new Attribute[0], false, s, new String[0]));
		}
		return decl;
	}

//	/**
//	 *
//	 *
//	 * @param initStatements
//	 * @return
//	 */
//	private boolean assertInitializedGlobalsTracksAllInitializedVariables(final Collection<Statement> initStatements) {
//		for (final Statement stmt : initStatements) {
//			if (stmt instanceof AssignmentStatement) {
//				final AssignmentStatement ass = (AssignmentStatement) stmt;
//				assert ass.getLhs().length == 1; // by construction ...
//				final LeftHandSide lhs = ass.getLhs()[0];
//				final String id = BoogieASTUtil.getLHSId(lhs);
//				if (!mInitializedGlobals.contains(id)) {
//					return false;
//				}
//			}
//		}
//		return true;
//	}

	/**
	 * Generate type declarations like, e.g., the following. type { :isUnsigned true } { :bitsize 16 } C_USHORT = bv16;
	 * This allow us to use type synonyms like C_USHORT during the translation. This is yet not consequently
	 * implemented. This is desired not only for bitvectors: it makes our translation more modular and can ease
	 * debugging. However, that this located in this class and fixed to bitvectors is a workaround.
	 *
	 * @param typeHandler
	 */
	public static ArrayList<Declaration> declarePrimitiveDataTypeSynonyms(final ILocation loc,
			final TypeSizes typeSizes, final TypeHandler typeHandler) {
		final ArrayList<Declaration> decls = new ArrayList<>();
		for (final CPrimitive.CPrimitives cPrimitive : CPrimitive.CPrimitives.values()) {
			final CPrimitive cPrimitiveO = new CPrimitive(cPrimitive);
			if (cPrimitiveO.getGeneralType() == CPrimitiveCategory.INTTYPE) {
				final Attribute[] attributes = new Attribute[2];
				attributes[0] = new NamedAttribute(loc, "isUnsigned",
						new Expression[] {
								ExpressionFactory.createBooleanLiteral(loc, typeSizes.isUnsigned(cPrimitiveO)) });
				final int bytesize = typeSizes.getSize(cPrimitive);
				final int bitsize = bytesize * 8;
				attributes[1] = new NamedAttribute(loc, "bitsize",
						new Expression[] { ExpressionFactory.createIntegerLiteral(loc, String.valueOf(bitsize)) });
				final String identifier = "C_" + cPrimitive.name();
				final String[] typeParams = new String[0];
				final String name = "bv" + bitsize;
				final ASTType astType = typeHandler.bytesize2asttype(loc, CPrimitiveCategory.INTTYPE, bytesize);
				decls.add(new TypeDeclaration(loc, attributes, false, identifier, typeParams, astType));
			}
		}
		return decls;
	}

	/**
	 * Generate FloatingPoint types
	 *
	 * @param loc
	 * @param typesizes
	 * @param typeHandler
	 * @param expressionTranslation
	 * @return
	 */
	public static ArrayList<Declaration> declareFloatDataTypes(final ILocation loc, final TypeSizes typesizes,
			final TypeHandler typeHandler, final boolean overapproximateFloat,
			final ExpressionTranslation expressionTranslation) {
		final ArrayList<Declaration> decls = new ArrayList<>();

		// Roundingmodes, for now RNE hardcoded
		final Attribute[] attributesRM;
		if (overapproximateFloat) {
			attributesRM = new Attribute[0];
		} else {
			final String smtlibRmIdentifier = "RoundingMode";
			attributesRM = new Attribute[1];
			attributesRM[0] = new NamedAttribute(loc, FunctionDeclarations.BUILTIN_IDENTIFIER,
					new Expression[] { ExpressionFactory.createStringLiteral(loc, smtlibRmIdentifier) });
		}
		final String[] typeParamsRM = new String[0];
		decls.add(new TypeDeclaration(loc, attributesRM, false, BitvectorTranslation.BOOGIE_ROUNDING_MODE_IDENTIFIER,
				typeParamsRM));

		final Attribute[] attributesRNE;
		final Attribute[] attributesRTZ;
		if (overapproximateFloat) {
			attributesRNE = new Attribute[0];
			attributesRTZ = new Attribute[0];
		} else {
			final Attribute attributeRNE = new NamedAttribute(loc, FunctionDeclarations.BUILTIN_IDENTIFIER,
					new Expression[] { ExpressionFactory.createStringLiteral(loc, "RNE") });
			final Attribute attributeRTZ = new NamedAttribute(loc, FunctionDeclarations.BUILTIN_IDENTIFIER,
					new Expression[] { ExpressionFactory.createStringLiteral(loc, "RTZ") });
			attributesRNE = new Attribute[] { attributeRNE };
			attributesRTZ = new Attribute[] { attributeRTZ };
		}
		decls.add(new ConstDeclaration(loc, attributesRNE, false,
				new VarList(loc, new String[] { BitvectorTranslation.BOOGIE_ROUNDING_MODE_RNE },
						new NamedType(loc, BitvectorTranslation.TYPE_OF_BOOGIE_ROUNDING_MODES,
								BitvectorTranslation.BOOGIE_ROUNDING_MODE_IDENTIFIER, new ASTType[0])),
				null, false));
		decls.add(new ConstDeclaration(loc, attributesRTZ, false,
				new VarList(loc, new String[] { BitvectorTranslation.BOOGIE_ROUNDING_MODE_RTZ },
						new NamedType(loc, BitvectorTranslation.TYPE_OF_BOOGIE_ROUNDING_MODES,
								BitvectorTranslation.BOOGIE_ROUNDING_MODE_IDENTIFIER, new ASTType[0])),
				null, false));

		for (final CPrimitive.CPrimitives cPrimitive : CPrimitive.CPrimitives.values()) {

			final CPrimitive cPrimitive0 = new CPrimitive(cPrimitive);

			if (cPrimitive0.getGeneralType() == CPrimitiveCategory.FLOATTYPE && !cPrimitive0.isComplexType()) {

				if (!overapproximateFloat) {
					final BitvectorTranslation bt = ((BitvectorTranslation) expressionTranslation);
					// declare floating point constructors here because we might
					// always need them for our backtranslation
					bt.declareFloatingPointConstructors(loc, new CPrimitive(cPrimitive));
					bt.declareFloatConstant(loc, BitvectorTranslation.SMT_LIB_MINUS_INF, new CPrimitive(cPrimitive));
					bt.declareFloatConstant(loc, BitvectorTranslation.SMT_LIB_PLUS_INF, new CPrimitive(cPrimitive));
					bt.declareFloatConstant(loc, BitvectorTranslation.SMT_LIB_NAN, new CPrimitive(cPrimitive));
					bt.declareFloatConstant(loc, BitvectorTranslation.SMT_LIB_MINUS_ZERO, new CPrimitive(cPrimitive));
					bt.declareFloatConstant(loc, BitvectorTranslation.SMT_LIB_PLUS_ZERO, new CPrimitive(cPrimitive));
				}

				final Attribute[] attributes;
				if (overapproximateFloat) {
					attributes = new Attribute[0];
				} else {
					attributes = new Attribute[2];
					attributes[0] = new NamedAttribute(loc, FunctionDeclarations.BUILTIN_IDENTIFIER,
							new Expression[] { ExpressionFactory.createStringLiteral(loc, "FloatingPoint") });
					final int bytesize = typesizes.getSize(cPrimitive);
					final int[] indices = new int[2];
					switch (bytesize) {
					case 4:
						indices[0] = 8;
						indices[1] = 24;
						break;
					case 8:
						indices[0] = 11;
						indices[1] = 53;
						break;
					case 12: // because of 80bit long doubles on linux x86
					case 16:
						indices[0] = 15;
						indices[1] = 113;
						break;
					default:
						throw new UnsupportedSyntaxException(loc, "unknown primitive type");
					}
					attributes[1] = new NamedAttribute(loc, FunctionDeclarations.INDEX_IDENTIFIER,
							new Expression[] { ExpressionFactory.createIntegerLiteral(loc, String.valueOf(indices[0])),
									ExpressionFactory.createIntegerLiteral(loc, String.valueOf(indices[1])) });
				}
				final String identifier = "C_" + cPrimitive.name();
				final String[] typeParams = new String[0];
				decls.add(new TypeDeclaration(loc, attributes, false, identifier, typeParams));
			}
		}
		return decls;
	}

	/**
	 * Generate the body for one of our internal function pointer dispatching procedures.
	 * See also {@link FunctionHandler.handleFunctionPointerCall} on how we treat function pointers.
	 *
	 *
	 * @param loc
	 * @param main
	 * @param procedureManager
	 * @param memoryHandler
	 * @param structHandler
	 * @param dispatchingProcedureName
	 * 			name of the dispatching procedure
	 * @param funcSignature
	 * 			signature of the dispatching procedure
	 * @param inParams
	 * 			in parameters of the dispatching procedure as it has been registered in FunctionHandler
	 * @param outParam
	 * 			out parameters of the dispatching procedure as it has been registered in FunctionHandler
	 * @return
	 */
	public Body getFunctionPointerFunctionBody(final ILocation loc, final Dispatcher main,
			final FunctionHandler functionHandler, final ProcedureManager procedureManager,
			final MemoryHandler memoryHandler, final String dispatchingProcedureName,
			final ProcedureSignature funcSignature, final VarList[] inParams,
			final VarList[] outParam) {

		final BoogieTypeHelper boogieTypeHelper = main.mCHandler.getBoogieTypeHelper();

		final boolean resultTypeIsVoid = (funcSignature.getReturnType() == null);

		final ExpressionResultBuilder builder = new ExpressionResultBuilder();

		/*
		 * Setup the input parameters for the dispatched procedures.
		 * The last inParam of the dispatching procedure is the function pointer in this case, which is not given to
		 *  the dispatched procedures.
		 *  --> therefore we iterate to inParams.lenth - 1 only..
		 */
		final ArrayList<Expression> args = new ArrayList<>();
		for (int i = 0; i < inParams.length - 1; i++) {
			final VarList vl = inParams[i];
			assert vl.getIdentifiers().length == 1;
			final String oldId = vl.getIdentifiers()[0];
			final String newId = oldId.replaceFirst("in", "");
			builder.addDeclaration(new VariableDeclaration(loc, new Attribute[0],
					new VarList[] { new VarList(loc, new String[] { newId }, vl.getType()) }));
			final IdentifierExpression oldIdExpr = ExpressionFactory.constructIdentifierExpression(loc,
							boogieTypeHelper.getBoogieTypeForBoogieASTType(vl.getType()), oldId,
							new DeclarationInformation(StorageClass.IMPLEMENTATION_INPARAM, dispatchingProcedureName));
			final VariableLHS newIdLhs = ExpressionFactory.constructVariableLHS(loc,
							boogieTypeHelper.getBoogieTypeForBoogieASTType(vl.getType()),
							newId, new DeclarationInformation(StorageClass.LOCAL, dispatchingProcedureName));
			builder.addStatement(StatementFactory.constructAssignmentStatement(loc, new LeftHandSide[] { newIdLhs },
					new Expression[] { oldIdExpr }));
			final IdentifierExpression newIdIdExpr = ExpressionFactory.constructIdentifierExpression(loc,
							boogieTypeHelper.getBoogieTypeForBoogieASTType(vl.getType()),
							newId, new DeclarationInformation(StorageClass.LOCAL, dispatchingProcedureName));
			args.add(newIdIdExpr);
		}

		// collect all functions that are addressoffed in the program and that match the signature
		final ArrayList<String> fittingFunctions = new ArrayList<>();
		for (final Entry<String, Integer> en : main.getFunctionToIndex().entrySet()) {
			final CFunction ptdToFuncType = procedureManager.getCFunctionType(en.getKey());
			if (new ProcedureSignature(main, ptdToFuncType).equals(funcSignature)) {
				fittingFunctions.add(en.getKey());
			}
		}

		// generate the actual body
		IdentifierExpression funcCallResult = null;
		if (fittingFunctions.isEmpty()) {
			return procedureManager.constructBody(loc,
					builder.getDeclarations().toArray(new VariableDeclaration[builder.getDeclarations().size()]),
					builder.getStatements().toArray(new Statement[builder.getStatements().size()]),
					dispatchingProcedureName);
		} else if (fittingFunctions.size() == 1) {
			final ExpressionResult rex = (ExpressionResult) functionHandler.makeTheFunctionCallItself(main, loc,
					fittingFunctions.get(0), new ExpressionResultBuilder(), args);

			final boolean voidReturnType = outParam.length == 0;

			if (!voidReturnType) {
				funcCallResult = (IdentifierExpression) rex.getLrValue().getValue();
			}
			builder.addAllExceptLrValue(rex);

			assert outParam.length <= 1;
			if (outParam.length == 1) {
				final String id = outParam[0].getIdentifiers()[0];
				final ASTType astType = outParam[0].getType();
				final VariableLHS lhs = //new VariableLHS(loc, outParam[0].getIdentifiers()[0]);
						ExpressionFactory.constructVariableLHS(loc,
								boogieTypeHelper.getBoogieTypeForBoogieASTType(astType),
								id,
								new DeclarationInformation(StorageClass.IMPLEMENTATION_OUTPARAM,
										dispatchingProcedureName));
				if (!voidReturnType) {
					builder.addStatement(StatementFactory.constructAssignmentStatement(loc,
							new LeftHandSide[] { lhs },
							new Expression[] { funcCallResult }));
				}
			}
			builder.addStatements(CTranslationUtil.createHavocsForAuxVars(rex.mAuxVars));
			builder.addStatement(new ReturnStatement(loc));

			return procedureManager.constructBody(loc,
					builder.getDeclarations().toArray(new VariableDeclaration[builder.getDeclarations().size()]),
					builder.getStatements().toArray(new Statement[builder.getStatements().size()]),
					dispatchingProcedureName);
		} else {
			AuxVarInfo auxvar = null;
			if (!resultTypeIsVoid) {
				auxvar = AuxVarInfo.constructAuxVarInfo(loc, main,
						funcSignature.getReturnType(),
						SFO.AUXVAR.FUNCPTRRES);
				builder.addDeclaration(auxvar.getVarDec());
				builder.addAuxVar(auxvar);
				funcCallResult = auxvar.getExp();
			}

			final ExpressionResult firstElseRex = (ExpressionResult) functionHandler.makeTheFunctionCallItself(main,
					loc, fittingFunctions.get(0), new ExpressionResultBuilder(), args);
			for (final Declaration dec : firstElseRex.mDecl) {
				builder.addDeclaration(dec);
			}
			builder.addAuxVars(firstElseRex.getAuxVars());

			final ArrayList<Statement> firstElseStmt = new ArrayList<>();
			firstElseStmt.addAll(firstElseRex.mStmt);
			if (!resultTypeIsVoid) {
				final AssignmentStatement assignment =
						StatementFactory.constructAssignmentStatement(loc, new VariableLHS[] { auxvar.getLhs() },
								new Expression[] { firstElseRex.mLrVal.getValue() });
				firstElseStmt.add(assignment);
			}
			IfStatement currentIfStmt = null;

			for (int i = 1; i < fittingFunctions.size(); i++) {
				final ExpressionResult currentRex = (ExpressionResult) functionHandler.makeTheFunctionCallItself(main,
						loc, fittingFunctions.get(i), new ExpressionResultBuilder(), args);
				for (final Declaration dec : currentRex.mDecl) {
					builder.addDeclaration(dec);
				}
				builder.addAuxVars(currentRex.mAuxVars);

				final ArrayList<Statement> newStmts = new ArrayList<>();
				newStmts.addAll(currentRex.mStmt);
				if (!resultTypeIsVoid) {
					final AssignmentStatement assignment =
							StatementFactory.constructAssignmentStatement(loc, new VariableLHS[] { auxvar.getLhs() },
									new Expression[] { currentRex.mLrVal.getValue() });
					newStmts.add(assignment);
				}

				final IdentifierExpression functionPointerIdex =
						boogieTypeHelper.constructIdentifierExpression(loc, inParams[inParams.length -1].getType(),
								inParams[inParams.length - 1].getIdentifiers()[0],
								StorageClass.IMPLEMENTATION_INPARAM, dispatchingProcedureName);
				final IdentifierExpression functionPointerValueOfCurrentFittingFunctionIdex =
						boogieTypeHelper.constructIdentifierExpression(loc,
								boogieTypeHelper.getBoogieTypeForPointerType(),
								SFO.FUNCTION_ADDRESS + fittingFunctions.get(i),
								StorageClass.IMPLEMENTATION_INPARAM, dispatchingProcedureName);
				final Expression condition =
						ExpressionFactory.newBinaryExpression(loc, BinaryExpression.Operator.COMPEQ,
								functionPointerIdex, functionPointerValueOfCurrentFittingFunctionIdex);

				if (i == 1) {
					currentIfStmt = new IfStatement(loc, condition, newStmts.toArray(new Statement[newStmts.size()]),
							firstElseStmt.toArray(new Statement[firstElseStmt.size()]));
				} else {
					currentIfStmt = new IfStatement(loc, condition, newStmts.toArray(new Statement[newStmts.size()]),
							new Statement[] { currentIfStmt });
				}
			}

			builder.addStatement(currentIfStmt);
			if (outParam.length == 1) {
				final VariableLHS dispatchingFunctionResultLhs = //new VariableLHS(loc, outParam[0].getIdentifiers()[0]);
						ExpressionFactory.constructVariableLHS(loc,
								boogieTypeHelper.getBoogieTypeForBoogieASTType(outParam[0].getType()),
								outParam[0].getIdentifiers()[0],
								new DeclarationInformation(StorageClass.IMPLEMENTATION_OUTPARAM,
										dispatchingProcedureName));
				builder.addStatement(StatementFactory.constructAssignmentStatement(loc,
						new LeftHandSide[] { dispatchingFunctionResultLhs },
						new Expression[] { funcCallResult }));
			}
			builder.addStatements(CTranslationUtil.createHavocsForAuxVars(builder.getAuxVars()));
			builder.addStatement(new ReturnStatement(loc));
			return procedureManager.constructBody(loc,
					builder.getDeclarations().toArray(new VariableDeclaration[builder.getDeclarations().size()]),
					builder.getStatements().toArray(new Statement[builder.getStatements().size()]),
					dispatchingProcedureName);
		}
	}

	class UltimateInitProcedure {

		private Declaration mUltimateInitImplementation;

		UltimateInitProcedure(final ILocation translationUnitLoc, final Dispatcher main, final IASTNode hook) {
			createInit(translationUnitLoc, main, hook);
		}

		void createInit(final ILocation translationUnitLoc, final Dispatcher main, final IASTNode hook) {

			final MemoryHandler memoryHandler = main.mCHandler.getMemoryHandler();
			final ExpressionTranslation expressionTranslation = main.mCHandler.getExpressionTranslation();
			final BoogieTypeHelper boogieTypeHelper = main.mCHandler.getBoogieTypeHelper();
			final ProcedureManager procedureManager = main.mCHandler.getProcedureManager();

			{
				final Procedure initProcedureDecl = new Procedure(translationUnitLoc, new Attribute[0], SFO.INIT,
						new String[0], new VarList[0], new VarList[0], new Specification[0], null);
				main.mCHandler.getProcedureManager().beginCustomProcedure(main, translationUnitLoc, SFO.INIT,
						initProcedureDecl);
			}
			final ArrayList<Statement> initStatements = new ArrayList<>();
			final Collection<String> proceduresCalledByUltimateInit = new HashSet<>();

			final ArrayList<VariableDeclaration> initDecl = new ArrayList<>();

			if (main.mCHandler.getMemoryHandler().getRequiredMemoryModelFeatures()
					.isMemoryModelInfrastructureRequired()) {

				// set #valid[0] = 0 (i.e., the memory at the NULL-pointer is not allocated)
				final Expression zero = mExpressionTranslation.constructLiteralForIntegerType(translationUnitLoc,
						mExpressionTranslation.getCTypeOfPointerComponents(), BigInteger.ZERO);
				final Expression literalThatRepresentsFalse = memoryHandler.getBooleanArrayHelper().constructFalse();
				final AssignmentStatement assignment = MemoryHandler.constructOneDimensionalArrayUpdate(main,
						translationUnitLoc, zero, memoryHandler.getValidArrayLhs(translationUnitLoc),
						literalThatRepresentsFalse);
				initStatements.add(0, assignment);

				// set the value of the NULL-constant to NULL = { base : 0, offset : 0 }
				final VariableLHS slhs = //new VariableLHS(translationUnitLoc, SFO.NULL);
						ExpressionFactory.constructVariableLHS(translationUnitLoc,
								boogieTypeHelper.getBoogieTypeForPointerType(),
								SFO.NULL,
								DeclarationInformation.DECLARATIONINFO_GLOBAL);
				initStatements.add(0, StatementFactory.constructAssignmentStatement(translationUnitLoc,
						new LeftHandSide[] { slhs },
						new Expression[] {
								ExpressionFactory.constructStructConstructor(translationUnitLoc,
										new String[] { "base", "offset" },
								new Expression[] {
										expressionTranslation.constructLiteralForIntegerType(translationUnitLoc,
												expressionTranslation.getCTypeOfPointerComponents(), BigInteger.ZERO),
										expressionTranslation.constructLiteralForIntegerType(translationUnitLoc,
												expressionTranslation.getCTypeOfPointerComponents(),
												BigInteger.ZERO) }) }));
			}

			/*
			 * We need to follow some order when addin the statements to init.
			 * Current strategy:
			 *  <li> First come all the statements that have been added via
			 *    {@link StaticObjectsHandler.addStatementsForUltimateInit} manually.
			 *  <li> After that we add the statements for the initialization of objects with static storage duration.
			 *  <li> Each of those lists is added in the order that we added those to the {@link StaticObjectsHandler}.
			 * It is unclear how generally feasible this strategy is, we know however that exchanging bullets 1 and 2
			 *  breaks regression/ctrans-bug-min-TE-static-const-uint8_t.c )
			 */
			final List<Statement> staticObjectInitStatements = new ArrayList<>();

			// initialization for statics and other globals
			for (final Entry<VariableDeclaration, CDeclaration> en :
					main.mCHandler.getStaticObjectsHandler().getGlobalVariableDeclsWithAssociatedCDecls().entrySet()) {
				final ILocation currentDeclsLoc = en.getKey().getLocation();
				final InitializerResult initializer = en.getValue().getInitializer();

				/*
				 * global variables with external linkage are not implicitly initialized. (They are initialized by the
				 * module that provides them..)
				 */
				if (en.getValue().isExtern()) {
					continue;
				}

				for (final VarList vl : en.getKey().getVariables()) {
					for (final String id : vl.getIdentifiers()) {

						final VariableLHS lhs = //new VariableLHS(currentDeclsLoc, id);
								ExpressionFactory.constructVariableLHS(currentDeclsLoc,
										main.mCHandler.getBoogieTypeHelper()
											.getBoogieTypeForBoogieASTType(vl.getType()),
										id,
										DeclarationInformation.DECLARATIONINFO_GLOBAL);

						if (main.mCHandler.isHeapVar(id)) {
							final LocalLValue llVal =
									new LocalLValue(lhs, en.getValue().getType(), null);
							staticObjectInitStatements.add(memoryHandler.getMallocCall(llVal, currentDeclsLoc, hook));
							proceduresCalledByUltimateInit.add(MemoryModelDeclarations.Ultimate_Alloc.name());
						}

						final ExpressionResult initRex = main.mCHandler.getInitHandler().initialize(currentDeclsLoc,
								main, lhs, en.getValue().getType(), initializer, hook);
						for (final Statement stmt : initRex.getStatements()) {
							if (stmt instanceof CallStatement) {
								proceduresCalledByUltimateInit.add(((CallStatement) stmt).getMethodName());
							}
							staticObjectInitStatements.add(stmt);
						}
						staticObjectInitStatements.addAll(CTranslationUtil.createHavocsForAuxVars(initRex.mAuxVars));
						for (final Declaration d : initRex.mDecl) {
							initDecl.add((VariableDeclaration) d);
						}
					}
				}
			}

			main.mCHandler.getStaticObjectsHandler().freeze();
			initStatements.addAll(main.mCHandler.getStaticObjectsHandler().getStatementsForUltimateInit());
			initStatements.addAll(staticObjectInitStatements);

			/*
			 * note that we only have to deal with the implementation part of the procedure, the declaration is managed
			 * by the FunctionHandler
			 */
			final Body initBody = procedureManager.constructBody(translationUnitLoc,
					initDecl.toArray(new VariableDeclaration[initDecl.size()]),
					initStatements.toArray(new Statement[initStatements.size()]),
					SFO.INIT);
			final Procedure initProcedureImplementation = new Procedure(translationUnitLoc, new Attribute[0],
					SFO.INIT, new String[0], new VarList[0], new VarList[0], null, initBody);


			main.mCHandler.getProcedureManager().endCustomProcedure(main, SFO.INIT);

			mUltimateInitImplementation = initProcedureImplementation;
		}

		public Declaration getUltimateInitImplementation() {
			assert mUltimateInitImplementation != null;
			return mUltimateInitImplementation;
		}

	}


	class UltimateStartProcedure {

		private Procedure mStartProcedure;

		UltimateStartProcedure(final Dispatcher main, final ILocation loc, final IASTNode hook) {
			createStartProc(main, loc, hook);
		}

		void createStartProc(final Dispatcher main, final ILocation loc, final IASTNode hook) {

			final FunctionHandler functionHandler = main.mCHandler.getFunctionHandler();
			final ProcedureManager procedureManager = main.mCHandler.getProcedureManager();
			final BoogieTypeHelper boogieTypeHelper = main.mCHandler.getBoogieTypeHelper();

//			final Map<String, Procedure> procedures = functionHandler.getProcedures();
			final String checkedMethod = main.getCheckedMethod();

			Procedure startProcedure = null;


				{
					final Procedure startDeclaration = new Procedure(loc, new Attribute[0], SFO.START, new String[0],
						new VarList[0], new VarList[0], new Specification[0], null);
					procedureManager.beginCustomProcedure(main, loc, SFO.START, startDeclaration);
				}

//				Procedure startDeclaration = null;
//				Specification[] specsStart = new Specification[0];

//				functionHandler.addCallGraphEdge(SFO.START, SFO.INIT);
//				functionHandler.addCallGraphEdge(SFO.START, checkedMethod);

				final ArrayList<Statement> startStmt = new ArrayList<>();
				final ArrayList<VariableDeclaration> startDecl = new ArrayList<>();
//				specsStart = new Specification[1];
				startStmt.add(StatementFactory.constructCallStatement(loc, false, new VariableLHS[0], SFO.INIT,
						new Expression[0]));
				final VarList[] checkedMethodOutParams =
						procedureManager.getProcedureDeclaration(checkedMethod).getOutParams();
				final VarList[] checkedMethodInParams =
						procedureManager.getProcedureDeclaration(checkedMethod).getInParams();
				final Specification[] checkedMethodSpec =
						procedureManager.getProcedureDeclaration(checkedMethod).getSpecification();

				// find out the requires specs of the checked method and assume it before its start
				final ArrayList<Statement> reqSpecsAssumes = new ArrayList<>();
				for (final Specification spec : checkedMethodSpec) {
					if (spec instanceof RequiresSpecification) {
						reqSpecsAssumes.add(new AssumeStatement(loc, ((RequiresSpecification) spec).getFormula()));
					}
				}
				startStmt.addAll(reqSpecsAssumes);

				final ArrayList<Expression> args = new ArrayList<>();
				if (checkedMethodInParams.length > 0) {
					startDecl.add(new VariableDeclaration(loc, new Attribute[0], checkedMethodInParams));
					for (final VarList arg : checkedMethodInParams) {
						assert arg.getIdentifiers().length == 1; // by construction
						final String id = arg.getIdentifiers()[0];
						final IdentifierExpression idEx = //new IdentifierExpression(loc, id);
								boogieTypeHelper.constructIdentifierExpression(loc,
										arg.getType(), id, StorageClass.LOCAL, SFO.START);
						args.add(idEx);
					}
				}
				if (checkedMethodOutParams.length != 0) {
					assert checkedMethodOutParams.length == 1;
					// there is 1(!) return value
//					final String checkMethodRet = main.mNameHandler.getTempVarUID(SFO.AUXVAR.RETURNED, null);
//					final VarList tempVar =
//							new VarList(loc, new String[] { checkMethodRet }, checkedMethodOutParams[0].getType());
//					final VariableDeclaration tmpVar =
//							new VariableDeclaration(loc, new Attribute[0], new VarList[] { tempVar });
					final CType checkedMethodResultCType =
							procedureManager.getCFunctionType(checkedMethod).getResultType();
					final AuxVarInfo checkedMethodReturnAuxVar = AuxVarInfo.constructAuxVarInfo(loc, main,
							checkedMethodResultCType, SFO.AUXVAR.RETURNED);
					main.mCHandler.getSymbolTable().addBoogieCIdPair(
							checkedMethodReturnAuxVar.getExp().getIdentifier(),
							SFO.NO_REAL_C_VAR + checkedMethodReturnAuxVar.getExp().getIdentifier(),
//							checkMethodRet,
//							SFO.NO_REAL_C_VAR + checkMethodRet,
							loc);
					startDecl.add(checkedMethodReturnAuxVar.getVarDec());
					startStmt.add(StatementFactory.constructCallStatement(loc, false,
							new VariableLHS[] { checkedMethodReturnAuxVar.getLhs() },
							checkedMethod, args.toArray(new Expression[args.size()])));
//					procedureManager.registerCall(checkedMethod);
				} else { // void
					startStmt.add(StatementFactory.constructCallStatement(loc, false, new VariableLHS[0], checkedMethod,
							args.toArray(new Expression[args.size()])));
//					procedureManager.registerCall(checkedMethod);
				}

//				final LinkedHashSet<VariableLHS> startModifiesClause = new LinkedHashSet<>();
//				for (final String id : mInitializedGlobals) {
//					startModifiesClause.add(new VariableLHS(loc, id));
//				}

				// should not be necessary if we treat start and init as normal procedures
//				for (final String id : functionHandler.getModifiedGlobals().get(checkedMethod)) {
//					startModifiesClause.add(new VariableLHS(loc, id));
//				}

//				specsStart[0] = new ModifiesSpecification(loc, false,
//						startModifiesClause.toArray(new VariableLHS[startModifiesClause.size()]));

				final Body startBody = procedureManager.constructBody(loc,
						startDecl.toArray(new VariableDeclaration[startDecl.size()]),
						startStmt.toArray(new Statement[startStmt.size()]), SFO.START);
//				final Body startBody = new Body(loc, startDecl.toArray(new VariableDeclaration[startDecl.size()]),
//						startStmt.toArray(new Statement[startStmt.size()]));

				startProcedure = new Procedure(loc, new Attribute[0], SFO.START, new String[0], new VarList[0],
						new VarList[0], null, startBody);

				/* note that we only deal with the implementation of the procedure here, the declaration is managed
				 * by the FucntionHandler
				 */
//				final Procedure startDeclaration = new Procedure(loc, new Attribute[0], SFO.START, new String[0],
//						new VarList[0], new VarList[0], specsStart, null);

//				final List<String> proceduresCalledByStart = Arrays.asList(new String[] { SFO.INIT, checkedMethod });

//				functionHandler.endUltimateInitOrStart(main, startDeclaration, SFO.START, proceduresCalledByStart);
//				functionHandler.endUltimateInitOrStart(main, startDeclaration, SFO.START);
				procedureManager.endCustomProcedure(main, SFO.START);

			mStartProcedure = startProcedure;
		}

		public Declaration getUltimateStartImplementation() {
			assert mStartProcedure != null;
			return mStartProcedure;
		}
	}
}
