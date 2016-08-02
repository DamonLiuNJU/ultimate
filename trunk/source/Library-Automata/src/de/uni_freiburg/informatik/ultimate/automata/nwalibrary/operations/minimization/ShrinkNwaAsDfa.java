/*
 * Copyright (C) 2016 Christian Schilling (schillic@informatik.uni-freiburg.de)
 * Copyright (C) 2009-2016 University of Freiburg
 * 
 * This file is part of the ULTIMATE Automata Library.
 * 
 * The ULTIMATE Automata Library is free software: you can redistribute it
 * and/or modify it under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation, either version 3 of the License,
 * or (at your option) any later version.
 * 
 * The ULTIMATE Automata Library is distributed in the hope that it will be
 * useful, but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser
 * General Public License for more details.
 * 
 * You should have received a copy of the GNU Lesser General Public License
 * along with the ULTIMATE Automata Library. If not, see
 * <http://www.gnu.org/licenses/>.
 * 
 * Additional permission under GNU GPL version 3 section 7: If you modify the
 * ULTIMATE Automata Library, or any covered work, by linking or combining it
 * with Eclipse RCP (or a modified version of Eclipse RCP), containing parts
 * covered by the terms of the Eclipse Public License, the licensors of the
 * ULTIMATE Automata Library grant you additional permission to convey the
 * resulting work.
 */
package de.uni_freiburg.informatik.ultimate.automata.nwalibrary.operations.minimization;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Map;
import java.util.Map.Entry;
import java.util.PriorityQueue;
import java.util.Set;

import de.uni_freiburg.informatik.ultimate.automata.AutomataLibraryException;
import de.uni_freiburg.informatik.ultimate.automata.AutomataLibraryServices;
import de.uni_freiburg.informatik.ultimate.automata.AutomataOperationCanceledException;
import de.uni_freiburg.informatik.ultimate.automata.IOperation;
import de.uni_freiburg.informatik.ultimate.automata.nwalibrary.IDoubleDeckerAutomaton;
import de.uni_freiburg.informatik.ultimate.automata.nwalibrary.INestedWordAutomaton;
import de.uni_freiburg.informatik.ultimate.automata.nwalibrary.INestedWordAutomatonSimple;
import de.uni_freiburg.informatik.ultimate.automata.nwalibrary.StateFactory;
import de.uni_freiburg.informatik.ultimate.automata.nwalibrary.transitions.IncomingCallTransition;
import de.uni_freiburg.informatik.ultimate.automata.nwalibrary.transitions.IncomingInternalTransition;
import de.uni_freiburg.informatik.ultimate.automata.nwalibrary.transitions.IncomingReturnTransition;
import de.uni_freiburg.informatik.ultimate.automata.nwalibrary.transitions.OutgoingCallTransition;
import de.uni_freiburg.informatik.ultimate.automata.nwalibrary.transitions.OutgoingInternalTransition;
import de.uni_freiburg.informatik.ultimate.automata.nwalibrary.transitions.OutgoingReturnTransition;
import de.uni_freiburg.informatik.ultimate.util.datastructures.relation.Pair;

/**
 * This class minimizes nested word automata.
 * 
 * It is based on Hopcroft's minimization for deterministic finite automata. All
 * nested edges (calls and returns) are seen as fresh symbols consisting of the
 * tuple <code>(symbol, hierarchical state)</code>
 * 
 * @author Christian Schilling <schillic@informatik.uni-freiburg.de>
 * @param <LETTER> letter type
 * @param <STATE> state type
 */
