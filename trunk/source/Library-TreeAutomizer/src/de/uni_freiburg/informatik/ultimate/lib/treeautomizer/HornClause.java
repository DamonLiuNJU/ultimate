package de.uni_freiburg.informatik.ultimate.lib.treeautomizer;

import java.util.Collections;
import java.util.List;
import java.util.Set;

import de.uni_freiburg.informatik.ultimate.automata.tree.IRankedLetter;
import de.uni_freiburg.informatik.ultimate.logic.Term;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.smt.managedscript.ManagedScript;

/**
 * This is our internal representation of a Horn clause. A Horn clause consists
 * of
 * <ul>
 * <li> a body with
 *  <ul>
 *   <li> n uninterpreted predicate symbols (n >= 0)
 *   <li> a transition formula
 *  </ul>
 * <li> a head with either
 *  <ul>
 *   <li> an uninterpreted predicate symbol  or
 *   <li> false
 *  </ul>
 * <li> a mapping that assigns each of the argument positions of the uninterpreted predicate a free variable in the
 *   transition formula
 * </ul>
 *
 * This class stores Horn clauses in a certain normal form:
 * <ul>
 *  <li> The arguments of the head predicate are a list of variables, which are determined by the argument position and
 *    the sort of that argument in the head predicate's signature.
 *      E.g. for two head predicates with the same signature, we have the same arguments.
 *
 * </ul>
 *
 * @author Alexander Nutz (nutz@informatik.uni-freiburg.de)
 *
 */
public class HornClause implements IRankedLetter {

	private final List<HornClausePredicateSymbol> mBodyPreds;

	private final List<List<Term>> mBodyPredToArgs;

	/**
	 * Stores for the predicate symbol in the head at every argument position of
	 * the represented atom, which TermVariable in the transition formula
	 * represents that argument in the represented atom.
	 */
//	private final List<TermVariable> mHeadPredTermVariables;
	private final List<HcHeadVar> mHeadPredVariables;
	private final HornClausePredicateSymbol mHeadPredicate;

	private final HCSymbolTable mHornClauseSymbolTable;

	private final Term mFormula;

	private final boolean mHeadIsFalse;

//	private final Set<TermVariable> mBodyPredVariables;
	private final Set<HcBodyVar> mBodyVariables;


	/**
	 * Constructor for a Horn clause of the form b1 /\ ... /\ bn /\ constraint
	 * --> false. Where b1 .. bn are uninterpreted predicates and constraint is
	 * a Term.
	 *
	 * @param script
	 * @param symbolTable
	 * @param constraint
	 * @param bodyPreds
	 * @param bodyPredToTermVariables
	 */
	public HornClause(final ManagedScript script, final HCSymbolTable symbolTable, final Term constraint,
			final List<HornClausePredicateSymbol> bodyPreds, final List<List<Term>> bodyPredToTermVariables,
			final Set<HcBodyVar> bodyVars
			) {
		this(script, symbolTable, constraint, null, Collections.emptyList(), bodyPreds, bodyPredToTermVariables,
				bodyVars, false);
	}

	public HornClause(final ManagedScript script, final HCSymbolTable symbolTable, final Term constraint,
//			final HornClausePredicateSymbol headPred, final List<TermVariable> headVars,
			final HornClausePredicateSymbol headPred, final List<HcHeadVar> headVars,
			final List<HornClausePredicateSymbol> bodyPreds, final List<List<Term>> bodyPredToTermVariables,
			final Set<HcBodyVar> bodyVars
			) {
		this(script, symbolTable, constraint, headPred, headVars, bodyPreds, bodyPredToTermVariables, bodyVars, false);
		assert headPred != null : "use other constructor for '... -> False' case";
	}

