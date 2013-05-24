package de.uni_freiburg.informatik.ultimate.automata.nwalibrary.reachableStatesAutomaton;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.log4j.Logger;

import de.uni_freiburg.informatik.ultimate.automata.Activator;
import de.uni_freiburg.informatik.ultimate.automata.AtsDefinitionPrinter;
import de.uni_freiburg.informatik.ultimate.automata.OperationCanceledException;
import de.uni_freiburg.informatik.ultimate.automata.ResultChecker;
import de.uni_freiburg.informatik.ultimate.automata.Word;
import de.uni_freiburg.informatik.ultimate.automata.nwalibrary.DoubleDecker;
import de.uni_freiburg.informatik.ultimate.automata.nwalibrary.IDoubleDeckerAutomaton;
import de.uni_freiburg.informatik.ultimate.automata.nwalibrary.INestedWordAutomaton;
import de.uni_freiburg.informatik.ultimate.automata.nwalibrary.INestedWordAutomatonOldApi;
import de.uni_freiburg.informatik.ultimate.automata.nwalibrary.INestedWordAutomatonSimple;
import de.uni_freiburg.informatik.ultimate.automata.nwalibrary.IncomingCallTransition;
import de.uni_freiburg.informatik.ultimate.automata.nwalibrary.IncomingInternalTransition;
import de.uni_freiburg.informatik.ultimate.automata.nwalibrary.IncomingReturnTransition;
import de.uni_freiburg.informatik.ultimate.automata.nwalibrary.OutgoingCallTransition;
import de.uni_freiburg.informatik.ultimate.automata.nwalibrary.OutgoingInternalTransition;
import de.uni_freiburg.informatik.ultimate.automata.nwalibrary.OutgoingReturnTransition;
import de.uni_freiburg.informatik.ultimate.automata.nwalibrary.StateFactory;
import de.uni_freiburg.informatik.ultimate.automata.nwalibrary.SummaryReturnTransition;
import de.uni_freiburg.informatik.ultimate.automata.nwalibrary.TransitionConsitenceCheck;
import de.uni_freiburg.informatik.ultimate.core.api.UltimateServices;

public class NestedWordAutomatonReachableStates<LETTER,STATE> implements INestedWordAutomatonOldApi<LETTER,STATE>, INestedWordAutomaton<LETTER,STATE>, IDoubleDeckerAutomaton<LETTER, STATE> {

	private static Logger s_Logger = UltimateServices.getInstance().getLogger(Activator.PLUGIN_ID);
	
	private final INestedWordAutomatonSimple<LETTER,STATE> m_Operand;
	
	private final Set<LETTER> m_InternalAlphabet;
	private final Set<LETTER> m_CallAlphabet;
	private final Set<LETTER> m_ReturnAlphabet;
	
	protected final StateFactory<STATE> m_StateFactory;
	
	private final Set<STATE> m_initialStates = new HashSet<STATE>();
	private final Set<STATE> m_finalStates = new HashSet<STATE>();
	
	private final Map<STATE,StateContainer<LETTER,STATE>> m_States = new HashMap<STATE,StateContainer<LETTER,STATE>>();
	private final Map<STATE,Entry<LETTER,STATE>> m_State2Entry = new HashMap<STATE,Entry<LETTER,STATE>>();
	
	public static enum ReachProp { REACHABLE, NODEADEND, LIVE };
	
	private final Set<STATE> m_initialStatesAfterDeadEndRemoval = new HashSet<STATE>();
	private final Set<STATE> m_StatesAfterDeadEndRemoval = new HashSet<STATE>();
	
	private List<CommonEntriesComponent<LETTER,STATE>> m_AllCECs = new ArrayList<CommonEntriesComponent<LETTER,STATE>>();
	

	/**
	 * Set of return transitions LinPREs x HierPREs x LETTERs x SUCCs stored as 
	 * map HierPREs -> LETTERs -> LinPREs -> SUCCs
	 * 
	 */
	private Map<STATE,Map<LETTER,Map<STATE,Set<STATE>>>> m_ReturnSummary =
			new HashMap<STATE,Map<LETTER,Map<STATE,Set<STATE>>>>();

	
	


	private Map<StateContainer<LETTER,STATE>,Set<StateContainer<LETTER,STATE>>> m_Summaries = new HashMap<StateContainer<LETTER,STATE>,Set<StateContainer<LETTER,STATE>>>();
	

	
	private void addSummary(StateContainer<LETTER,STATE> callPred, StateContainer<LETTER,STATE> returnSucc) {
		Set<StateContainer<LETTER,STATE>> returnSuccs = m_Summaries.get(callPred);
		if (returnSuccs == null) {
			returnSuccs = new HashSet<StateContainer<LETTER,STATE>>();
			m_Summaries.put(callPred, returnSuccs);
		}
		returnSuccs.add(returnSucc);
	}
	
	public NestedWordAutomatonReachableStates(INestedWordAutomatonSimple<LETTER,STATE> operand) throws OperationCanceledException {
		this.m_Operand = operand;
		m_InternalAlphabet = operand.getInternalAlphabet();
		m_CallAlphabet = operand.getCallAlphabet();
		m_ReturnAlphabet = operand.getReturnAlphabet();
		m_StateFactory = operand.getStateFactory();
		try {
			new ReachableStatesComputation();
			new DeadEndComputation();
			s_Logger.info(componentInformation());
			s_Logger.info(stateContainerInformation());
			assert (new TransitionConsitenceCheck<LETTER, STATE>(this)).consistentForAll();

			assert (checkTransitionsReturnedConsistent());
			assert (allStatesAreInTheirCec());
			assert (cecSumConsistent());
			for (CommonEntriesComponent<LETTER,STATE> cec : m_AllCECs) {
				assert (occuringStatesAreConsistent(cec));
				if (cec.m_Size > 0) {
					assert (downStatesConsistentwithEntriesDownStates(cec));
				}
			}
			
		} catch (Error e) {
			String message = "// Problem with  removeUnreachable";
			ResultChecker.writeToFileIfPreferred("FailedremoveUnreachable",
					message, operand);
			throw e;
		} catch (RuntimeException e) {
			String message = "// Problem with  removeUnreachable";
			ResultChecker.writeToFileIfPreferred("FailedremoveUnreachable",
					message, operand);
			throw e;
		}
	}
	
	private String stateContainerInformation() {
		int inMap = 0;
		int outMap = 0;
		for(STATE state : m_States.keySet()) {
			StateContainer<LETTER, STATE> cont = m_States.get(state);
			if (cont instanceof StateContainerFieldAndMap) {
				if (((StateContainerFieldAndMap) cont).mapModeIncoming()) {
					inMap++;
				}
				if (((StateContainerFieldAndMap) cont).mapModeOutgoing()) {
					outMap++;
				}
			}
		}
		return m_States.size() + " StateContainers " + inMap + " in inMapMode" + outMap + " in outMapMode";
	}
	