public class ShrinkNwaAsDfa<LETTER, STATE>
		extends AMinimizeNwa<LETTER, STATE>
		implements IOperation<LETTER, STATE> {
	// old automaton
	private IDoubleDeckerAutomaton<LETTER, STATE> mDoubleDecker;
	// partition object
	private Partition mPartition;
	// IDs for equivalence classes
	private int mIds;
	// work lists
	private WorkList mWorkList;
	// simulates the output automaton
	private ShrinkNwaResult mResult;
	
	/**
	 * This constructor creates a copy of the operand.
	 * 
	 * @param services Ultimate services
	 * @param stateFactory state factory
	 * @param operand preprocessed nested word automaton preprocessing: dead end
	 *        and unreachable states/transitions removed
	 * @throws AutomataOperationCanceledException if cancel signal is received
	 */
	public ShrinkNwaAsDfa(final AutomataLibraryServices services,
			final StateFactory<STATE> stateFactory,
			final INestedWordAutomaton<LETTER, STATE> operand)
					throws AutomataLibraryException {
		this(services, stateFactory, operand, null, false, false);
	}
	
	/**
	 * This constructor creates a copy of the operand with an initial partition.
	 * 
	 * @param services Ultimate services
	 * @param operand preprocessed nested word automaton preprocessing: dead end
	 *        and unreachable states/transitions removed
	 * @param equivalenceClasses represent initial equivalence classes
	 * @param stateFactory used for Hoare annotation
	 * @param includeMapping true iff mapping old to new state is needed
	 * @param considerNeutralStates true iff neutral states should be considered
	 * @throws AutomataOperationCanceledException if cancel signal is received
	 */
	public ShrinkNwaAsDfa(final AutomataLibraryServices services,
			final StateFactory<STATE> stateFactory,
			final INestedWordAutomaton<LETTER, STATE> operand,
			final Collection<Set<STATE>> equivalenceClasses,
			final boolean includeMapping, final boolean considerNeutralStates)
					throws AutomataLibraryException {
		super(services, stateFactory, "shrinkNwaAsDfa", operand);
		
		mDoubleDecker = considerNeutralStates
				? (IDoubleDeckerAutomaton<LETTER, STATE>) mOperand
				: null;
		mPartition = new Partition();
		mIds = 0;
		mWorkList = new WorkList();
		
		// must be the last part of the constructor
		minimize(equivalenceClasses, includeMapping);
		mLogger.info(exitMessage());
	}
	
	// --- [start] main methods --- //
	
	/**
	 * This is the main method that merges states not distinguishable (based on
	 * Hopcroft's algorithm).
	 * 
	 * @param isFiniteAutomaton true iff automaton is a finite automaton
	 * @param modules predefined modules that must be split
	 * @param includeMapping true iff mapping old to new state is needed
	 * @throws AutomataOperationCanceledException if cancel signal is received
	 */
	private void minimize(final Iterable<Set<STATE>> modules,
			final boolean includeMapping) throws AutomataLibraryException {
		// initialize the partition object
		initialize(modules);
		
		final InternalTransitionIterator internalIterator =
				new InternalTransitionIterator();
		final CallTransitionIterator callIterator =
				new CallTransitionIterator();
		final ReturnTransitionIterator returnIterator =
				new ReturnTransitionIterator();
				
		// internals and calls
		while (mWorkList.hasNext()) {
			// cancel if signal is received
			if (!mServices.getProgressMonitorService().continueProcessing()) {
				throw new AutomataOperationCanceledException(this.getClass());
			}
			
			// cancel if signal is received
			if (!mServices.getProgressMonitorService().continueProcessing()) {
				throw new AutomataOperationCanceledException(this.getClass());
			}
			
			final EquivalenceClass a = mWorkList.next();
			
			// internal split
			if (a.mIncomingInt == EIncomingStatus.IN_WL) {
				a.mIncomingInt = EIncomingStatus.UNKNOWN;
				splitPredecessors(a, internalIterator,
						ETransitionType.INTERNAL);
			}
			
			// call split
			if (a.mIncomingCall == EIncomingStatus.IN_WL) {
				a.mIncomingCall = EIncomingStatus.UNKNOWN;
				splitPredecessors(a, callIterator, ETransitionType.CALL);
			}
			
			// return split
			if (a.mIncomingRet == EIncomingStatus.IN_WL) {
				a.mIncomingRet = EIncomingStatus.UNKNOWN;
				splitPredecessors(a, returnIterator, ETransitionType.RETURN);
			}
		}
		
		mLogger.info("Finished analysis, constructing result of size " +
				mPartition.mEquivalenceClasses.size());
				
		// automaton construction
		constructAutomaton(includeMapping);
	}
	
	/**
	 * The partition object is initialized. Final states are separated from
	 * non-final states. For the passed modules this is assumed.
	 * 
	 * @param modules modules that must be split
	 */
	private void initialize(final Iterable<Set<STATE>> modules) {
		// split final from non-final states
		if (modules == null) {
			final HashSet<STATE> finals = new HashSet<STATE>();
			final HashSet<STATE> nonfinals = new HashSet<STATE>();
			
			for (final STATE state : mOperand.getStates()) {
				if (mOperand.isFinal(state)) {
					finals.add(state);
				} else {
					nonfinals.add(state);
				}
			}
			
			if (! finals.isEmpty()) {
				mPartition.addEcInitialization(finals);
			}
			if (! nonfinals.isEmpty()) {
				mPartition.addEcInitialization(nonfinals);
			}
		} else {
			// predefined modules are already split with respect to final states
			assert assertStatesSeparation(
					modules) : "The states in the initial modules are not separated with " +
							"respect to their final status.";
			for (final Set<STATE> module : modules) {
				mPartition.addEcInitialization(module);
			}
		}
	}
	
	/**
	 * For each state and symbol respectively do the usual Hopcroft backwards
	 * split.
	 * 
	 * First all predecessor sets (with respect to a single symbol) are found
	 * and then for each such set the states are split from their equivalence
	 * classes.
	 * 
	 * @param a the splitter equivalence class
	 * @param iterator the iterator abstracting from the letter type
	 * @param isInternal true iff split is internal
	 */
	private void splitPredecessors(final EquivalenceClass a,
			final ITransitionIterator<LETTER, STATE> iterator,
			final ETransitionType type) {
		assert ((type == ETransitionType.INTERNAL &&
				(iterator instanceof ShrinkNwaAsDfa.InternalTransitionIterator) &&
				(a.mIncomingInt != EIncomingStatus.IN_WL)) ||
				(type == ETransitionType.CALL &&
						(iterator instanceof ShrinkNwaAsDfa.CallTransitionIterator) &&
						(a.mIncomingCall != EIncomingStatus.IN_WL)) ||
				(type == ETransitionType.RETURN &&
						(iterator instanceof ShrinkNwaAsDfa.ReturnTransitionIterator) &&
						(a.mIncomingRet != EIncomingStatus.IN_WL)));
						
		// create a hash map from letter to respective predecessor states
		final HashMap<Pair<LETTER, STATE>, HashSet<STATE>> letter2states =
				new HashMap<Pair<LETTER, STATE>, HashSet<STATE>>();
		for (final STATE state : a.mStates) {
			iterator.nextState(state);
			while (iterator.hasNext()) {
				final Pair<LETTER, STATE> letter = iterator.nextAndLetter();
				HashSet<STATE> predecessorSet = letter2states.get(letter);
				if (predecessorSet == null) {
					predecessorSet = new HashSet<STATE>();
					letter2states.put(letter, predecessorSet);
				}
				predecessorSet.add(iterator.getPred());
			}
		}
		
		// remember that this equivalence class has no incoming transitions
		if (letter2states.isEmpty()) {
			switch (type) {
				case INTERNAL:
					a.mIncomingInt = EIncomingStatus.NONE;
					break;
					
				case CALL:
					a.mIncomingCall = EIncomingStatus.NONE;
					break;
					
				case RETURN:
					a.mIncomingRet = EIncomingStatus.NONE;
					break;
			}
		} else {
			// split each map value (set of predecessor states)
			for (final Entry<Pair<LETTER, STATE>, HashSet<STATE>> entry : letter2states
					.entrySet()) {
				final Pair<LETTER, STATE> letter;
				if (mDoubleDecker == null) {
					letter = null;
				} else {
					switch (type) {
						case INTERNAL:
						case CALL:
							letter = null;
							break;
							
						case RETURN:
							letter = entry.getKey();
							break;
							
						default:
							throw new IllegalArgumentException("Illegal type.");
					}
				}
				final HashSet<STATE> predecessorSet = entry.getValue();
				assert (!predecessorSet.isEmpty());
				mPartition.splitEquivalenceClasses(predecessorSet, letter);
			}
		}
	}
	
	/**
	 * For each remaining equivalence class create a new state. Also remove all
	 * other objects references.
	 * 
	 * @param includeMapping true iff mapping old to new state is needed
	 */
	private void constructAutomaton(final boolean includeMapping) {
		mResult = new ShrinkNwaResult(includeMapping);
		
		// clean up
		mPartition = null;
		mWorkList = null;
	}
	
	// --- [end] main methods --- //
	
	// --- [start] helper methods and classes --- //
	
	/**
	 * type of a transition/symbol
	 */
	private enum ETransitionType {
		INTERNAL,
		CALL,
		RETURN
	}
	
	/**
	 * This enum is used to tell for an equivalence class whether it contains
	 * incoming transitions. Since it is expensive to compute this each time,
	 * only the answer "no" is correct. This status is inherited by the two
	 * resulting equivalence classes after a split. The idea is to not insert
	 * such equivalence classes in the work list for which it is known that
	 * there are no incoming transitions. The status is updated as a byproduct
	 * after the search for transitions.
	 */
	private enum EIncomingStatus {
		/** unknown whether there are incoming transitions */
		UNKNOWN,
		
		/** equivalence class is in work list */
		IN_WL,
		
		/** there are no incoming transitions */
		NONE
	}
	
	/**
	 * A transition iterator is used for splitting internal and call
	 * predecessors.
	 *
	 * @param <STATE> state type
	 * @param <LETTER> letter type
	 */
	private interface ITransitionIterator<LETTER, STATE> {
		/**
		 * A new successor state is considered.
		 *
		 * @param state the successor state
		 * @return the next predecessor
		 */
		void nextState(final STATE state);
		
		/**
		 * The iterator is told to consider the next transition.
		 * 
		 * @return a tuple with letter and hierarchical state of next transition
		 */
		Pair<LETTER, STATE> nextAndLetter();
		
		/**
		 * Tells whether the iterator has another transition.
		 *
		 * @return true iff there is another transition left
		 */
		boolean hasNext();
		
		/**
		 * @return the predecessor state
		 */
		STATE getPred();
	}
	
	/**
	 * This is the implementation for internal transitions.
	 */
	private class InternalTransitionIterator
			implements ITransitionIterator<LETTER, STATE> {
		// iterator of the operand
		private Iterator<IncomingInternalTransition<LETTER, STATE>> mIterator;
		// current transition
		private IncomingInternalTransition<LETTER, STATE> mTransition;
		
		@Override
		public void nextState(final STATE state) {
			mIterator = mOperand.internalPredecessors(state).iterator();
		}
		
		@Override
		public STATE getPred() {
			return mTransition.getPred();
		}
		
		@Override
		public Pair<LETTER, STATE> nextAndLetter() {
			mTransition = mIterator.next();
			// NOTE: the state does not matter, but the value must be non-null
			return new Pair<LETTER, STATE>(mTransition.getLetter(),
					mTransition.getPred());
		}
		
		@Override
		public boolean hasNext() {
			return mIterator.hasNext();
		}
	}
	
	/**
	 * This is the implementation for call transitions.
	 */
	private class CallTransitionIterator
			implements ITransitionIterator<LETTER, STATE> {
		// iterator of the operand
		private Iterator<IncomingCallTransition<LETTER, STATE>> mIterator;
		// current transition
		private IncomingCallTransition<LETTER, STATE> mTransition;
		
		@Override
		public void nextState(final STATE state) {
			mIterator = mOperand.callPredecessors(state).iterator();
		}
		
		@Override
		public Pair<LETTER, STATE> nextAndLetter() {
			mTransition = mIterator.next();
			return new Pair<LETTER, STATE>(mTransition.getLetter(),
					mTransition.getPred());
		}
		
		@Override
		public STATE getPred() {
			return mTransition.getPred();
		}
		
		@Override
		public boolean hasNext() {
			return mIterator.hasNext();
		}
	}
	
	/**
	 * This is the implementation for return transitions.
	 */
	private class ReturnTransitionIterator
			implements ITransitionIterator<LETTER, STATE> {
		// iterator of the operand
		private Iterator<IncomingReturnTransition<LETTER, STATE>> mIterator;
		// current transition
		private IncomingReturnTransition<LETTER, STATE> mTransition;
		
		@Override
		public void nextState(final STATE state) {
			mIterator = mOperand.returnPredecessors(state).iterator();
		}
		
		@Override
		public Pair<LETTER, STATE> nextAndLetter() {
			mTransition = mIterator.next();
			return new Pair<LETTER, STATE>(mTransition.getLetter(),
					mTransition.getHierPred());
		}
		
		@Override
		public STATE getPred() {
			return mTransition.getLinPred();
		}
		
		@Override
		public boolean hasNext() {
			return mIterator.hasNext();
		}
	}
	
	/**
	 * This method checks that the states in each equivalence class initially
	 * passed in the constructor are all either final or non-final.
	 *
	 * @param equivalenceClasses partition passed in constructor
	 * @return true iff equivalence classes respect final status of states
	 */
	private boolean assertStatesSeparation(
			final Iterable<Set<STATE>> equivalenceClasses) {
		for (final Set<STATE> equivalenceClass : equivalenceClasses) {
			final Iterator<STATE> it = equivalenceClass.iterator();
			assert (it
					.hasNext()) : "Empty equivalence classes should be avoided.";
			final boolean isFinal = mOperand.isFinal(it.next());
			while (it.hasNext()) {
				if (isFinal != mOperand.isFinal(it.next())) {
					return false;
				}
			}
		}
		return true;
	}
	
	/**
	 * Returns a Map from states of the input automaton to states of the output
	 * automaton. The image of a state oldState is the representative of
	 * oldStates equivalence class. This method can only be used if the
	 * minimization is finished.
	 */
	public Map<STATE, STATE> getOldState2newState() {
		return mResult.mOldState2newState;
	}
	
	// --- [end] helper methods and classes --- //
	
	// --- [start] important inner classes --- //
	
	/**
	 * The partition is the main object of the procedure. It contains and
	 * handles the equivalence classes and works as the resulting automaton.
	 */
	private class Partition {
		// equivalence classes
		private final Collection<EquivalenceClass> mEquivalenceClasses;
		// mapping 'state -> equivalence class'
		private final HashMap<STATE, EquivalenceClass> mState2EquivalenceClass;
		
		/**
		 * constructor
		 */
		public Partition() {
			mEquivalenceClasses = new LinkedList<EquivalenceClass>();
			mState2EquivalenceClass = new HashMap<STATE, EquivalenceClass>(
					computeHashCap(mOperand.size()));
		}
		
		/**
		 * This method adds an equivalence class (also to the work list) during
		 * the initialization phase.
		 *
		 * @param module the states in the equivalence class
		 */
		private void addEcInitialization(final Set<STATE> module) {
			final EquivalenceClass ec = new EquivalenceClass(module);
			mEquivalenceClasses.add(ec);
			for (final STATE state : module) {
				mState2EquivalenceClass.put(state, ec);
			}
		}
		
		/**
		 * This method adds an equivalence class to the partition that resulted
		 * from a split.
		 *
		 * @param parent the parent equivalence class
		 * @return the equivalence class
		 */
		private EquivalenceClass addEcSplit(final EquivalenceClass parent) {
			Set<STATE> newStates = parent.mIntersection;
			if (newStates.size() > parent.mStates.size()) {
				newStates = parent.mStates;
				parent.mStates = parent.mIntersection;
			}
			final EquivalenceClass ec = new EquivalenceClass(newStates, parent);
			mEquivalenceClasses.add(ec);
			for (final STATE state : ec.mStates) {
				mState2EquivalenceClass.put(state, ec);
			}
			return ec;
		}
		
		/**
		 * This method splits a state from its equivalence class during the
		 * internal and call split. The equivalence class is remembered.
		 * 
		 * @param state the state
		 * @param splitEcs the list of split equivalence classes
		 */
		private void splitState(final STATE state,
				final LinkedList<EquivalenceClass> splitEcs) {
			final EquivalenceClass ec = mState2EquivalenceClass.get(state);
			
			// first occurrence of the equivalence class, mark it
			if (ec.mIntersection.isEmpty()) {
				assert (!splitEcs.contains(ec));
				splitEcs.add(ec);
			} else {
				assert (splitEcs.contains(ec));
			}
			
			splitStateFast(state, ec);
		}
		
		/**
		 * This method splits a state for a given equivalence class without any
		 * further considerations.
		 * 
		 * @param state state
		 * @param ec equivalence class
		 */
		private void splitStateFast(final STATE state,
				final EquivalenceClass ec) {
			// move state to intersection set
			ec.mIntersection.add(state);
			
			// remove state from old set
			ec.mStates.remove(state);
		}
		
		/**
		 * This method finally splits the marked equivalence classes into two
		 * (for the internal and call split). The states have already been split
		 * in the equivalence class before. Only if there are states remaining
		 * the split is executed, otherwise the old equivalence class is
		 * restored.
		 * 
		 * @param states set of states to split
		 * @param letter pair (letter, state) used for splitting
		 * @return true iff a split occurred
		 */
		public boolean splitEquivalenceClasses(final Iterable<STATE> states,
				final Pair<LETTER, STATE> letter) {
			boolean splitOccurred = false;
			final LinkedList<EquivalenceClass> splitEcs =
					new LinkedList<EquivalenceClass>();
					
			// process splits
			for (final STATE state : states) {
				splitState(state, splitEcs);
			}
			
			// check and finalize splits
			for (final EquivalenceClass ec : splitEcs) {
				if ((letter != null) && (!ec.mStates.isEmpty())) {
					final STATE hier = letter.getSecond();
					// return split, also add neutral states
					final ArrayList<STATE> ecStates =
							new ArrayList<STATE>(ec.mStates);
					for (final STATE lin : ecStates) {
						if (!mDoubleDecker.isDoubleDecker(lin, hier)) {
							splitStateFast(lin, ec);
						}
					}
				}
				
				// split removed every state, restore equivalence class
				if (ec.mStates.isEmpty()) {
					ec.mStates = ec.mIntersection;
				} else {
					// do a split
					splitOccurred = true;
					addEcSplit(ec);
				}
				
				// reset equivalence class
				ec.reset();
			}
			
			return splitOccurred;
		}
		
		@Override
		public String toString() {
			final StringBuilder builder = new StringBuilder();
			builder.append("{");
			String append = "";
			for (final EquivalenceClass ec : mEquivalenceClasses) {
				builder.append(append);
				append = ", ";
				builder.append(ec);
			}
			builder.append("}");
			return builder.toString();
		}
	}
	
	/**
	 * An equivalence class contains states and knows whether it is in the work
	 * list.
	 * 
	 * Two equivalence class objects are equal iff they share the same pointer.
	 */
	private class EquivalenceClass {
		// unique ID (useful for hashCode and so for deterministic runs)
		private final int mId;
		// the states
		private Set<STATE> mStates;
		// intersection set that finally becomes a new equivalence class
		private Set<STATE> mIntersection;
		// status regarding incoming transitions
		private EIncomingStatus mIncomingInt;
		private EIncomingStatus mIncomingCall;
		private EIncomingStatus mIncomingRet;
		
		/**
		 * This is a partial constructor which is used for both initialization
		 * and splitting.
		 * 
		 * @param states the set of states for the equivalence class
		 * @param fromSplit flag currently ignored (necessary for overloading)
		 */
		private EquivalenceClass(final Set<STATE> states,
				final boolean fromSplit) {
			assert (! states.isEmpty());
			mId = ++mIds;
			mStates = states;
			reset();
		}
		
		/**
		 * This constructor is used for the initialization.
		 * 
		 * @param states the set of states for the equivalence class
		 */
		public EquivalenceClass(final Set<STATE> states) {
			this(states, false);
			mIncomingInt = EIncomingStatus.IN_WL;
			mIncomingCall = EIncomingStatus.IN_WL;
			mIncomingRet = EIncomingStatus.IN_WL;
			mWorkList.add(this);
		}
		
		/**
		 * This constructor is used during a split.
		 * 
		 * @param states the set of states for the equivalence class
		 * @param parent the parent equivalence class
		 */
		public EquivalenceClass(final Set<STATE> states,
				final EquivalenceClass parent) {
			this(states, true);
			boolean add = false;
			switch (parent.mIncomingInt) {
				case UNKNOWN:
				case IN_WL:
					mIncomingInt = EIncomingStatus.IN_WL;
					add = true;
					break;
				case NONE:
					mIncomingInt = EIncomingStatus.NONE;
			}
			switch (parent.mIncomingCall) {
				case UNKNOWN:
				case IN_WL:
					mIncomingCall = EIncomingStatus.IN_WL;
					add = true;
					break;
				case NONE:
					mIncomingCall = EIncomingStatus.NONE;
			}
			switch (parent.mIncomingRet) {
				case UNKNOWN:
				case IN_WL:
					mIncomingRet = EIncomingStatus.IN_WL;
					add = true;
					break;
				case NONE:
					mIncomingRet = EIncomingStatus.NONE;
					break;
			}
			if (add) {
				mWorkList.add(this);
			}
		}
		
		@Override
		public int hashCode() {
			return mId;
		}
		
		@Override
		public boolean equals(final Object obj) {
			if (this == obj) {
				return true;
			}
			if (obj == null) {
				return false;
			}
			assert (getClass() == obj.getClass());
			final EquivalenceClass other = (EquivalenceClass) obj;
			return (mId == other.mId);
		}
		
		/**
		 * This method resets the intersection set.
		 */
		private void reset() {
			mIntersection =
					new HashSet<STATE>(computeHashCap(mStates.size()));
		}
		
		@Override
		public String toString() {
			if (mStates == null) {
				return "negative equivalence class";
			}
			
			final StringBuilder builder = new StringBuilder();
			String append = "";
			
			builder.append("<[");
			builder.append(mIncomingInt);
			builder.append(",");
			builder.append(mIncomingCall);
			builder.append(",");
			builder.append(mIncomingRet);
			builder.append("], [");
			for (final STATE state : mStates) {
				builder.append(append);
				append = ", ";
				builder.append(state);
			}
			builder.append("], [");
			append = "";
			for (final STATE state : mIntersection) {
				builder.append(append);
				append = ", ";
				builder.append(state);
			}
			builder.append("]>");
			return builder.toString();
		}
		
		/**
		 * This method returns a short representation of the equivalence class
		 * with only the states. States in the intersection are not visible.
		 *
		 * @return a short string representation of the states
		 */
		public String toStringShort() {
			if (mStates == null) {
				return "negative equivalence class";
			}
			
			final StringBuilder builder = new StringBuilder();
			String append = "";
			
			builder.append("<");
			for (final STATE state : mStates) {
				builder.append(append);
				append = ", ";
				builder.append(state);
			}
			builder.append(">");
			
			return builder.toString();
		}
	}
	
	/**
	 * The work list has a priority queue of equivalence classes.
	 * 
	 * Since the size of the equivalence classes may change due to splitting, it
	 * is not guaranteed that the order is correct over time, but since it is a
	 * heuristic rather than a rule to prefer smaller splitters first, this is
	 * not considered bad and additional overhead is avoided.
	 */
	private abstract class AWorkList implements Iterator<EquivalenceClass> {
		protected final PriorityQueue<EquivalenceClass> mQueue;
		
		public AWorkList() {
			mQueue = new PriorityQueue<EquivalenceClass>(
					Math.max(mOperand.size(), 1),
					new Comparator<EquivalenceClass>() {
						@Override
						public int compare(final EquivalenceClass ec1,
								final EquivalenceClass ec2) {
							return ec1.mStates.size() - ec2.mStates.size();
						}
					});
		}
		
		/**
		 * This method adds an equivalence class to the work list. The caller
		 * asserts that the class is not already in the work list and must
		 * inform the equivalence class beforehand.
		 *
		 * @param ec the equivalence class
		 */
		public void add(final EquivalenceClass ec) {
			assert (!mQueue.contains(ec));
			mQueue.add(ec);
		}
		
		@Override
		public boolean hasNext() {
			return (!mQueue.isEmpty());
		}
		
		@Override
		public abstract EquivalenceClass next();
		
		@Override
		public void remove() {
			throw new UnsupportedOperationException(
					"Removing is not supported.");
		}
		
		@Override
		public String toString() {
			final StringBuilder builder = new StringBuilder();
			toStringHelper(builder);
			builder.append(">>");
			return builder.toString();
		}
		
		/**
		 * This method constructs most of the string representation.
		 *
		 * @param builder the string builder
		 */
		protected void toStringHelper(final StringBuilder builder) {
			builder.append("<<");
			String append = "";
			for (final EquivalenceClass ec : mQueue) {
				builder.append(append);
				append = ", ";
				builder.append(ec);
			}
		}
	}
	
	/**
	 * This class implements the work list.
	 */
	private class WorkList extends AWorkList {
		@Override
		public EquivalenceClass next() {
			final EquivalenceClass ec = mQueue.poll();
			return ec;
		}
		
		@Override
		public void add(final EquivalenceClass ec) {
			assert ((ec.mIncomingInt == EIncomingStatus.IN_WL) ||
					(ec.mIncomingCall == EIncomingStatus.IN_WL) ||
					(ec.mIncomingRet == EIncomingStatus.IN_WL));
			super.add(ec);
		}
	}
	
	/**
	 * This class temporarily works as the output automaton. The future idea is
	 * not to generate a real object but to simulate states and transitions with
	 * the equivalence class objects.
	 */
	public class ShrinkNwaResult
			implements INestedWordAutomatonSimple<LETTER, STATE> {
		private final Map<STATE, STATE> mOldState2newState;
		// old automaton
		private final INestedWordAutomaton<LETTER, STATE> mOldNwa;
		// states
		private final HashSet<STATE> mFinals;
		private final HashSet<STATE> mNonfinals;
		// initial states
		private final HashSet<STATE> mInitialStates;
		// transitions
		private final HashMap<STATE, HashSet<OutgoingInternalTransition<LETTER, STATE>>> mOutInt;
		private final HashMap<STATE, HashSet<OutgoingCallTransition<LETTER, STATE>>> mOutCall;
		private final HashMap<STATE, HashSet<OutgoingReturnTransition<LETTER, STATE>>> mOutRet;
		
		/**
		 * @param includeMapping true iff mapping old to new state is needed
		 */
		public ShrinkNwaResult(final boolean includeMapping) {
			mOldNwa = mOperand;
			mFinals = new HashSet<STATE>();
			mNonfinals = new HashSet<STATE>();
			mInitialStates = new HashSet<STATE>();
			mOldState2newState =
					includeMapping
							? new HashMap<STATE, STATE>(
									computeHashCap(mOldNwa.size()))
							: null;
							
			assert (mStateFactory != null);
			final HashMap<EquivalenceClass, STATE> ec2state =
					new HashMap<EquivalenceClass, STATE>(computeHashCap(
							mPartition.mEquivalenceClasses.size()));
							
			mOutInt =
					new HashMap<STATE, HashSet<OutgoingInternalTransition<LETTER, STATE>>>();
			mOutCall =
					new HashMap<STATE, HashSet<OutgoingCallTransition<LETTER, STATE>>>();
			mOutRet =
					new HashMap<STATE, HashSet<OutgoingReturnTransition<LETTER, STATE>>>();
					
			// states
			for (final EquivalenceClass ec : mPartition.mEquivalenceClasses) {
				final Set<STATE> ecStates = ec.mStates;
				
				// new state
				final STATE newState = mStateFactory.minimize(ecStates);
				ec2state.put(ec, newState);
				if (includeMapping) {
					for (final STATE oldState : ecStates) {
						mOldState2newState.put(oldState, newState);
					}
				}
				
				// states
				if (mOldNwa.isFinal(ecStates.iterator().next())) {
					mFinals.add(newState);
				} else {
					mNonfinals.add(newState);
				}
			}
			
			// initial states (efficiency assumption: there are only a few)
			for (final STATE init : mOldNwa.getInitialStates()) {
				mInitialStates.add(ec2state
						.get(mPartition.mState2EquivalenceClass.get(init)));
			}
			
			// preprocessing: ignore call and return loops for finite automata
			final boolean isNwa = (mOperand.getCallAlphabet().size() > 0);
			
			// transitions
			for (final EquivalenceClass ec : mPartition.mEquivalenceClasses) {
				final STATE newState = ec2state.get(ec);
				
				final STATE representative = ec.mStates.iterator().next();
				
				HashMap<LETTER, Set<STATE>> letter2succs =
						new HashMap<LETTER, Set<STATE>>();
						
				// internal transitions
				final HashSet<OutgoingInternalTransition<LETTER, STATE>> outInt =
						new HashSet<OutgoingInternalTransition<LETTER, STATE>>();
						
				for (final OutgoingInternalTransition<LETTER, STATE> edge : mOldNwa
						.internalSuccessors(representative)) {
					final LETTER letter = edge.getLetter();
					final STATE succ =
							ec2state.get(mPartition.mState2EquivalenceClass
									.get(edge.getSucc()));
					Set<STATE> succs = letter2succs.get(letter);
					boolean isNew;
					if (succs == null) {
						/*
						 * efficiency assumption: there is only one transition
						 * with the same letter (determinism)
						 */
						succs = Collections.singleton(succ);
						letter2succs.put(letter, succs);
						isNew = true;
					} else {
						/*
						 * If there is nondeterminism, replace the (immutable)
						 * singleton set by a usual HashSet.
						 */
						if (succs.size() == 1) {
							final STATE oldSucc = succs.iterator().next();
							succs = new HashSet<STATE>();
							succs.add(oldSucc);
						}
						isNew = succs.add(succ);
					}
					if (isNew) {
						final OutgoingInternalTransition<LETTER, STATE> newEdge =
								new OutgoingInternalTransition<LETTER, STATE>(
										letter, succ);
						outInt.add(newEdge);
					}
				}
				if (!outInt.isEmpty()) {
					mOutInt.put(newState, outInt);
				}
				
				if (isNwa) {
					letter2succs = new HashMap<LETTER, Set<STATE>>();
					
					// call transitions
					final HashSet<OutgoingCallTransition<LETTER, STATE>> outCall =
							new HashSet<OutgoingCallTransition<LETTER, STATE>>();
							
					for (final OutgoingCallTransition<LETTER, STATE> edge : mOldNwa
							.callSuccessors(representative)) {
						final LETTER letter = edge.getLetter();
						final STATE succ = ec2state
								.get(mPartition.mState2EquivalenceClass
										.get(edge.getSucc()));
						Set<STATE> succs = letter2succs.get(letter);
						boolean isNew;
						if (succs == null) {
							/*
							 * efficiency assumption: there is only one
							 * transition with the same letter (determinism)
							 */
							succs = Collections.singleton(succ);
							letter2succs.put(letter, succs);
							isNew = true;
						} else {
							/*
							 * If there is nondeterminism, replace the
							 * (immutable) singleton set by a usual HashSet.
							 */
							if (succs.size() == 1) {
								final STATE oldSucc = succs.iterator().next();
								succs = new HashSet<STATE>();
								succs.add(oldSucc);
							}
							isNew = succs.add(succ);
						}
						if (isNew) {
							final OutgoingCallTransition<LETTER, STATE> newEdge =
									new OutgoingCallTransition<LETTER, STATE>(
											letter, succ);
							outCall.add(newEdge);
						}
					}
					if (!outCall.isEmpty()) {
						mOutCall.put(newState, outCall);
					}
					
					letter2succs = null;
					
					/*
					 * return transitions NOTE: Return transitions need not be
					 * present everywhere, so each state must be visited.
					 */
					final HashSet<OutgoingReturnTransition<LETTER, STATE>> outRet =
							new HashSet<OutgoingReturnTransition<LETTER, STATE>>();
							
					final HashMap<LETTER, HashMap<STATE, HashSet<STATE>>> returns =
							new HashMap<LETTER, HashMap<STATE, HashSet<STATE>>>();
					for (final STATE state : ec.mStates) {
						for (final OutgoingReturnTransition<LETTER, STATE> edge : mOldNwa
								.returnSuccessors(state)) {
							final LETTER letter = edge.getLetter();
							HashMap<STATE, HashSet<STATE>> hier2succs =
									returns.get(letter);
							if (hier2succs == null) {
								hier2succs =
										new HashMap<STATE, HashSet<STATE>>();
								returns.put(letter, hier2succs);
							}
							final STATE hier = ec2state
									.get(mPartition.mState2EquivalenceClass
											.get(edge.getHierPred()));
							HashSet<STATE> succs = hier2succs.get(hier);
							if (succs == null) {
								succs = new HashSet<STATE>();
								hier2succs.put(hier, succs);
							}
							succs.add(ec2state
									.get(mPartition.mState2EquivalenceClass
											.get(edge.getSucc())));
						}
					}
					for (final Entry<LETTER, HashMap<STATE, HashSet<STATE>>> entry : returns
							.entrySet()) {
						for (final Entry<STATE, HashSet<STATE>> entry2 : entry
								.getValue().entrySet()) {
							for (final STATE succ : entry2.getValue()) {
								final OutgoingReturnTransition<LETTER, STATE> newEdge =
										new OutgoingReturnTransition<LETTER, STATE>(
												entry2.getKey(), entry.getKey(),
												succ);
								outRet.add(newEdge);
							}
						}
					}
					
					if (!outRet.isEmpty()) {
						mOutRet.put(newState, outRet);
					}
				}
			}
		}
		
		@Override
		public Set<LETTER> getAlphabet() {
			return mOldNwa.getAlphabet();
		}
		
		@Override
		public Set<LETTER> getInternalAlphabet() {
			return mOldNwa.getInternalAlphabet();
		}
		
		@Override
		public Set<LETTER> getCallAlphabet() {
			return mOldNwa.getCallAlphabet();
		}
		
		@Override
		public Set<LETTER> getReturnAlphabet() {
			return mOldNwa.getReturnAlphabet();
		}
		
		@Override
		public STATE getEmptyStackState() {
			return mOldNwa.getEmptyStackState();
		}
		
		@Override
		public StateFactory<STATE> getStateFactory() {
			return mOldNwa.getStateFactory();
		}
		
		@Override
		public String sizeInformation() {
			return size() + " states.";
		}
		
		@Override
		public int size() {
			return mFinals.size() + mNonfinals.size();
		}
		
		@Override
		public Collection<STATE> getInitialStates() {
			return mInitialStates;
		}
		
		@Override
		public boolean isInitial(final STATE state) {
			return mInitialStates.contains(state);
		}
		
		@Override
		public boolean isFinal(final STATE state) {
			return mFinals.contains(state);
		}
		
		@Override
		public Set<LETTER> lettersInternal(final STATE state) {
			final HashSet<OutgoingInternalTransition<LETTER, STATE>> set =
					mOutInt.get(state);
			if (set == null) {
				return Collections.emptySet();
			}
			
			final HashSet<LETTER> result = new HashSet<LETTER>();
			for (final OutgoingInternalTransition<LETTER, STATE> edge : set) {
				result.add(edge.getLetter());
			}
			return result;
		}
		
		@Override
		public Set<LETTER> lettersCall(final STATE state) {
			final HashSet<OutgoingCallTransition<LETTER, STATE>> set =
					mOutCall.get(state);
			if (set == null) {
				return Collections.emptySet();
			}
			
			final HashSet<LETTER> result = new HashSet<LETTER>();
			for (final OutgoingCallTransition<LETTER, STATE> edge : set) {
				result.add(edge.getLetter());
			}
			return result;
		}
		
		@Override
		public Set<LETTER> lettersReturn(final STATE state) {
			final HashSet<OutgoingReturnTransition<LETTER, STATE>> set =
					mOutRet.get(state);
			if (set == null) {
				return Collections.emptySet();
			}
			
			final HashSet<LETTER> result = new HashSet<LETTER>();
			for (final OutgoingReturnTransition<LETTER, STATE> edge : set) {
				result.add(edge.getLetter());
			}
			return result;
		}
		
		@Override
		public Iterable<OutgoingInternalTransition<LETTER, STATE>>
				internalSuccessors(final STATE state, final LETTER letter) {
			final HashSet<OutgoingInternalTransition<LETTER, STATE>> set =
					mOutInt.get(state);
			if (set == null) {
				return Collections.emptySet();
			}
			
			final HashSet<OutgoingInternalTransition<LETTER, STATE>> result =
					new HashSet<OutgoingInternalTransition<LETTER, STATE>>();
			for (final OutgoingInternalTransition<LETTER, STATE> edge : set) {
				if (edge.getLetter().equals(letter)) {
					result.add(edge);
				}
			}
			return result;
		}
		
		@Override
		public Iterable<OutgoingInternalTransition<LETTER, STATE>>
				internalSuccessors(final STATE state) {
			final HashSet<OutgoingInternalTransition<LETTER, STATE>> set =
					mOutInt.get(state);
			if (set == null) {
				return Collections.emptySet();
			}
			return set;
		}
		
		@Override
		public Iterable<OutgoingCallTransition<LETTER, STATE>>
				callSuccessors(final STATE state, final LETTER letter) {
			final HashSet<OutgoingCallTransition<LETTER, STATE>> set =
					mOutCall.get(state);
			if (set == null) {
				return Collections.emptySet();
			}
			
			final HashSet<OutgoingCallTransition<LETTER, STATE>> result =
					new HashSet<OutgoingCallTransition<LETTER, STATE>>();
			for (final OutgoingCallTransition<LETTER, STATE> edge : set) {
				if (edge.getLetter().equals(letter)) {
					result.add(edge);
				}
			}
			return result;
		}
		
		@Override
		public Iterable<OutgoingCallTransition<LETTER, STATE>>
				callSuccessors(final STATE state) {
			final HashSet<OutgoingCallTransition<LETTER, STATE>> set =
					mOutCall.get(state);
			if (set == null) {
				return Collections.emptySet();
			}
			return set;
		}
		
		@Override
		public Iterable<OutgoingReturnTransition<LETTER, STATE>>
				returnSuccessors(final STATE state, final STATE hier, final LETTER letter) {
			final HashSet<OutgoingReturnTransition<LETTER, STATE>> set =
					mOutRet.get(state);
			if (set == null) {
				return Collections.emptySet();
			}
			
			final HashSet<OutgoingReturnTransition<LETTER, STATE>> result =
					new HashSet<OutgoingReturnTransition<LETTER, STATE>>();
			for (final OutgoingReturnTransition<LETTER, STATE> edge : set) {
				if (edge.getLetter().equals(letter) &&
						edge.getHierPred().equals(hier)) {
					result.add(edge);
				}
			}
			return result;
		}
		
		@Override
		public Iterable<OutgoingReturnTransition<LETTER, STATE>>
				returnSuccessorsGivenHier(final STATE state, final STATE hier) {
			final HashSet<OutgoingReturnTransition<LETTER, STATE>> set =
					mOutRet.get(state);
			if (set == null) {
				return Collections.emptySet();
			}
			
			final HashSet<OutgoingReturnTransition<LETTER, STATE>> result =
					new HashSet<OutgoingReturnTransition<LETTER, STATE>>();
			for (final OutgoingReturnTransition<LETTER, STATE> edge : set) {
				if (edge.getHierPred().equals(hier)) {
					result.add(edge);
				}
			}
			return result;
		}
		
		@Override
		public String toString() {
			final StringBuilder builder = new StringBuilder();
			
			builder.append("{N[");
			String append = "";
			for (final STATE state : mNonfinals) {
				builder.append(append);
				append = ", ";
				builder.append(state);
				if (mInitialStates.contains(state)) {
					builder.append(" (I)");
				}
			}
			builder.append("], F[");
			append = "";
			for (final STATE state : mFinals) {
				builder.append(append);
				append = ", ";
				builder.append(state);
				if (mInitialStates.contains(state)) {
					builder.append(" (I)");
				}
			}
			builder.append("], ");
			builder.append(mOutInt.isEmpty() ? "-" : mOutInt);
			builder.append(", ");
			builder.append(mOutCall.isEmpty() ? "-" : mOutCall);
			builder.append(", ");
			builder.append(mOutRet.isEmpty() ? "-" : mOutRet);
			builder.append("}");
			
			return builder.toString();
		}
	}
	
	// --- [end] important inner classes --- //
	
	// --- [start] framework methods --- //
	
	@Override
	public INestedWordAutomatonSimple<LETTER, STATE> getResult() {
		assert mResult != null;
		return mResult;
	}
	
	// --- [end] framework methods --- //
}
