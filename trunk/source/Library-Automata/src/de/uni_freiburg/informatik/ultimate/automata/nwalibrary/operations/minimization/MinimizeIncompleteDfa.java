/*
 * Copyright (C) 2015 Christian Schilling (schillic@informatik.uni-freiburg.de)
 * Copyright (C) 2015 Daniel Tischner
 * Copyright (C) 2009-2015 University of Freiburg
 * 
 * This file is part of the ULTIMATE Automata Library.
 * 
 * The ULTIMATE Automata Library is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * The ULTIMATE Automata Library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 * 
 * You should have received a copy of the GNU Lesser General Public License
 * along with the ULTIMATE Automata Library. If not, see <http://www.gnu.org/licenses/>.
 * 
 * Additional permission under GNU GPL version 3 section 7:
 * If you modify the ULTIMATE Automata Library, or any covered work, by linking
 * or combining it with Eclipse RCP (or a modified version of Eclipse RCP), 
 * containing parts covered by the terms of the Eclipse Public License, the 
 * licensors of the ULTIMATE Automata Library grant you additional permission 
 * to convey the resulting work.
 */
package de.uni_freiburg.informatik.ultimate.automata.nwalibrary.operations.minimization;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.Map;
import java.util.Set;

import de.uni_freiburg.informatik.ultimate.automata.AutomataLibraryServices;
import de.uni_freiburg.informatik.ultimate.automata.IOperation;
import de.uni_freiburg.informatik.ultimate.automata.nwalibrary.INestedWordAutomaton;
import de.uni_freiburg.informatik.ultimate.automata.nwalibrary.NestedWordAutomaton;
import de.uni_freiburg.informatik.ultimate.automata.nwalibrary.StateFactory;
import de.uni_freiburg.informatik.ultimate.automata.nwalibrary.transitions.IncomingInternalTransition;
import de.uni_freiburg.informatik.ultimate.automata.nwalibrary.transitions.OutgoingInternalTransition;

/**
 * Utility class for minimizing incomplete DFAs (Deterministic Finite
 * Automaton). Uses a modification of the Hopcroft algorithm.<br/>
 * Runtime is in:<br/>
 * <b>O(m * log(n))</b> with usage of<br/>
 * <b>O(k + n + m)</b> space<br/>
 * where 'n' is the number of states, 'm' the number of edges and 'k' the size
 * of the alphabet.
 * 
 * @author Daniel Tischner
 *
 * @param <LETTER>
 *            Class of the letters from the automata
 * @param <STATE>
 *            Class of the states from the automata
 */