	private String componentInformation() {
		int withoutStates = 0;
		Map<Set<Entry<LETTER,STATE>>, Integer> occurrence = new HashMap<Set<Entry<LETTER,STATE>>, Integer>();
		for (CommonEntriesComponent<LETTER,STATE> cec : m_AllCECs) {
			Set<Entry<LETTER,STATE>> entries = cec.getEntries();
			if (cec.m_Size == 0) {
				withoutStates++;
			} else {
				if (occurrence.containsKey(entries)) {
					occurrence.put(entries,occurrence.get(entries) + 1);
				} else {
					occurrence.put(entries, 1);
				}
			}
		}
		return m_State2Entry.size() + "entries. " + m_AllCECs.size() + " components " + withoutStates + " without states ____" + occurrence.values();
	}
	
	public boolean isDeadEnd(STATE state) {
		ReachProp reachProp = m_States.get(state).getReachProp();
		return  reachProp == ReachProp.REACHABLE;
	}
	
	public boolean isInitialAfterDeadEndRemoval(STATE state) {
		if (!m_initialStates.contains(state)) {
			throw new IllegalArgumentException("Not initial state");
		}
		return (m_State2Entry.get(state).getDownStates().get(getEmptyStackState()) != ReachProp.REACHABLE);
	}
	
	
	@Override
	public boolean accepts(Word<LETTER> word) {
		throw new UnsupportedOperationException();
	}

	@Override
	public int size() {
		return m_States.size();
	}

	@Override
	public Set<LETTER> getAlphabet() {
		return m_InternalAlphabet;
	}

	@Override
	public String sizeInformation() {
		int states = m_States.size();
		return states + " states.";
	}

	@Override
	public Set<LETTER> getInternalAlphabet() {
		return m_InternalAlphabet;
	}

	@Override
	public Set<LETTER> getCallAlphabet() {
		return m_CallAlphabet;
	}

	@Override
	public Set<LETTER> getReturnAlphabet() {
		return m_ReturnAlphabet;
	}

	@Override
	public StateFactory<STATE> getStateFactory() {
		return m_StateFactory;
	}

	@Override
	public Collection<STATE> getStates() {
		return m_States.keySet();
	}

	@Override
	public Collection<STATE> getInitialStates() {
		return Collections.unmodifiableSet(m_initialStates);
	}

	@Override
	public Collection<STATE> getFinalStates() {
		return Collections.unmodifiableSet(m_finalStates);
	}

	@Override
	public boolean isInitial(STATE state) {
		return m_Operand.isInitial(state);
	}

	@Override
	public boolean isFinal(STATE state) {
		return m_Operand.isFinal(state);
	}

	@Override
	public STATE getEmptyStackState() {
		return m_Operand.getEmptyStackState();
	}

	@Override
	public Collection<LETTER> lettersInternal(STATE state) {
		return m_States.get(state).lettersInternal();
	}

	@Override
	public Collection<LETTER> lettersCall(STATE state) {
		return m_States.get(state).lettersCall();
	}

	@Override
	public Collection<LETTER> lettersReturn(STATE state) {
		return m_States.get(state).lettersReturn();
	}

	@Override
	public Collection<LETTER> lettersInternalIncoming(STATE state) {
		return m_States.get(state).lettersInternalIncoming();
	}

	@Override
	public Collection<LETTER> lettersCallIncoming(STATE state) {
		return m_States.get(state).lettersCallIncoming();
	}

	@Override
	public Collection<LETTER> lettersReturnIncoming(STATE state) {
		return m_States.get(state).lettersReturnIncoming();
	}

	@Override
	public Collection<LETTER> lettersReturnSummary(STATE state) {
		if (!m_States.containsKey(state)) {
			throw new IllegalArgumentException("State " + state + " unknown");
		}
		 Map<LETTER, Map<STATE, Set<STATE>>> map = m_ReturnSummary.get(state);
		return map == null ? new ArrayList<LETTER>(0) : map.keySet();
	}

	@Override
	public Iterable<STATE> succInternal(STATE state, LETTER letter) {
		return m_States.get(state).succInternal(letter);
	}

	@Override
	public Iterable<STATE> succCall(STATE state, LETTER letter) {
		return m_States.get(state).succCall(letter);
	}

	@Override
	public Iterable<STATE> hierPred(STATE state, LETTER letter) {
		return m_States.get(state).hierPred(letter);
	}

	@Override
	public Iterable<STATE> succReturn(STATE state, STATE hier, LETTER letter) {
		return m_States.get(state).succReturn(hier, letter);
	}

	@Override
	public Iterable<STATE> predInternal(STATE state, LETTER letter) {
		return m_States.get(state).predInternal(letter);
	}

	@Override
	public Iterable<STATE> predCall(STATE state, LETTER letter) {
		return m_States.get(state).predCall(letter);
	}

	@Override
	public Iterable<STATE> predReturnLin(STATE state, LETTER letter, STATE hier) {
		return m_States.get(state).predReturnLin(letter, hier);
	}

	@Override
	public Iterable<STATE> predReturnHier(STATE state, LETTER letter) {
		return m_States.get(state).predReturnHier(letter);
	}

	@Override
	public boolean finalIsTrap() {
		throw new UnsupportedOperationException();
	}

	@Override
	public boolean isDeterministic() {
		return false;
	}

	@Override
	public boolean isTotal() {
		throw new UnsupportedOperationException();
	}
	
	private void addReturnSummary(STATE pred, STATE hier, LETTER letter, STATE succ) {
		Map<LETTER, Map<STATE, Set<STATE>>> letter2pred2succs = m_ReturnSummary.get(hier);
		if (letter2pred2succs == null) {
			letter2pred2succs = new HashMap<LETTER, Map<STATE, Set<STATE>>>();
			m_ReturnSummary.put(hier, letter2pred2succs);
		}
		Map<STATE, Set<STATE>> pred2succs = letter2pred2succs.get(letter);
		if (pred2succs == null) {
			pred2succs = new HashMap<STATE, Set<STATE>>();
			letter2pred2succs.put(letter, pred2succs);
		}
		Set<STATE> succS = pred2succs.get(pred);
		if (succS == null) {
			succS = new HashSet<STATE>();
			pred2succs.put(pred, succS);
		}
		succS.add(succ);
	}

	@Override
	public Iterable<SummaryReturnTransition<LETTER, STATE>> 
						returnSummarySuccessor(LETTER letter, STATE hier) {
		Set<SummaryReturnTransition<LETTER, STATE>> result = 
				new HashSet<SummaryReturnTransition<LETTER, STATE>>();
		Map<LETTER, Map<STATE, Set<STATE>>> letter2pred2succ = 
				m_ReturnSummary.get(hier);
		if (letter2pred2succ == null) {
			return result;
		}
		Map<STATE, Set<STATE>> pred2succ = letter2pred2succ.get(letter);
		if (pred2succ == null) {
			return result;
		}
		for (STATE pred : pred2succ.keySet()) {
			if (pred2succ.get(pred) != null) {
				for (STATE succ : pred2succ.get(pred)) {
				SummaryReturnTransition<LETTER, STATE> srt = 
					new SummaryReturnTransition<LETTER, STATE>(pred, letter, succ);
				result.add(srt);
				}
			}
		}
		return result;
	}