	/**
	 * Constructor for a Horn clause of the form b1 /\ ... /\ bn /\ constraint
	 * --> h. Where b1 .. bn, and h, are uninterpreted predicates and constraint
	 * is a Term.
	 *
	 * @param script
	 *            The script that will be used in TreeAutomizer (not the
	 *            HornClauseParserScript)
	 * @param symbolTable
	 * @param constraint
	 * @param headPred
	 * @param headVars
	 * @param bodyPreds
	 * @param bodyPredToArgs
	 * @param dummy
	 *            dummy parameter to allow for an extra constructor
	 */
	private HornClause(final ManagedScript script, final HCSymbolTable symbolTable, final Term constraint,
//			final HornClausePredicateSymbol headPred, final List<TermVariable> headVars,
			final HornClausePredicateSymbol headPred, final List<HcHeadVar> headVars,
			final List<HornClausePredicateSymbol> bodyPreds, final List<List<Term>> bodyPredToArgs,
			final Set<HcBodyVar> bodyVars,
			final boolean dummy) {

		mHornClauseSymbolTable = symbolTable;

		mFormula = constraint;

		mHeadIsFalse = headPred == null;
		mHeadPredicate = headPred;
		mHeadPredVariables = mHeadIsFalse ? null : Collections.unmodifiableList(headVars);
		mBodyPreds = Collections.unmodifiableList(bodyPreds);
		mBodyPredToArgs = Collections.unmodifiableList(bodyPredToArgs);
		mBodyVariables = Collections.unmodifiableSet(bodyVars);

//		final TermTransferrer ttf = new TermTransferrer(script.getScript());
//
//		/*
//		 * send all the TermVariables through the TermTransferrer
//		 */
//		mHeadPredTermVariables = headVars.stream().map(var -> (TermVariable) ttf.transform(var))
//				.collect(Collectors.toList());
//		mBodyPredToTermVariables = bodyPredToTermVariables.stream()
//				.map(list -> list.stream().map(var -> ttf.transform(var)).collect(Collectors.toList()))
//				.collect(Collectors.toList());
//
//		// transfer the transition formula to the solver script
//		mFormula = ttf.transform(constraint);
//
////		mHeadPredTermVariables.forEach(mHornClauseSymbolTable::registerHeadVariable);
//		for (int i = 0; i < mHeadPredicate.getArity(); i++) {
//			final Sort sort = mHeadPredicate.getParameterSorts().get(i);
////			mHeadPredTermVariables
//		}
//
//		for (final TermVariable fv : mFormula.getFreeVars()) {
//			mHornClauseSymbolTable.registerTermVariable(fv);
//		}
//
//
//		mBodyPredVariables = new HashSet<>();
//		for (final List<Term> ts : mBodyPredToTermVariables) {
//			for (final Term t : ts) {
//				if (t instanceof TermVariable) {
//					mHornClauseSymbolTable.registerTermVariable((TermVariable) t);
//					if (!mHeadPredTermVariables.contains(t)) {
//						mBodyPredVariables.add((TermVariable) t);
//					}
//				}
//			}
//		}

//		mHeadIsFalse = headPred == null;
//		mHeadPredicate = headPred;
//		mBodyPreds = bodyPreds;
	}

	public HornClausePredicateSymbol getHeadPredicate() {
		if (mHeadIsFalse) {
			throw new AssertionError("Check for isHeadFalse() before calling this");
		}
		return mHeadPredicate;
	}

	public boolean isHeadFalse() {
		return mHeadIsFalse;
	}

	public List<HornClausePredicateSymbol> getBodyPredicates() {
		return mBodyPreds;
	}

	public int getNoBodyPredicates() {
		return mBodyPreds.size();
	}

	public Term getPredArgTermVariable(final int predPos, final int argPos) {
		return mBodyPredToArgs.get(predPos).get(argPos);
	}

	public List<Term> getTermVariablesForPredPos(final int predPos) {
		return mBodyPredToArgs.get(predPos);
	}

	public List<List<Term>> getBodyPredToArgs() {
		return Collections.unmodifiableList(mBodyPredToArgs);
	}

//	public List<TermVariable> getTermVariablesForHeadPred() {
	public List<HcHeadVar> getTermVariablesForHeadPred() {
		return mHeadPredVariables;
	}

	public String debugString() {

		String cobody = "";

		for (int i = 0; i < mBodyPredToArgs.size(); ++i) {
			cobody += " " + mBodyPreds.get(i) + "(";
			cobody += mBodyPredToArgs.get(i);
			cobody += ")";
		}
		if (cobody.length() > 0) {
			cobody = "and" + cobody;
		} else {
			cobody = "true";
		}

		final String body = mHeadIsFalse ? "false" : mHeadPredicate.getName() ;
		if (mFormula == null) {
			return "unintialized HornClause";
		}

		return String.format("(%s) ^^ (%s) ~~> (%s)", cobody, mFormula.toString(), body);
	}

	@Override
	public String toString() {
		// String cobody = "";
		//
		// for (final HornClausePredicateSymbol symbol :
		// mBodyPredToTermVariables.keySet()) {
		// cobody += " " + symbol.getName() +
		// mBodyPredToTermVariables.get(symbol);
		// }
		// if (cobody.length() > 0) {
		// cobody = "and" + cobody;
		// } else {
		// cobody = "true";
		// }
		//
		// final String body = mHeadPredicate.getName() +
		// mHeadPredTermVariables;
		// if (mTransitionFormula == null) {
		if (mFormula == null) {
			return "unintialized HornClause";
		}

		// return mTransitionFormula.getFormula().toString();
		return mFormula.toStringDirect();
		// return String.format("(%s) ^^ (%s) ~~> (%s) || in : %s || out : %s ",
		// cobody, mTransitionFormula, body,
		// return String.format("(%s) ^^ (%s) ~~> (%s)", cobody,
		// mTransitionFormula.getFormula(), body);
		// return "HornClause TODO: better description"; //TODO
	}

	public HCSymbolTable getHornClauseSymbolTable() {
		return mHornClauseSymbolTable;
	}

	@Override
	public int getRank() {
		// if (mBodyPreds.isEmpty())
		// {//mTransitionFormula.getInVars().isEmpty()) {
		// // Initial state
		// return 1;
		// }
		return mBodyPreds.size();// mTransitionFormula.getInVars().size();
	}

	public Term getConstraintFormula() {
		return mFormula;
	}

	/**
	 * Retrieve the variables that occur free as an argument in a body predicate. However, exempt the variables that
	 * are arguments of the head predicate.
	 * (these variables roughly correspond to the primed variabels in a transition formula..)
	 *
	 * @return
	 */
//	public Set<TermVariable> getBodyPredVariables() {
	public Set<HcBodyVar> getBodyPredVariables() {
		return mBodyVariables;
	}

}