public final class MinimizeIncompleteDfa<LETTER, STATE>
		extends AMinimizeNwa<LETTER, STATE>
		implements IOperation<LETTER, STATE> {
	/**
	 * Initial amount of blocks.
	 */
	private static final int INITIAL_BLOCK_AMOUNT = 2;
	/**
	 * Next usable unique id for a block.
	 */
	private int mBlockId = 0;
	/**
	 * List of all blocks the automata currently has. (Also known as "Q").
	 */
	private final LinkedList<LinkedHashSet<Integer>> mBlocks =
			new LinkedList<LinkedHashSet<Integer>>();
	/**
	 * Mapping for block to a unique id.
	 */
	private final HashMap<LinkedHashSet<Integer>, Integer> mBlockToId;
	/**
	 * Mapping for a unique id to block.
	 */
	private final HashMap<Integer, LinkedHashSet<Integer>> mIdToBlock;
	/**
	 * Mapping for a unique id to state.
	 */
	private final HashMap<Integer, STATE> mIdToState;
	/**
	 * Mapping for a letter to unique id.
	 */
	private final HashMap<LETTER, Integer> mLetterToId;
	/**
	 * Resulting minimized automaton.
	 */
	private final NestedWordAutomaton<LETTER, STATE> mResult;
	/**
	 * Mapping for state to the block number where it is contained.
	 */
	private final HashMap<Integer, Integer> mStateToBlockId;
	/**
	 * Mapping for a state to unique id.
	 */
	private final HashMap<STATE, Integer> mStateToId;
	/**
	 * Mapping for state to incoming edges.
	 */
	private final HashMap<Integer, Iterable<
		IncomingInternalTransition<LETTER, STATE>>> mStateToIncomingEdges;
//	/**
//	 * Mapping for state to outgoing edges.
//	 * Christian: not used anymore
//	 */
//	private final HashMap<Integer, Iterable<
//		OutgoingInternalTransition<LETTER, STATE>>> stateToOutgoingEdges;
	
	// added by Christian
	private HashMap<STATE, STATE> mOldState2newState;
	
	/**
	 * Minimizes a given incomplete DFAs (Deterministic Finite Automaton).<br/>
	 * Runtime is in:<br/>
	 * <b>O(m * log(n))</b> with usage of<br/>
	 * <b>O(k + n + m)</b> space<br/>
	 * where 'n' is the number of states, 'm' the number of edges and 'k' the
	 * size of the alphabet.
	 */
	public MinimizeIncompleteDfa(
			final AutomataLibraryServices services,
			final INestedWordAutomaton<LETTER, STATE> operand,
			final StateFactory<STATE> stateFactory,
			final Collection<Set<STATE>> initialPartition,
			final boolean addMapping) {
		super(services, stateFactory, "minimizeIncompleteDFA", operand);
		
		// added by Christian
		if ((operand.getCallAlphabet().size() > 0) ||
				(operand.getReturnAlphabet().size() > 0)) {
			throw new UnsupportedOperationException(
				"This class only supports minimization of finite automata.");
		}
		if (addMapping) {
			this.mOldState2newState = null;
		} else {
			mOldState2newState = new HashMap<STATE, STATE>();
		}
		
		mBlockToId = new HashMap<LinkedHashSet<Integer>, Integer>(
				INITIAL_BLOCK_AMOUNT);
		mIdToBlock = new HashMap<Integer, LinkedHashSet<Integer>>(
				INITIAL_BLOCK_AMOUNT);
		final int stateAmount = operand.getStates().size();
		mIdToState = new HashMap<Integer, STATE>(stateAmount);
		mStateToId = new HashMap<STATE, Integer>(stateAmount);
		mStateToBlockId = new HashMap<Integer, Integer>(stateAmount);
		mStateToIncomingEdges =
				new HashMap<Integer,
				Iterable<IncomingInternalTransition<LETTER, STATE>>>(
				stateAmount);
		// Christian: not used anymore
//		stateToOutgoingEdges = new HashMap<Integer,
//				Iterable<OutgoingInternalTransition<LETTER, STATE>>>(
//				stateAmount);
		final int letterAmount = operand.getInternalAlphabet().size();
		mLetterToId = new HashMap<LETTER, Integer>(letterAmount);

		init(stateAmount, letterAmount);
		
		mResult = minimizeICDFA(mOperand, initialPartition);
		mLogger.info(exitMessage());
	}

	/**
	 * Minimizes a given incomplete DFAs (Deterministic Finite Automaton).<br/>
	 * Runtime is in:<br/>
	 * <b>O(m * log(n))</b> with usage of<br/>
	 * <b>O(k + n + m)</b> space<br/>
	 * where 'n' is the number of states, 'm' the number of edges and 'k' the
	 * size of the alphabet.
	 * 
	 * @param services
	 *            Service provider
	 * @param operand
	 *            Automaton to minimize
	 */
	public MinimizeIncompleteDfa(final AutomataLibraryServices services,
			final INestedWordAutomaton<LETTER, STATE> operand) {
		this(services, operand, operand.getStateFactory(), null, false);
	}

	/**
	 * Builds the minimized automaton using the block
	 * representation of all nodes.
	 * 
	 * @return The minimized automaton
	 */
	private NestedWordAutomaton<LETTER, STATE> buildMinimizedAutomaton() {
		final NestedWordAutomaton<LETTER, STATE> result =
				new NestedWordAutomaton<LETTER, STATE>(mServices,
						mOperand.getInternalAlphabet(),
						mOperand.getCallAlphabet(),
						mOperand.getReturnAlphabet(),
						mStateFactory);
		
		// Select a representative state for every block
		final LinkedList<STATE> representatives = new LinkedList<STATE>();
		final HashMap<Integer, STATE> blockToNewState =
				new HashMap<Integer, STATE>();
//		HashMap<Integer, STATE> representativeIdToNewState =
//				new HashMap<Integer, STATE>();
		
		// Christian: edited for proper state factory usage
		final HashSet<Integer> initialBlocks = new HashSet<Integer>();
		for (final STATE initialState : mOperand.getInitialStates()) {
			initialBlocks.add(mStateToBlockId.get(mStateToId.get(initialState)));
		}
		for (final LinkedHashSet<Integer> block : mBlocks) {
			if (block == null || block.isEmpty()) {
				continue;
			}
			
			final ArrayList<STATE> allStates = new ArrayList<STATE>(block.size());
			final Iterator<Integer> blockIt = block.iterator();
			final int representativeId = blockIt.next();
			final int blockId = mBlockToId.get(block);
			final STATE representative = mIdToState.get(representativeId);
			representatives.add(representative);
			allStates.add(representative);
			
			while (blockIt.hasNext()) {
				allStates.add(mIdToState.get(blockIt.next()));
			}
			
			final STATE newState = mStateFactory.minimize(allStates);
			blockToNewState.put(blockId, newState);
			result.addState(initialBlocks.contains(blockId),
					mOperand.isFinal(representative), newState);
			
			// update mapping 'old state -> new state'
			if (mOldState2newState != null) {
				for (final STATE oldState : allStates) {
					mOldState2newState.put(oldState, newState);
				}
			}
		}
		//Add adjusted outgoing transitions of every representative
		for (final STATE oldSrcState : representatives) {
			for (final OutgoingInternalTransition<LETTER, STATE> trans :
				mOperand.internalSuccessors(oldSrcState)) {
				//Redirect the destination to the representative of the block
				final int oldSrc = mStateToId.get(oldSrcState);
				final int oldDest = mStateToId.get(trans.getSucc());
				
				final STATE predState = blockToNewState.get(
						mStateToBlockId.get(oldSrc));
				final LETTER letter = trans.getLetter();
				final STATE succState = blockToNewState.get(
						mStateToBlockId.get(oldDest));
				result.addInternalTransition(predState, letter, succState);
			}
		}
		
//		for (LinkedHashSet<Integer> block : blocks) {
//			if (block == null || block.isEmpty()) {
//				continue;
//			}
//			int stateId = block.iterator().next();
//			STATE state = idToState.get(stateId);
//			representatives.add(stateId);
//			blockToRepresentatives.put(stateToBlockId.get(stateId), stateId);
//			
//			// Determine if the block contains an initial state
//			// If yes, the block also must be initial
//			Collection<STATE> initialStates = moperand.getInitialStates();
//			boolean isBlockInitial = moperand.isInitial(state);
//			// If representative is not initial, check if there are
//			// other states that are
//			if (!isBlockInitial) {
//				for (STATE initialState : initialStates) {
//					if (block.contains(stateToId.get(initialState))) {
//						isBlockInitial = true;
//						break;
//					}
//				}
//			}
//			
//			result.addState(isBlockInitial, moperand.isFinal(state), state);
//		}
//		//Add adjusted outgoing transitions of every representative
//        for (int state : representatives) {
//            for (OutgoingInternalTransition<LETTER, STATE> trans :
//                    stateToOutgoingEdges.get(state)) {
//                //Redirect the destination to the representative of the block
//                int oldDest = stateToId.get(trans.getSucc());
//                int destRepresentative = blockToRepresentatives.get(
//                                stateToBlockId.get(oldDest));
//                
//                STATE predState = idToState.get(state);
//                LETTER letter = trans.getLetter();
//                STATE succState = idToState.get(destRepresentative);
//                result.addInternalTransition(predState, letter, succState);
//            }
//        }
		
		return result;
	}

	@Override
	public INestedWordAutomaton<LETTER, STATE> getResult() {
		return mResult;
	}

	/**
	 * Gets a usable unique id for a block.
	 * 
	 * @return Usable unique id for a block
	 */
	private int getUniqueBlocKId() {
		final int curId = mBlockId;
		mBlockId++;
		return curId;
	}

	/**
	 * Maps state and letter to id and state to edge structures.
	 * 
	 * @param stateAmount
	 *            amount of states
	 * @param letterAmount
	 *            amount of letters
	 * 
	 */
	private void init(final int stateAmount, final int letterAmount) {
		int maxAmount = stateAmount;
		if (stateAmount < letterAmount) {
			maxAmount = letterAmount;
		}
		final Iterator<STATE> states = mOperand.getStates().iterator();
		final Iterator<LETTER> letters = mOperand.getInternalAlphabet().iterator();
		
		for (int i = 0; i < maxAmount; i++) {
			if (states.hasNext()) {
				final STATE state = states.next();
				
				mIdToState.put(i, state);
				mStateToId.put(state, i);
				mStateToIncomingEdges.put(i,
						mOperand.internalPredecessors(state));
				// Christian: not needed anymore
//				stateToOutgoingEdges
//						.put(i, moperand.internalSuccessors(state));
			}
			if (letters.hasNext()) {
				final LETTER letter = letters.next();
				
				mLetterToId.put(letter, i);
			}
		}
	}
	
	/**
	 * Minimizes a given incomplete DFAs (Deterministic Finite Automaton).<br/>
	 * Runtime is in:<br/>
	 * <b>O(m * log(n))</b> with usage of<br/>
	 * <b>O(k + n + m)</b> space<br/>
	 * where 'n' is the number of states, 'm' the number of edges and 'k' the
	 * size of the alphabet.
	 * 
	 * @param incdfa
	 *            Automaton to minimize
	 * @param initialPartition
	 *            Initial partition of states 
	 * @return Minimized automaton
	 */
	private NestedWordAutomaton<LETTER, STATE> minimizeICDFA(
			final INestedWordAutomaton<LETTER, STATE> incdfa,
			final Collection<Set<STATE>> initialPartition) {
		// Initial blocks
		final LinkedList<Integer> finalStates = new LinkedList<Integer>();
		final LinkedList<Integer> otherStates = new LinkedList<Integer>();
		final Set<STATE> allStates = mStateToId.keySet();
		
		// List also known as "L"
		final LinkedHashSet<LinkedHashSet<Integer>> splitCandidates =
				new LinkedHashSet<LinkedHashSet<Integer>>();
		
		if (initialPartition == null) {
			for (final STATE state : allStates) {
				if (incdfa.isFinal(state)) {
					finalStates.add(mStateToId.get(state));
				} else {
					otherStates.add(mStateToId.get(state));
				}
			}
			//Add block only if there are final states
			int finalStatesBlockId = -1;
			final boolean existsFinal = finalStates != null && !finalStates.isEmpty();
			LinkedHashSet<Integer> finalStatesBlock = null;
			if (existsFinal) {
				finalStatesBlockId = getUniqueBlocKId();
				finalStatesBlock = new LinkedHashSet<Integer>(
						finalStates);
				mBlockToId.put(finalStatesBlock, finalStatesBlockId);
				mIdToBlock.put(finalStatesBlockId, finalStatesBlock);
			}
			//Add block only if there are remaining states
			int otherStatesBlockId = -1;
			final boolean existsOther = otherStates != null && !otherStates.isEmpty();
			LinkedHashSet<Integer> otherStatesBlock = null;
			if (existsOther) {
				otherStatesBlockId = getUniqueBlocKId();
				otherStatesBlock = new LinkedHashSet<Integer>(
						otherStates);
				mBlockToId.put(otherStatesBlock, otherStatesBlockId);
				mIdToBlock.put(otherStatesBlockId, otherStatesBlock);
			}
	
			for (final STATE state : allStates) {
				if (incdfa.isFinal(state)) {
					mStateToBlockId.put(mStateToId.get(state), finalStatesBlockId);
				} else {
					mStateToBlockId.put(mStateToId.get(state), otherStatesBlockId);
				}
			}
			if (existsFinal) {
				mBlocks.add(finalStatesBlock);
			}
			if (existsOther) {
				mBlocks.add(otherStatesBlock);
			}

			// Initial split candidates
			if (existsFinal) {
				splitCandidates.add(finalStatesBlock);
			}
			if (existsOther) {
				splitCandidates.add(otherStatesBlock);
			}
		} else {
			// Christian: added this case
			for (final Set<STATE> block : initialPartition) {
				final LinkedList<Integer> newBlockStates = new LinkedList<Integer>();
				final int blockId = getUniqueBlocKId();
				for (final STATE state : block) {
					final int stateId = mStateToId.get(state);
					newBlockStates.add(stateId);
					mStateToBlockId.put(stateId, blockId);
				}
				final LinkedHashSet<Integer> newBlock = new LinkedHashSet<Integer>(
						newBlockStates);
				mBlockToId.put(newBlock, blockId);
				mIdToBlock.put(blockId, newBlock);
				splitCandidates.add(newBlock);
			}
		}
		
		// Split blocks until there is no candidate left
		while (!splitCandidates.isEmpty()) {
			Iterator<LinkedHashSet<Integer>> splitCandidatesIter =
					splitCandidates.iterator();
			LinkedHashSet<Integer> splitter = splitCandidatesIter.next();

			// If splitter block was deleted during a previous split, skip it
			boolean noElementWithContentLeft = false;
			while (splitter == null || mBlockToId.get(splitter) == null) {
				if (splitCandidatesIter.hasNext()) {
					splitCandidates.remove(splitter);
					splitCandidatesIter = splitCandidates.iterator();
					splitter = splitCandidatesIter.next();
				} else {
					noElementWithContentLeft = true;
					break;
				}
			}
			// If there is no element left that has content, break out
			if (noElementWithContentLeft) {
				break;
			}

			splitCandidates.remove(splitter);
			
			final LinkedList<LinkedHashSet<Integer>> splitCandidatesToAppend =
					split(splitter, incdfa.getInternalAlphabet().size());
			
			splitCandidates.addAll(splitCandidatesToAppend);
		}
		
		return buildMinimizedAutomaton();
	}

	/**
	 * Splits blocks in order to find blocks that can be left out for
	 * minimizing.
	 * 
	 * @param splitter
	 *            Splitter block
	 * @param letterAmount
	 *            Amount of letters the automaton has,
	 *            i.e. the size of the alphabet
	 * @return List of blocks to append to list of split candidates
	 */
	private LinkedList<LinkedHashSet<Integer>> split(
			final LinkedHashSet<Integer> splitter,
			final int letterAmount) {
		// Initialize needed structures

		// Represents the set of letters that belong to edges incoming in the
		// splitter block. (Also known as "l").
		final LinkedList<Integer> letterList = new LinkedList<Integer>();
		// Represents a set of sets that contain all the, splitter block,
		// incoming states, accessible by the letter of the incoming edge.
		// (Also known as "l(a)").
		final HashMap<Integer, LinkedList<Integer>> stateListByLetter =
				new HashMap<Integer, LinkedList<Integer>>();
		// Signatures of the states.
		final HashMap<Integer, LinkedList<Integer>> signatures =
				new HashMap<Integer, LinkedList<Integer>>();
		// Contains states that are used in splitting procedure.
		// (Also known as "s").
		final LinkedList<Integer> splitStates = new LinkedList<Integer>();
		// Numbers of blocks that are used in splitting procedure.
		// (Also known as "l1").
		final LinkedList<Integer> splitBlockNumbers = new LinkedList<Integer>();
		// Maps block numbers with respective states. (Also known as "t_b[i]").
		final HashMap<Integer, LinkedList<Integer>> blockStateMap =
				new HashMap<Integer, LinkedList<Integer>>();
		// Contains letters that are used in splitting procedure.
		// (Also known as "l2").
		final LinkedList<Integer> splitLetters = new LinkedList<Integer>();
		// Maps letters with respective states that are used in splitting
		// procedure.
		// (Also known as "t" or "t[a]").
		final HashMap<Integer, LinkedList<Integer>> splitStatesOfLetter =
				new HashMap<Integer, LinkedList<Integer>>();
		
		// Step 1
		// Iterate over all states in the splitter block
		// and setup some data structures
		for (final int stateInSplitter : splitter) {
			final Iterator<IncomingInternalTransition<LETTER, STATE>> incomingTransitions =
					mStateToIncomingEdges.get(stateInSplitter).iterator();

			while (incomingTransitions.hasNext()) {
				final IncomingInternalTransition<LETTER, STATE> incomingTrans =
						incomingTransitions.next();
				final int incomingState = mStateToId.get(incomingTrans.getPred());
				final int incomingLetter = mLetterToId.get(incomingTrans.getLetter());

				// Incoming edges, accessible by incoming letter
				if (!stateListByLetter.containsKey(incomingLetter)) {
					stateListByLetter.put(incomingLetter,
							new LinkedList<Integer>());
					// List of incoming letters (add letters only once)
					letterList.add(incomingLetter);
				}
				// Add incoming state to its letter list
				final LinkedList<Integer> statesOfLetter = stateListByLetter
						.get(incomingLetter);
				statesOfLetter.add(incomingState);
				stateListByLetter.put(incomingLetter, statesOfLetter);
			}
		}
		
		// Step 2
		// Scan the letterList and update signatures
		int maxSignatureSize = 0;
		for (final Integer letter : letterList) {
			for (final Integer state : stateListByLetter.get(letter)) {
				if (!signatures.containsKey(state)) {
					signatures.put(state, new LinkedList<Integer>());
					// Remember states that have a signature
					splitStates.add(state);
				}
				// Add letter to states signature
				final LinkedList<Integer> signature = signatures.get(state);
				signature.add(letter);
				signatures.put(state, signature);

				// Track maximal signature size
				if (signature.size() > maxSignatureSize) {
					maxSignatureSize = signature.size();
				}
			}
		}
		stateListByLetter.clear();
		letterList.clear();
		
		// Step 3
		// Discriminate the states
		for (final Integer state : splitStates) {
			final int blockNumber = mStateToBlockId.get(state);
			if (!blockStateMap.containsKey(blockNumber)) {
				blockStateMap.put(blockNumber, new LinkedList<Integer>());
				// Remember blocks that are used
				splitBlockNumbers.add(blockNumber);
			}
			final LinkedList<Integer> statesOfBlock = blockStateMap.get(blockNumber);
			statesOfBlock.add(state);
			blockStateMap.put(blockNumber, statesOfBlock);
		}
		
		splitStates.clear();
		for (final int blockNumber : splitBlockNumbers) {
			splitStates.addAll(blockStateMap.get(blockNumber));
		}
		
		blockStateMap.clear();
		//Keep references to iterator alive.
		final HashMap<Integer, Iterator<Integer>> signaturesIter =
				new HashMap<Integer, Iterator<Integer>>();
		// Iterate over all signature elements
		for (int j = 0; j < maxSignatureSize; j++) {
			for (final Integer state : splitStates) {
				
				final LinkedList<Integer> curSignature = signatures.get(state);
				//Use iterator for fast sequential access
				Iterator<Integer> curSignatureIter = null;
				if (!signaturesIter.containsKey(state)) {
					curSignatureIter = curSignature.iterator();
					signaturesIter.put(state, curSignatureIter);
				} else {
					curSignatureIter = signaturesIter.get(state);
				}

				// Skip this position for the letter because it
				// is not that long
				if (!curSignatureIter.hasNext()) {
					continue;
				}
				
				final Integer curSigLetter = curSignatureIter.next();

				// Add state to the state list of this letter
				if (!splitStatesOfLetter.containsKey(curSigLetter)) {
					splitStatesOfLetter.put(curSigLetter,
							new LinkedList<Integer>());
					// Remember letters that are used
					splitLetters.add(curSigLetter);
				}
				final LinkedList<Integer> statesOfLetter = splitStatesOfLetter
						.get(curSigLetter);
				statesOfLetter.add(state);
				splitStatesOfLetter.put(curSigLetter, statesOfLetter);
			}

			// Clear and update the split states list
			splitStates.clear();
			for (final Integer letter : splitLetters) {
				splitStates.addAll(splitStatesOfLetter.get(letter));
			}
		}
		splitLetters.clear();
		
		// Step 4
		// Split the blocks

		// Change the format of the split information into a better usable
		// where the content is separated by blocks.
		// Also remove duplicate content by using a set.
		final LinkedHashMap<Integer, LinkedHashSet<Integer>> splitStatesBlockWrapper =
				new LinkedHashMap<Integer, LinkedHashSet<Integer>>(mBlocks.size());
		LinkedHashSet<Integer> curBlockContent = new LinkedHashSet<Integer>();
		int lastBlockNumber = -1;
		int curBlockNumber = -1;
		for (final int state : splitStates) {
			curBlockNumber = mStateToBlockId.get(state);
			// If next block begins save old content and create a new list
			if (curBlockNumber != lastBlockNumber) {
				if (!curBlockContent.isEmpty()) {
					// If block was not used before put the list in as new
					if (splitStatesBlockWrapper.get(lastBlockNumber) == null) {
						splitStatesBlockWrapper.put(lastBlockNumber,
								curBlockContent);
					} else {
						// If the block was used before update the
						// old block and put it back in
						final LinkedHashSet<Integer> oldBlockContent =
								splitStatesBlockWrapper.get(lastBlockNumber);
						oldBlockContent.addAll(curBlockContent);
						splitStatesBlockWrapper.put(lastBlockNumber,
								oldBlockContent);
					}
				}
				curBlockContent = new LinkedHashSet<Integer>();
			}
			curBlockContent.add(state);
			lastBlockNumber = curBlockNumber;
		}
		//Handle last remaining element and put the content also in
		if (!curBlockContent.isEmpty()) {
			if (splitStatesBlockWrapper.get(curBlockNumber) == null) {
				splitStatesBlockWrapper.put(curBlockNumber,
						curBlockContent);
			} else {
				// If the block was used before update the
				// old block and put it back in
				final LinkedHashSet<Integer> oldBlockContent =
						splitStatesBlockWrapper.get(curBlockNumber);
				oldBlockContent.addAll(curBlockContent);
				splitStatesBlockWrapper.put(curBlockNumber,
						oldBlockContent);
			}
		}
		curBlockContent = null;
		// splitStatesBlockWrapper now contains split
		// information once per block

		// Save blockNumber of current splitter (before it gets removed)
		final int splitterBlockNumber = mBlockToId.get(splitter);
		final LinkedList<LinkedHashSet<Integer>> splitCandidatesToAppend =
				new LinkedList<LinkedHashSet<Integer>>();

		// Iterate over block content and determine splittings
		for (final LinkedHashSet<Integer> blockContent
				: splitStatesBlockWrapper.values()) {
			// Setup splittings
			final LinkedList<LinkedHashSet<Integer>> splittings =
					new LinkedList<LinkedHashSet<Integer>>();
			LinkedHashSet<Integer> curSplit = new LinkedHashSet<Integer>();
			LinkedList<Integer> lastSignature = null;
			for (final int state : blockContent) {
				final LinkedList<Integer> curSignature = signatures.get(state);
				// If next state has a different signature
				// save old content and create a new splitting.
				if (!curSignature.equals(lastSignature)) {
					if (!curSplit.isEmpty()) {
						splittings.add(curSplit);
					}
					curSplit = new LinkedHashSet<Integer>();
				}
				curSplit.add(state);
				lastSignature = curSignature;
			}
			splittings.add(curSplit);
			curSplit = null;

			// If there are missing states also add them as separate split
			LinkedHashSet<Integer> originalBlock = mIdToBlock.get(mStateToBlockId
					.get(blockContent.iterator().next()));
			if (!blockContent.equals(originalBlock)) {
				final HashSet<Integer> missingStates = new HashSet<Integer>();
				for (final int state : originalBlock) {
					if (!blockContent.contains(state)) {
						missingStates.add(state);
					}
				}
				if (!missingStates.isEmpty()) {
					splittings.add(new LinkedHashSet<Integer>(missingStates));
				}
			}

			// If there are more than one set a splits must be done
			if (splittings.size() > 1) {
				
				// Remove old block
				final int oldBlockId = mBlockToId.get(originalBlock);
				mIdToBlock.remove(oldBlockId);
				mBlockToId.remove(originalBlock);
				mBlocks.remove(originalBlock);
				originalBlock = null;

				// Track maximal size of split parts to not add the
				// biggest part as split candidate
				final int maxSplitPartSize = -1;
				LinkedHashSet<Integer> biggestSplitPart = null;

				// Create new blocks
				for (final LinkedHashSet<Integer> splitBlockPart : splittings) {
					final int nextBlockId = getUniqueBlocKId();
					mIdToBlock.put(nextBlockId, splitBlockPart);
					mBlockToId.put(splitBlockPart, nextBlockId);
					mBlocks.add(splitBlockPart);
					for (final int state : splitBlockPart) {
						mStateToBlockId.put(state, nextBlockId);
					}

					// Append block to candidate list
					splitCandidatesToAppend.add(splitBlockPart);
					// Update maximal split part size
					if (splitBlockPart.size() > maxSplitPartSize) {
						biggestSplitPart = splitBlockPart;
					}
				}

				// Remove biggest split part if splitter got split
				if (oldBlockId == splitterBlockNumber) {
					splitCandidatesToAppend.remove(biggestSplitPart);
				}
			}
		}
		return splitCandidatesToAppend;
	}
	
	/**
	 * Returns a Map from states of the input automaton to states of the output
	 * automaton. The image of a state oldState is the representative of 
	 * oldStates equivalence class.
	 * This method can only be used if the minimization is finished.
	 */
	public Map<STATE,STATE> getOldState2newState() {
		return mOldState2newState;
	}
}