	@Override
	public Iterable<IncomingInternalTransition<LETTER, STATE>> internalPredecessors(
			LETTER letter, STATE succ) {
		return m_States.get(succ).internalPredecessors(letter);
	}

	@Override
	public Iterable<IncomingInternalTransition<LETTER, STATE>> internalPredecessors(
			STATE succ) {
		return m_States.get(succ).internalPredecessors();
	}

	@Override
	public Iterable<IncomingCallTransition<LETTER, STATE>> callPredecessors(
			LETTER letter, STATE succ) {
		return m_States.get(succ).callPredecessors(letter);
	}

	@Override
	public Iterable<IncomingCallTransition<LETTER, STATE>> callPredecessors(
			STATE succ) {
		return m_States.get(succ).callPredecessors();
	}

	@Override
	public Iterable<OutgoingInternalTransition<LETTER, STATE>> internalSuccessors(
			STATE state, LETTER letter) {
		return m_States.get(state).internalSuccessors(letter);
	}

	@Override
	public Iterable<OutgoingInternalTransition<LETTER, STATE>> internalSuccessors(
			STATE state) {
		return m_States.get(state).internalSuccessors();
	}

	@Override
	public Iterable<OutgoingCallTransition<LETTER, STATE>> callSuccessors(
			STATE state, LETTER letter) {
		return m_States.get(state).callSuccessors(letter);
	}

	@Override
	public Iterable<OutgoingCallTransition<LETTER, STATE>> callSuccessors(
			STATE state) {
		return m_States.get(state).callSuccessors();
	}

	@Override
	public Iterable<IncomingReturnTransition<LETTER, STATE>> returnPredecessors(
			STATE hier, LETTER letter, STATE succ) {
		return m_States.get(succ).returnPredecessors(hier, letter);
	}

	@Override
	public Iterable<IncomingReturnTransition<LETTER, STATE>> returnPredecessors(
			LETTER letter, STATE succ) {
		return m_States.get(succ).returnPredecessors(letter);
	}

	@Override
	public Iterable<IncomingReturnTransition<LETTER, STATE>> returnPredecessors(
			STATE succ) {
		return m_States.get(succ).returnPredecessors();
	}

	@Override
	public Iterable<OutgoingReturnTransition<LETTER, STATE>> returnSucccessors(
			STATE state, STATE hier, LETTER letter) {
		return m_States.get(state).returnSuccessors(hier, letter);
	}

	@Override
	public Iterable<OutgoingReturnTransition<LETTER, STATE>> returnSuccessors(
			STATE state, LETTER letter) {
		return m_States.get(state).returnSuccessors(letter);
	}

	@Override
	public Iterable<OutgoingReturnTransition<LETTER, STATE>> returnSuccessors(
			STATE state) {
		return m_States.get(state).returnSuccessors();
	}

	@Override
	public Iterable<OutgoingReturnTransition<LETTER, STATE>> returnSuccessorsGivenHier(
			STATE state, STATE hier) {
		return m_States.get(state).returnSuccessorsGivenHier(hier);
	}
	
	
	

	public Set<STATE> getDownStates(STATE state) {
		StateContainer<LETTER, STATE> stateContainer = m_States.get(state);
		CommonEntriesComponent<LETTER,STATE> cec = stateContainer.getCommonEntriesComponent();
		return cec.getDownStates();
	}
	
	public boolean isDoubleDecker(STATE up, STATE down) {
		return getDownStates(up).contains(down);
	}
	
	

	public Set<STATE> getDownStatesAfterDeadEndRemoval(STATE up) {
		HashSet<STATE> downStates = new HashSet<STATE>();
		for(Entry<LETTER,STATE> entry : m_States.get(up).getCommonEntriesComponent().getEntries()) {
			STATE entryState = entry.getState();
			for (IncomingCallTransition<LETTER, STATE> trans : callPredecessors(entryState)) {
				STATE callPred = trans.getPred();
				StateContainer<LETTER, STATE> callPredCont = m_States.get(callPred);
				if (callPredCont.getReachProp() != ReachProp.REACHABLE) {
					downStates.add(callPred);
				}
			}
			if (m_initialStatesAfterDeadEndRemoval.contains(entryState)) {
				downStates.add(getEmptyStackState());
			}
		}
		return downStates;
	}
	
	
	
	
	
	
	////////////////////////////////////////////////////////////////////////////
	
	private class ReachableStatesComputation {
		private final DoubleDeckerWorklist doubleDeckerWorklist = 
				new DoubleDeckerWorklist();
		private final CecSplitWorklist cecSplitWorklist = 
				new CecSplitWorklist();
		private final LinkedList<StateContainer<LETTER,STATE>> m_ForwardWorklist = 
				new LinkedList<StateContainer<LETTER,STATE>>();

		ReachableStatesComputation() throws OperationCanceledException {
			addInitialStates(m_Operand.getInitialStates());

			while (!m_ForwardWorklist.isEmpty()) {
				StateContainer<LETTER,STATE> cont = m_ForwardWorklist.remove(0);
				cont.toString();
				addInternalsAndSuccessors(cont);
				addCallsAndSuccessors(cont);

				CommonEntriesComponent<LETTER,STATE> stateCEC = cont.getCommonEntriesComponent();
				// TODO: need copy to avoid concurModExcpetion ???
				for (STATE down : stateCEC.getDownStates()) {
					if (down != getEmptyStackState()) {
						StateContainer<LETTER,STATE> downSC = m_States.get(down);
						addReturnsAndSuccessors(cont, downSC);
					}
				}

				while (!doubleDeckerWorklist.isEmpty()) {
					DoubleDecker<STATE> doubleDecker = doubleDeckerWorklist
							.dequeue();
					StateContainer<LETTER,STATE> upCont = m_States.get(doubleDecker.getUp());
					StateContainer<LETTER,STATE> downCont = m_States.get(doubleDecker
							.getDown());
					addReturnsAndSuccessors(upCont, downCont);
				}
				
				if (!UltimateServices.getInstance().continueProcessing()) {
					throw new OperationCanceledException();
				}
			}
			assert (m_ForwardWorklist.isEmpty());
			assert (doubleDeckerWorklist.isEmpty());
			assert (cecSplitWorklist.isEmtpy());
		}
		
		
		private void addInitialStates(Iterable<STATE> initialStates) {
			for (STATE state : initialStates) {
				m_initialStates.add(state);
				Entry<LETTER,STATE> entry = new Entry<LETTER,STATE>(state);
				m_State2Entry.put(state, entry);
				entry.getDownStates().put(getEmptyStackState(), ReachProp.REACHABLE);
				HashSet<Entry<LETTER,STATE>> entries = new HashSet<Entry<LETTER,STATE>>(1);
				entries.add(entry);
				HashSet<STATE> downStates = new HashSet<STATE>();
				downStates.add(getEmptyStackState());
				CommonEntriesComponent<LETTER,STATE> cec = 
						new CommonEntriesComponent<LETTER,STATE>(entries,downStates);
				m_AllCECs.add(cec);
				StateContainer<LETTER,STATE> sc = addState(state, cec);
				m_States.put(state, sc);
			}
		}
		
		
		
		/**
		 * Construct State Container. Add to CommonEntriesComponent<LETTER,STATE>. Add to
		 * ForwardWorklist.
		 * @param state
		 * @param cec
		 * @return
		 */
		private StateContainer<LETTER, STATE> addState(STATE state, CommonEntriesComponent<LETTER,STATE> cec) {
			assert !m_States.containsKey(state);
			if (m_Operand.isFinal(state)) {
				m_finalStates.add(state);
			}
			StateContainer<LETTER,STATE> result = new StateContainerFieldAndMap<LETTER,STATE>(state, cec);
			m_States.put(state, result);
			cec.m_Size++;
			if (candidateForOutgoingReturn(state)) {
				cec.addReturnOutCandicate(state);
			}
			m_ForwardWorklist.add(result);
			return result;
		}
		
		private boolean candidateForOutgoingReturn(STATE state) {
			return true;
		}
		

		private <E> Set<E> differenceSet(Set<E> minuend, Set<E> subtrahend) {
			Set<E> result = new HashSet<E>();
			for (E elem : minuend) {
				if (!subtrahend.contains(elem)) {
					result.add(elem);
				}
			}
			return result;
		}
		
		private void updateCECs(CommonEntriesComponent<LETTER,STATE> startCec,
				HashSet<STATE> downStates) {
			List<CommonEntriesComponent<LETTER,STATE>> worklist = new LinkedList<CommonEntriesComponent<LETTER,STATE>>(); 
			Set<CommonEntriesComponent<LETTER,STATE>> visitedCECs = new HashSet<CommonEntriesComponent<LETTER,STATE>>();
			worklist.add(startCec);
			visitedCECs.add(startCec);
			while(!worklist.isEmpty()) {
				CommonEntriesComponent<LETTER,STATE> cec = worklist.remove(0);
				HashSet<STATE> newdownStates = new HashSet<STATE>();
				for (STATE down : downStates) {
					if (!cec.getDownStates().contains(down)) {
						newdownStates.add(down);
					}
				}
				if(!newdownStates.isEmpty()) {
					for (STATE state : cec.getReturnOutCandidates()) {
						for (STATE down : newdownStates) {
							if (down != getEmptyStackState()) {
								doubleDeckerWorklist.enqueue(state, down);
							}
							cec.addDownState(down);
						}
					}
					for (StateContainer<LETTER, STATE> resident : cec.m_BorderOut.keySet()) {
						for (StateContainer<LETTER, STATE> foreigner : cec.m_BorderOut.get(resident)) {
							CommonEntriesComponent<LETTER,STATE> foreignerCec = 
									foreigner.getCommonEntriesComponent();
							if (!visitedCECs.contains(foreignerCec)) {
								worklist.add(foreignerCec);
								visitedCECs.add(cec);
							}
						}
					}

				}
			}
		}
		
		private void addInternalsAndSuccessors(StateContainer<LETTER,STATE> cont) {
			STATE state = cont.getState();
			CommonEntriesComponent<LETTER,STATE> stateCec = cont.getCommonEntriesComponent(); 
			for (OutgoingInternalTransition<LETTER, STATE> trans : 
											m_Operand.internalSuccessors(state)) {
				STATE succ = trans.getSucc();
				StateContainer<LETTER,STATE> succSC = m_States.get(succ);
				if (succSC == null) {
					succSC = addState(succ, stateCec);
				} else {
					CommonEntriesComponent<LETTER,STATE> succCEC = succSC.getCommonEntriesComponent();
					if (stateCec != succCEC) {
						Set<Entry<LETTER,STATE>> newEntries = 
								differenceSet(stateCec.getEntries(), succCEC.getEntries());
						Set<STATE> newDownStates = 
								differenceSet(stateCec.getDownStates(), succCEC.getDownStates()); 
						Set<StateContainer<LETTER,STATE>> splitStates = 
								new HashSet<StateContainer<LETTER,STATE>>();
						splitStates.add(succSC);
						updateCECs(splitStates, newEntries, newDownStates);
						stateCec.addBorderCrossing(cont, succSC);
						cecSplitWorklist.processAll();
					}
				}
				cont.addInternalOutgoing(trans);
				succSC.addInternalIncoming(new IncomingInternalTransition<LETTER, STATE>(state, trans.getLetter()));
			}
		}
		
		
		private void addCallsAndSuccessors(StateContainer<LETTER,STATE> cont) {
			STATE state = cont.getState();
			for (OutgoingCallTransition<LETTER, STATE> trans : 
										m_Operand.callSuccessors(cont.getState())) {
				STATE succ = trans.getSucc();
				StateContainer<LETTER,STATE> succCont = m_States.get(succ);
				HashSet<STATE> downStates = new HashSet<STATE>();
				downStates.add(state);
				Entry<LETTER,STATE> entry;
				if (succCont == null) {
					entry = new Entry<LETTER,STATE>(succ);
					m_State2Entry.put(succ, entry);
					HashSet<Entry<LETTER,STATE>> entries = new HashSet<Entry<LETTER,STATE>>();
					entries.add(entry);
					CommonEntriesComponent<LETTER,STATE> succCEC = 
							new CommonEntriesComponent<LETTER,STATE>(entries, downStates);
					m_AllCECs.add(succCEC);
					succCont = addState(succ, succCEC);
				} else {
					CommonEntriesComponent<LETTER,STATE> succCEC = succCont
							.getCommonEntriesComponent();
					entry = m_State2Entry.get(succ);
					if (entry == null) {
						entry = new Entry<LETTER,STATE>(succ);
						m_State2Entry.put(succ, entry);
					}
					if (succCEC.getEntries().contains(entry)) {
						updateCECs(succCEC, downStates);
					} else {
						HashSet<Entry<LETTER,STATE>> entries = new HashSet<Entry<LETTER,STATE>>();
						entries.add(entry);
						downStates.removeAll(succCEC.getDownStates());
						Set<StateContainer<LETTER,STATE>> splitStarts = new HashSet<StateContainer<LETTER,STATE>>();
						splitStarts.add(succCont);
						updateCECs(splitStarts, entries, downStates);
						cecSplitWorklist.processAll();
					}
				}
				entry.getDownStates().put(state, ReachProp.REACHABLE);
				cont.addCallOutgoing(trans);
				succCont.addCallIncoming(
						new IncomingCallTransition<LETTER, STATE>(state, trans.getLetter()));
			}
		}
		
		

		private void addReturnsAndSuccessors(StateContainer<LETTER,STATE> stateSc, StateContainer<LETTER,STATE> downSc) {
			STATE state = stateSc.getState();
			STATE down = downSc.getState();
			CommonEntriesComponent<LETTER,STATE> downCec = downSc.getCommonEntriesComponent();
			for (OutgoingReturnTransition<LETTER, STATE> trans : 
									m_Operand.returnSuccessorsGivenHier(state,down)) {
				STATE succ = trans.getSucc();
				StateContainer<LETTER,STATE> succSC = m_States.get(succ);
				if (succSC == null) {
					succSC = addState(succ, downCec);
				} else {
					CommonEntriesComponent<LETTER,STATE> succCEC = succSC.getCommonEntriesComponent();
					if (downCec != succCEC) {
						Set<Entry<LETTER,STATE>> newEntries = 
								differenceSet(downCec.getEntries(), succCEC.getEntries());
						Set<STATE> newDownStates = 
								differenceSet(downCec.getDownStates(), succCEC.getDownStates()); 
						Set<StateContainer<LETTER,STATE>> splitStates = 
								new HashSet<StateContainer<LETTER,STATE>>();
						splitStates.add(succSC);
						updateCECs(splitStates, newEntries, newDownStates);
						downCec.addBorderCrossing(downSc, succSC);
						cecSplitWorklist.processAll();
					}
				}
				stateSc.addReturnOutgoing(trans);
				succSC.addReturnIncoming(
						new IncomingReturnTransition<LETTER, STATE>(stateSc.getState(), down, trans.getLetter()));
				addReturnSummary(state, down, trans.getLetter(), succ);
				addSummary(downSc, succSC);
			}
			assert checkTransitionsReturnedConsistent();
		}
		
		private CommonEntriesComponent<LETTER,STATE> updateCECs(Set<StateContainer<LETTER,STATE>> splitStarts, 
				Set<Entry<LETTER,STATE>> newEntries, Set<STATE> newDownStates) {
			CommonEntriesComponent<LETTER,STATE> oldCec;
			{
				Iterator<StateContainer<LETTER,STATE>> it = splitStarts.iterator();
				StateContainer<LETTER, STATE> sc = it.next();
				oldCec = sc.getCommonEntriesComponent();
				for (; it.hasNext(); sc = it.next()) {
					assert (oldCec == sc.getCommonEntriesComponent());
				}
			}
			assert oldCec.m_Size == oldCec.m_ReturnOutCandidates.size();
			if (isSubset(newEntries, oldCec.getEntries())) {
				assert (isSubset(newDownStates, oldCec.getDownStates()));
				return oldCec;
			}
			
			HashSet<Entry<LETTER,STATE>> entries = new HashSet<Entry<LETTER,STATE>>();
			entries.addAll(oldCec.getEntries());
			entries.addAll(newEntries);
			HashSet<STATE> downStates = new HashSet<STATE>();
			downStates.addAll(oldCec.getDownStates());
			downStates.addAll(newDownStates);
			CommonEntriesComponent<LETTER,STATE> result = 
					new CommonEntriesComponent<LETTER,STATE>(entries, downStates);
			m_AllCECs.add(result);
			
			Set<StateContainer<LETTER,STATE>> visited = new HashSet<StateContainer<LETTER,STATE>>();
			List<StateContainer<LETTER,STATE>> worklist = new LinkedList<StateContainer<LETTER,STATE>>();
			for (StateContainer<LETTER,STATE> splitStart : splitStarts) {
				assert(splitStart.getCommonEntriesComponent() == oldCec);
				worklist.add(splitStart);
				visited.add(splitStart);
			}
			while (!worklist.isEmpty()) {
				StateContainer<LETTER,STATE> stateSc = worklist.remove(0);
				assert stateSc.getCommonEntriesComponent() == oldCec;
				
				oldCec.moveWithoutBorderUpdate(stateSc, result);
				if (result.getReturnOutCandidates().contains(stateSc.getState())) {
					for(STATE down : newDownStates) {
						if (down != getEmptyStackState()) {
							doubleDeckerWorklist.enqueue(stateSc.getState(), down);
						}
					}
				}
				oldCec.m_BorderOut.remove(stateSc);
				Set<StateContainer<LETTER,STATE>> foreigners = null;
				for (OutgoingInternalTransition<LETTER, STATE> trans : stateSc.internalSuccessors()) {
					STATE succ = trans.getSucc();
					StateContainer<LETTER,STATE> succSc = m_States.get(succ);
					if (succSc.getCommonEntriesComponent() == oldCec) {
						if (!visited.contains(succSc)) {
							worklist.add(succSc);
							visited.add(succSc);
						}
					} else if (succSc.getCommonEntriesComponent() != result) {
						if (foreigners == null) {
							foreigners = new HashSet<StateContainer<LETTER,STATE>>();
						}
						foreigners.add(succSc);
						cecSplitWorklist.add(succSc, entries, downStates);
					}
				}
				if (m_Summaries.containsKey(stateSc)) {
					for (StateContainer<LETTER,STATE> succSc : m_Summaries.get(stateSc)) {
						if (succSc.getCommonEntriesComponent() == oldCec) {
							if (!visited.contains(succSc)) {
								worklist.add(succSc);
								visited.add(succSc);
							}
						} else if (succSc.getCommonEntriesComponent() != result) {
							if (foreigners == null) {
								foreigners = new HashSet<StateContainer<LETTER,STATE>>();
							}
							foreigners.add(succSc);
							cecSplitWorklist.add(succSc, entries, downStates);
						}
					}
				}
				if (foreigners != null) {
					result.m_BorderOut.put(stateSc, foreigners);
				}
			}
			
			
			
			if (oldCec.m_Size != 0) {
				assert (oldCec.m_Size > 0);
				// we have to check all states of the newCec if they have an
				// incoming transition from the oldCec and set m_BorderOut of
				// oldCec accordingly
				for (StateContainer<LETTER,STATE> sc : visited) {
					
//					if (oldCec.isBorderState(sc)) {
//						Set<StateContainer<LETTER,STATE>> foreigners = oldCec.getForeigners(sc);
//						result.m_BorderOut.put(sc, foreigners);
//						oldCec.m_BorderOut.remove(sc);
//						Iterator<StateContainer<LETTER,STATE>> it = foreigners.iterator();
//						for (StateContainer<LETTER,STATE> foreigner = it.next(); it.hasNext(); foreigner = it.next()) {
//							if (foreigner.getCommonEntriesComponent() == result) {
//								it.remove();
//							} else {
//								cecSplitWorklist.add(foreigner, entries, downStates);
//							}
//						}
//					}
					
					//TODO: move this upwards. no second iteration required
					for (IncomingInternalTransition<LETTER, STATE> inTrans : sc.internalPredecessors()) {
						STATE pred = inTrans.getPred();
						StateContainer<LETTER,STATE> predSc = m_States.get(pred);
						if (predSc.getCommonEntriesComponent() == oldCec) {
							oldCec.addBorderCrossing(predSc, sc);
						}
					}
					for (IncomingReturnTransition<LETTER, STATE> inTrans : sc.returnPredecessors()) {
						STATE hierPred = inTrans.getHierPred();
						StateContainer<LETTER,STATE> predSc = m_States.get(hierPred);
						if (predSc.getCommonEntriesComponent() == oldCec) {
							oldCec.addBorderCrossing(predSc, sc);
						}
					}
				}
			} else {
				assert oldCec.m_BorderOut.isEmpty();
			}
			assert oldCec.m_Size == oldCec.m_ReturnOutCandidates.size();
			assert result.m_Size == result.m_ReturnOutCandidates.size();
			return result;
		}
		
		class DoubleDeckerWorklist {
			LinkedList<STATE> m_UpStates = new LinkedList<STATE>();
			LinkedList<STATE> m_DownStates = new LinkedList<STATE>();
			
			private void enqueue(STATE up, STATE down) {
				m_UpStates.add(up);
				m_DownStates.add(down);
			}
			
			private boolean isEmpty() {
				return m_UpStates.isEmpty();
			}
			
			private DoubleDecker<STATE> dequeue() {
				return new DoubleDecker<STATE>(
						m_DownStates.remove(0), m_UpStates.remove(0));
			}
		}
		
		
		
		private class CecSplitWorklist {
			List<Object[]> m_worklist = new LinkedList<Object[]>();
			
			private boolean isEmtpy() {
				return m_worklist.isEmpty();
			}
			
			private void add(StateContainer<LETTER, STATE> state, Set<Entry<LETTER,STATE>> entries, Set<STATE> downStates) {
				assert state.getCommonEntriesComponent().m_Size == state.getCommonEntriesComponent().m_ReturnOutCandidates.size();
				Object[] elem = new Object[] { state, entries, downStates };
				m_worklist.add(elem);
			}
			
			@SuppressWarnings("unchecked")
			private void processFirst() {
				Object[] elem = m_worklist.remove(0);
				StateContainer<LETTER,STATE> stateC = (StateContainer<LETTER,STATE>) elem[0];
				Set<Entry<LETTER,STATE>> entries = (Set<Entry<LETTER,STATE>>) elem[1];
				Set<STATE> downStates = (Set<STATE>) elem[2];
				HashSet<StateContainer<LETTER,STATE>> splitStates = new HashSet<StateContainer<LETTER,STATE>>();
				splitStates.add(stateC);
				updateCECs(splitStates, entries, downStates);
			}
			
			private void processAll() {
				while (!isEmtpy()) {
					processFirst();
				}
			}
		}
	}

		

	
	

////////////////////////////////////////////////////////////////////////////////
	private class DeadEndComputation {
		
		private LinkedList<StateContainer<LETTER,STATE>> m_NonReturnBackwardWorklist =
				new LinkedList<StateContainer<LETTER,STATE>>();
		private Set<StateContainer<LETTER,STATE>> m_HasIncomingReturn = 
				new HashSet<StateContainer<LETTER,STATE>>();
		private LinkedList<StateContainer<LETTER,STATE>> m_NonCallBackwardWorklist =
				new LinkedList<StateContainer<LETTER,STATE>>();

		DeadEndComputation() {
			for (STATE fin : getFinalStates()) {
				StateContainer<LETTER,STATE> cont = m_States.get(fin);
				assert cont.getReachProp() == ReachProp.REACHABLE;
				cont.setReachProp(ReachProp.NODEADEND);
				m_StatesAfterDeadEndRemoval.add(fin);
				m_NonReturnBackwardWorklist.add(cont);
			}

			while (!m_NonReturnBackwardWorklist.isEmpty()) {
				StateContainer<LETTER,STATE> cont = m_NonReturnBackwardWorklist.remove(0);
				if (cont.isEntry()) {
					Entry<LETTER,STATE> entry = m_State2Entry.get(cont.getState());
					assert (isSubset(entry.getDownStates().keySet(), 
							cont.getCommonEntriesComponent().m_DownStates));
					for (STATE down : entry.getDownStates().keySet()) {
						entry.getDownStates().put(down, ReachProp.NODEADEND);
					}
					m_initialStatesAfterDeadEndRemoval.add(entry.getState());
				}
				
				for (IncomingInternalTransition<LETTER, STATE> inTrans : cont
						.internalPredecessors()) {
					STATE pred = inTrans.getPred();
					StateContainer<LETTER,STATE> predCont = m_States.get(pred);
					if (predCont.getReachProp() != ReachProp.NODEADEND) {
						predCont.setReachProp(ReachProp.NODEADEND);
						m_StatesAfterDeadEndRemoval.add(pred);
						m_NonReturnBackwardWorklist.add(predCont);
					}
				}
				for (IncomingReturnTransition<LETTER, STATE> inTrans : cont
						.returnPredecessors()) {
					STATE hier = inTrans.getHierPred();
					StateContainer<LETTER,STATE> hierCont = m_States.get(hier);
					if (hierCont.getReachProp() != ReachProp.NODEADEND) {
						hierCont.setReachProp(ReachProp.NODEADEND);
						m_StatesAfterDeadEndRemoval.add(hier);
						m_NonReturnBackwardWorklist.add(hierCont);
					}
					m_HasIncomingReturn.add(cont);
				}
				for (IncomingCallTransition<LETTER, STATE> inTrans : cont
						.callPredecessors()) {
					STATE pred = inTrans.getPred();
					StateContainer<LETTER,STATE> predCont = m_States.get(pred);
					if (predCont.getReachProp() != ReachProp.NODEADEND) {
						predCont.setReachProp(ReachProp.NODEADEND);
						m_StatesAfterDeadEndRemoval.add(pred);
						m_NonReturnBackwardWorklist.add(predCont);
					}
				}
			}
			
			m_NonCallBackwardWorklist.addAll(m_HasIncomingReturn);
			
			while (!m_NonCallBackwardWorklist.isEmpty()) {
				StateContainer<LETTER, STATE> cont = m_NonCallBackwardWorklist.remove(0);
				for (IncomingInternalTransition<LETTER, STATE> inTrans : cont
						.internalPredecessors()) {
					STATE pred = inTrans.getPred();
					StateContainer<LETTER,STATE> predCont = m_States.get(pred);
					if (predCont.getReachProp() != ReachProp.NODEADEND) {
						predCont.setReachProp(ReachProp.NODEADEND);
						m_StatesAfterDeadEndRemoval.add(pred);
						m_NonCallBackwardWorklist.add(predCont);
					}
				}
				for (IncomingReturnTransition<LETTER, STATE> inTrans : cont
						.returnPredecessors()) {
					STATE hier = inTrans.getHierPred();
					StateContainer<LETTER,STATE> hierCont = m_States.get(hier);
					if (hierCont.getReachProp() != ReachProp.NODEADEND) {
						hierCont.setReachProp(ReachProp.NODEADEND);
						m_StatesAfterDeadEndRemoval.add(hier);
						m_NonCallBackwardWorklist.add(hierCont);
					}
					STATE lin = inTrans.getLinPred();
					StateContainer<LETTER,STATE> linCont = m_States.get(lin);
					if (linCont.getReachProp() != ReachProp.NODEADEND) {
						linCont.setReachProp(ReachProp.NODEADEND);
						m_StatesAfterDeadEndRemoval.add(lin);
						m_NonCallBackwardWorklist.add(linCont);
					}
					for (Entry<LETTER,STATE> entry : linCont.getCommonEntriesComponent().getEntries()) {
						if (entry.getDownStates().containsKey(hier)) {
							entry.getDownStates().put(hier, ReachProp.NODEADEND);
						}
					}
				}
			}
		}
}


	
	
	

	

	
	
	
	
	
	
	

	

	

	
	


	
	

	

	
	
	

	
	
	

	
	


	
	

	
	
	
	
	
	
	
	
	
	
	
	
	

	////////////////////////////////////////////////////////////////////////////
	// Methods to check correctness
	
	private boolean containsInternalTransition(STATE state, LETTER letter, STATE succ) {
		return m_States.get(state).containsInternalTransition(letter, succ);
	}
	
	private boolean containsCallTransition(STATE state, LETTER letter, STATE succ) {
		return m_States.get(state).containsCallTransition(letter, succ);
	}
	
	private boolean containsReturnTransition(STATE state, STATE hier, LETTER letter, STATE succ) {
		return m_States.get(state).containsReturnTransition(hier, letter, succ);
	}
	
	protected boolean containsSummaryReturnTransition(STATE lin, STATE hier, LETTER letter, STATE succ) {
		for (SummaryReturnTransition<LETTER, STATE> trans : returnSummarySuccessor(letter, hier)) {
			if (succ.equals(trans.getSucc()) && lin.equals(trans.getLinPred())) {
				return true;
			}
		}
		return false;
	}
	
	private boolean checkTransitionsReturnedConsistent() {
		boolean result = true;
		for (STATE state : getStates()) {
			for (IncomingInternalTransition<LETTER, STATE> inTrans : internalPredecessors(state)) {
				result &= containsInternalTransition(inTrans.getPred(), inTrans.getLetter(), state);
				assert result;
				StateContainer<LETTER, STATE> cont  = m_States.get(state); 
				result &= cont.lettersInternalIncoming().contains(inTrans.getLetter());
				assert result;
				result &= cont.predInternal(inTrans.getLetter()).contains(inTrans.getPred());
				assert result;
			}
			for (OutgoingInternalTransition<LETTER, STATE> outTrans : internalSuccessors(state)) {
				result &= containsInternalTransition(state, outTrans.getLetter(), outTrans.getSucc());
				assert result;
				StateContainer<LETTER, STATE> cont  = m_States.get(state);
				result &= cont.lettersInternal().contains(outTrans.getLetter());
				assert result;
				result &= cont.succInternal(outTrans.getLetter()).contains(outTrans.getSucc());
				assert result;
			}
			for (IncomingCallTransition<LETTER, STATE> inTrans : callPredecessors(state)) {
				result &= containsCallTransition(inTrans.getPred(), inTrans.getLetter(), state);
				assert result;
				StateContainer<LETTER, STATE> cont  = m_States.get(state);
				result &= cont.lettersCallIncoming().contains(inTrans.getLetter());
				assert result;
				result &= cont.predCall(inTrans.getLetter()).contains(inTrans.getPred());
				assert result;
			}
			for (OutgoingCallTransition<LETTER, STATE> outTrans : callSuccessors(state)) {
				result &= containsCallTransition(state, outTrans.getLetter(), outTrans.getSucc());
				assert result;
				StateContainer<LETTER, STATE> cont  = m_States.get(state);
				result &= cont.lettersCall().contains(outTrans.getLetter());
				assert result;
				result &= cont.succCall(outTrans.getLetter()).contains(outTrans.getSucc());
				assert result;
			}
			for (IncomingReturnTransition<LETTER, STATE> inTrans : returnPredecessors(state)) {
				result &= containsReturnTransition(inTrans.getLinPred(), inTrans.getHierPred(), inTrans.getLetter(), state);
				assert result;
				result &= containsSummaryReturnTransition(inTrans.getLinPred(), inTrans.getHierPred(), inTrans.getLetter(), state);
				assert result;
				StateContainer<LETTER, STATE> cont  = m_States.get(state);
				result &= cont.lettersReturnIncoming().contains(inTrans.getLetter());
				assert result;
				result &= cont.predReturnHier(inTrans.getLetter()).contains(inTrans.getHierPred());
				assert result;
				result &= cont.predReturnLin(inTrans.getLetter(),inTrans.getHierPred()).contains(inTrans.getLinPred());
				assert result;
			}
			for (OutgoingReturnTransition<LETTER, STATE> outTrans : returnSuccessors(state)) {
				result &= containsReturnTransition(state, outTrans.getHierPred(), outTrans.getLetter(), outTrans.getSucc());
				assert result;
				result &= containsSummaryReturnTransition(state, outTrans.getHierPred(), outTrans.getLetter(), outTrans.getSucc());
				assert result;
				StateContainer<LETTER, STATE> cont  = m_States.get(state);
				result &= cont.lettersReturn().contains(outTrans.getLetter());
				assert result;
				result &= cont.hierPred(outTrans.getLetter()).contains(outTrans.getHierPred());
				assert result;
				result &= cont.succReturn(outTrans.getHierPred(),outTrans.getLetter()).contains(outTrans.getSucc());
				assert result;
			}
//			for (LETTER letter : lettersReturnSummary(state)) {
//				for (SummaryReturnTransition<LETTER, STATE> sumTrans : returnSummarySuccessor(letter, state)) {
//				result &= containsReturnTransition(state, sumTrans.getHierPred(), outTrans.getLetter(), outTrans.getSucc());
//				assert result;
//				StateContainer<LETTER, STATE> cont  = m_States.get(state);
//				result &= cont.lettersReturn().contains(outTrans.getLetter());
//				assert result;
//				result &= cont.hierPred(outTrans.getLetter()).contains(outTrans.getHierPred());
//				assert result;
//				result &= cont.succReturn(outTrans.getHierPred(),outTrans.getLetter()).contains(outTrans.getSucc());
//				assert result;
//				}
//			}
			
			
			for (LETTER letter : lettersInternal(state)) {
				for (STATE succ : succInternal(state, letter)) {
					result &= containsInternalTransition(state, letter, succ);
					assert result;
				}
			}
			for (LETTER letter : lettersCall(state)) {
				for (STATE succ : succCall(state, letter)) {
					result &= containsCallTransition(state, letter, succ);
					assert result;
				}
			}
			for (LETTER letter : lettersReturn(state)) {
				for (STATE hier : hierPred(state, letter)) {
					for (STATE succ : succReturn(state, hier, letter)) {
						result &= containsReturnTransition(state, hier, letter, succ);
						assert result;
					}
				}
			}
			for (LETTER letter : lettersInternalIncoming(state)) {
				for (STATE pred : predInternal(state, letter)) {
					result &= containsInternalTransition(pred, letter, state);
					assert result;
				}
			}
			for (LETTER letter : lettersCallIncoming(state)) {
				for (STATE pred : predCall(state, letter)) {
					result &= containsCallTransition(pred, letter, state);
					assert result;
				}
			}
			for (LETTER letter : lettersReturnIncoming(state)) {
				for (STATE hier : predReturnHier(state, letter)) {
					for (STATE lin : predReturnLin(state, letter, hier)) {
						result &= containsReturnTransition(lin, hier, letter, state);
						assert result;
					}
				}
			}
			
		}

		return result;
	}
	
	private boolean cecSumConsistent() {
		int sum = 0;
		for (CommonEntriesComponent<LETTER,STATE> cec : m_AllCECs) {
			sum += cec.m_Size;
		}
		int allStates = m_States.keySet().size();
		return sum == allStates;
	}
	
	private boolean allStatesAreInTheirCec() {
		boolean result = true;
		for (STATE state : m_States.keySet()) {
			StateContainer<LETTER,STATE> sc = m_States.get(state);
			CommonEntriesComponent<LETTER,STATE> cec = sc.getCommonEntriesComponent();
			if (!cec.m_BorderOut.keySet().contains(sc)) {
				Set<StateContainer<LETTER,STATE>> empty = new HashSet<StateContainer<LETTER,STATE>>();
				result &= internalOutSummaryOutInCecOrForeigners(sc, empty, cec);
			}
		}
		return result;
	}
	
	private boolean occuringStatesAreConsistent(CommonEntriesComponent<LETTER,STATE> cec) {
		boolean result = true;
		Set<STATE> downStates = cec.m_DownStates;
		Set<Entry<LETTER,STATE>> entries = cec.m_Entries;
		if (cec.m_Size > 0) {
			result &= downStatesAreCallPredsOfEntries(downStates, entries);
		}
		assert (result);
		result &= eachStateHasThisCec(cec.getReturnOutCandidates(), cec);
		assert (result);
		for (StateContainer<LETTER, STATE> resident : cec.m_BorderOut.keySet()) {
			Set<StateContainer<LETTER,STATE>> foreignerSCs = cec.m_BorderOut.get(resident);
			result &= internalOutSummaryOutInCecOrForeigners(resident, foreignerSCs, cec);
			assert (result);
		}
		return result;
	}
	
	
	private boolean downStatesConsistentwithEntriesDownStates(CommonEntriesComponent<LETTER,STATE> cec) {
		boolean result = true;
		Set<STATE> downStates = cec.m_DownStates;
		Set<Entry<LETTER,STATE>> entries = cec.m_Entries;
		Set<STATE> downStatesofEntries = new HashSet<STATE>();
		for (Entry<LETTER,STATE> entry : entries) {
			downStatesofEntries.addAll(entry.getDownStates().keySet());
		}
		result &= isSubset(downStates, downStatesofEntries);
		assert (result);
		result &= isSubset(downStatesofEntries, downStates);
		assert (result);
		return result;
	}
	
	private boolean internalOutSummaryOutInCecOrForeigners(StateContainer<LETTER, STATE> state, Set<StateContainer<LETTER,STATE>> foreigners, CommonEntriesComponent<LETTER,STATE> cec) {
		Set<StateContainer<LETTER,STATE>> neighbors = new HashSet<StateContainer<LETTER,STATE>>();
		
		for (OutgoingInternalTransition<LETTER, STATE> trans : state.internalSuccessors()) {
			STATE succ = trans.getSucc();
			StateContainer<LETTER,STATE> succSc = m_States.get(succ);
			if (succSc.getCommonEntriesComponent() == cec) {
				// do nothing
			} else {
				neighbors.add(succSc);
			}
		}
		if (m_Summaries.containsKey(state)) {
			for (StateContainer<LETTER,STATE> succSc : m_Summaries.get(state)) {
				if (succSc.getCommonEntriesComponent() == cec) {
					// do nothing
				} else {
					neighbors.add(succSc);
				}
			}
		}
		boolean allNeighborAreForeigners = isSubset(neighbors, foreigners);
		assert allNeighborAreForeigners;
		boolean allForeignersAreNeighbor = isSubset(foreigners, neighbors);
		assert allForeignersAreNeighbor;
		return allNeighborAreForeigners && allForeignersAreNeighbor;
	}
	
	private boolean eachStateHasThisCec(Set<STATE> states, CommonEntriesComponent<LETTER,STATE> cec) {
		boolean result = true;
		for (STATE state : states) {
			StateContainer<LETTER, STATE> sc = m_States.get(state);
			if (sc.getCommonEntriesComponent() != cec) {
				result = false;
				assert result;
			}
		}
		return result;
	}
	
	private boolean downStatesAreCallPredsOfEntries(Set<STATE> downStates, Set<Entry<LETTER,STATE>> entries) {
		Set<STATE> callPreds = new HashSet<STATE>();
		for (Entry<LETTER,STATE> entry : entries) {
			STATE entryState = entry.getState();
			if (isInitial(entryState)) {
				callPreds.add(getEmptyStackState());
			}
			for (IncomingCallTransition<LETTER, STATE> trans : callPredecessors(entryState)) {
				callPreds.add(trans.getPred());
			}
		}
		boolean callPredsIndownStates = isSubset(callPreds, downStates);
		boolean downStatesInCallPreds = isSubset(downStates, callPreds);
		return callPredsIndownStates && downStatesInCallPreds;
	}
	
	
	
	
	

	////////////////////////////////////////////////////////////////////////////
	// Auxilliary Methods

	public static <E> boolean noElementIsNull(Collection<E> collection) {
		for (E elem : collection) {
			if (elem == null) return false;
		}
		return true;
	}
	
	private static <E> boolean isSubset(Set<E> lhs, Set<E> rhs) {
		for (E elem : lhs) {
			if (!rhs.contains(elem)) {
				return false;
			}
		}
		return true;
	}
	
	@Override
	public String toString() {
		return (new AtsDefinitionPrinter<String,String>("nwa", this)).getDefinitionAsString();
	}

}
