/*
 * Copyright (C) 2017 Matthias Heizmann (heizmann@informatik.uni-freiburg.de)
 * Copyright (C) 2017 Christian Schilling (schillic@informatik.uni-freiburg.de)
 * Copyright (C) 2017 University of Freiburg
 * 
 * This file is part of the ULTIMATE ModelCheckerUtils Library.
 * 
 * The ULTIMATE ModelCheckerUtils Library is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * The ULTIMATE ModelCheckerUtils Library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 * 
 * You should have received a copy of the GNU Lesser General Public License
 * along with the ULTIMATE ModelCheckerUtils Library. If not, see <http://www.gnu.org/licenses/>.
 * 
 * Additional permission under GNU GPL version 3 section 7:
 * If you modify the ULTIMATE ModelCheckerUtils Library, or any covered work, by linking
 * or combining it with Eclipse RCP (or a modified version of Eclipse RCP),
 * containing parts covered by the terms of the Eclipse Public License, the
 * licensors of the ULTIMATE ModelCheckerUtils Library grant you additional permission
 * to convey the resulting work.
 */
package de.uni_freiburg.informatik.ultimate.plugins.generator.traceabstraction.errorabstraction;

import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import de.uni_freiburg.informatik.ultimate.automata.AutomataLibraryException;
import de.uni_freiburg.informatik.ultimate.automata.AutomataLibraryServices;
import de.uni_freiburg.informatik.ultimate.automata.IRun;
import de.uni_freiburg.informatik.ultimate.automata.Word;
import de.uni_freiburg.informatik.ultimate.automata.nestedword.IDoubleDeckerAutomaton;
import de.uni_freiburg.informatik.ultimate.automata.nestedword.INestedWordAutomaton;
import de.uni_freiburg.informatik.ultimate.automata.nestedword.INwaOutgoingLetterAndTransitionProvider;
import de.uni_freiburg.informatik.ultimate.automata.nestedword.NestedWordAutomaton;
import de.uni_freiburg.informatik.ultimate.automata.nestedword.VpAlphabet;
import de.uni_freiburg.informatik.ultimate.automata.nestedword.operations.Determinize;
import de.uni_freiburg.informatik.ultimate.automata.nestedword.operations.Difference;
import de.uni_freiburg.informatik.ultimate.automata.nestedword.operations.Intersect;
import de.uni_freiburg.informatik.ultimate.automata.nestedword.operations.IsEmpty;
import de.uni_freiburg.informatik.ultimate.automata.nestedword.operations.PowersetDeterminizer;
import de.uni_freiburg.informatik.ultimate.automata.nestedword.operations.RemoveDeadEnds;
import de.uni_freiburg.informatik.ultimate.automata.nestedword.operations.RemoveUnreachable;
import de.uni_freiburg.informatik.ultimate.automata.nestedword.reachablestates.NestedWordAutomatonReachableStates;
import de.uni_freiburg.informatik.ultimate.core.model.services.ILogger;
import de.uni_freiburg.informatik.ultimate.core.model.services.IUltimateServiceProvider;
import de.uni_freiburg.informatik.ultimate.logic.Term;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.cfg.structure.IAction;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.cfg.structure.IIcfgTransition;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.cfg.variables.IProgramVar;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.smt.predicates.IPredicate;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.smt.predicates.IPredicateUnifier;
import de.uni_freiburg.informatik.ultimate.plugins.generator.traceabstraction.PredicateFactoryForInterpolantAutomata;
import de.uni_freiburg.informatik.ultimate.plugins.generator.traceabstraction.PredicateFactoryResultChecking;
import de.uni_freiburg.informatik.ultimate.plugins.generator.traceabstraction.interpolantautomata.builders.StraightLineInterpolantAutomatonBuilder;
import de.uni_freiburg.informatik.ultimate.plugins.generator.traceabstraction.singletracecheck.IInterpolantGenerator;
import de.uni_freiburg.informatik.ultimate.plugins.generator.traceabstraction.singletracecheck.InterpolantComputationStatus;
import de.uni_freiburg.informatik.ultimate.util.statistics.Benchmark;
import de.uni_freiburg.informatik.ultimate.util.statistics.IStatisticsDataProvider;
import de.uni_freiburg.informatik.ultimate.util.statistics.IStatisticsType;

/**
 * Statistics provider for error automaton construction.
 * 
 * @author Matthias Heizmann (heizmann@informatik.uni-freiburg.de)
 * @author Christian Schilling (schillic@informatik.uni-freiburg.de)
 */
public class ErrorAutomatonStatisticsGenerator implements IStatisticsDataProvider {
	private static final String ERROR_AUTOMATON_CONSTRUCTION_TIME = "ErrorAutomatonConstructionTime";
	private static final String ERROR_AUTOMATON_DIFFERENCE_TIME = "ErrorAutomatonDifferenceTime";
	private final Benchmark mBenchmark;
	private boolean mRunningConstruction = false;
	private boolean mRunningDifference = false;
	private int mTraceLength = -1;
	private final List<AutomatonStatisticsEntry> mAutomatonStatistics = new LinkedList<>();
	private Boolean mAcceptsSingleTrace;

	public ErrorAutomatonStatisticsGenerator() {
		mBenchmark = new Benchmark();
		mBenchmark.register(ERROR_AUTOMATON_CONSTRUCTION_TIME);
	}

	public void startErrorAutomatonConstructionTime() {
		assert !mRunningConstruction : "Timing already running";
		mRunningConstruction = true;
		mBenchmark.start(ERROR_AUTOMATON_CONSTRUCTION_TIME);
	}

	public void stopErrorAutomatonConstructionTime() {
		assert mRunningConstruction : "Timing not running";
		mRunningConstruction = false;
		mBenchmark.pause(ERROR_AUTOMATON_CONSTRUCTION_TIME);
	}

	public void startErrorAutomatonDifferenceTime() {
		assert !mRunningDifference : "Timing already running";
		mRunningDifference = true;
		mBenchmark.start(ERROR_AUTOMATON_DIFFERENCE_TIME);
	}

	public void stopErrorAutomatonDifferenceTime() {
		assert mRunningDifference : "Timing not running";
		mRunningDifference = false;
		mBenchmark.pause(ERROR_AUTOMATON_DIFFERENCE_TIME);
	}

	public void reportTraceLength(final int length) {
		assert mTraceLength == -1 : "Length already reported";
		mTraceLength = length;
	}

	public <LETTER extends IIcfgTransition<?>> void evaluateFinalErrorAutomaton(final IUltimateServiceProvider services,
			final ILogger logger, final IErrorAutomatonBuilder<LETTER> errorAutomatonBuilder,
			final INwaOutgoingLetterAndTransitionProvider<LETTER, IPredicate> abstraction,
			final PredicateFactoryForInterpolantAutomata predicateFactory,
			final PredicateFactoryResultChecking predicateFactoryResultChecking,
			final IRun<LETTER, IPredicate, ?> errorTrace) throws AutomataLibraryException {
		final NestedWordAutomaton<LETTER, IPredicate> subtrahend;
		String automatonType;
		final AutomataLibraryServices alServices = new AutomataLibraryServices(services);
		switch (errorAutomatonBuilder.getType()) {
			case ERROR_AUTOMATON:
				subtrahend = errorAutomatonBuilder.getResultBeforeEnhancement();
				automatonType = "error";
				break;
			case DANGER_AUTOMATON:
				subtrahend = constructStraightLineAutomaton(services, errorTrace, new VpAlphabet<>(abstraction),
						predicateFactory);
				automatonType = "danger";
				break;
			default:
				throw new IllegalArgumentException("Unknown error automaton type: " + errorAutomatonBuilder.getType());
		}
		final NestedWordAutomatonReachableStates<LETTER, IPredicate> errorAutomatonAfterEnhancement =
				new RemoveUnreachable<>(alServices, errorAutomatonBuilder.getResultAfterEnhancement()).getResult();
		final INestedWordAutomaton<LETTER, IPredicate> intersectionWithAbstraction =
				new Intersect<>(alServices, predicateFactoryResultChecking, abstraction, errorAutomatonAfterEnhancement)
						.getResult();
		final INestedWordAutomaton<LETTER, IPredicate> withoutDeadEnds =
				new RemoveDeadEnds<>(alServices, intersectionWithAbstraction).getResult();
		final INestedWordAutomaton<LETTER, IPredicate> effectiveErrorAutomaton =
				new Determinize<>(alServices, predicateFactoryResultChecking, withoutDeadEnds).getResult();
		final PowersetDeterminizer<LETTER, IPredicate> psd =
				new PowersetDeterminizer<>(subtrahend, true, predicateFactory);
		final IDoubleDeckerAutomaton<LETTER, IPredicate> diff = new Difference<>(alServices,
				predicateFactoryResultChecking, effectiveErrorAutomaton, subtrahend, psd, false).getResult();
		mAcceptsSingleTrace = new IsEmpty<>(alServices, diff).getResult();
		if (mAcceptsSingleTrace) {
			logger.warn("Enhancement did not add additional traces.");
		}
		final NestedWordAutomatonReachableStates<LETTER, IPredicate> nwars =
				new NestedWordAutomatonReachableStates<>(alServices, effectiveErrorAutomaton);
		nwars.computeAcceptingComponents();
		logger.info("Effective " + automatonType + " automaton size information: " + nwars.sizeInformation());
	}

	public void finishAutomatonInstance() {
		if (mRunningConstruction || mRunningDifference || mTraceLength == -1 || mAcceptsSingleTrace == null) {
			throw new IllegalAccessError("Not all statistics data were provided.");
		}
		final long constructionTime = getLastConstructionTime();
		final long differenceTime =
				(long) mBenchmark.getElapsedTime(ERROR_AUTOMATON_DIFFERENCE_TIME, TimeUnit.NANOSECONDS);
		final int traceLength = mTraceLength;
		final boolean acceptsSingleTrace = mAcceptsSingleTrace;
		mTraceLength = -1;
		mAutomatonStatistics
				.add(new AutomatonStatisticsEntry(constructionTime, differenceTime, traceLength, acceptsSingleTrace));
	}

	/**
	 * @return Construction time of last (previous) error automaton (in nanoseconds).
	 */
	public long getLastConstructionTime() {
		return (long) mBenchmark.getElapsedTime(ERROR_AUTOMATON_CONSTRUCTION_TIME, TimeUnit.NANOSECONDS);
	}

	/**
	 * @return Total number of error automata/traces.
	 */
	public Object getTotalNumber() {
		return mAutomatonStatistics.size();
	}

	@Override
	public Collection<String> getKeys() {
		return ErrorAutomatonStatisticsType.getInstance().getKeys();
	}

	@Override
	public Object getValue(final String key) {
		final ErrorAutomatonStatisticsDefinitions keyEnum =
				Enum.valueOf(ErrorAutomatonStatisticsDefinitions.class, key);
		switch (keyEnum) {
			case TotalNumber:
				return getTotalNumber();
			case TraceLengthAvg:
				return getAverageTraceLength();
			case ErrorAutomatonConstructionTimeAvg:
				return getAverageErrorAutomatonConstructionTime(stats -> stats.mConstructionTime);
			case ErrorAutomatonConstructionTimeTotal:
				return getTotalErrorAutomatonConstructionTime(stats -> stats.mConstructionTime);
			case ErrorAutomatonDifferenceTimeAvg:
				return getAverageErrorAutomatonConstructionTime(stats -> stats.mDifferenceTime);
			case ErrorAutomatonDifferenceTimeTotal:
				return getTotalErrorAutomatonConstructionTime(stats -> stats.mDifferenceTime);
			case AcceptsSingleTrace:
				return getTotalNumberOfSingleTraceAcceptance();
			default:
				throw new AssertionError("Unknown key: " + key);
		}
	}

	@Override
	public IStatisticsType getBenchmarkType() {
		return ErrorAutomatonStatisticsType.getInstance();
	}

	private int getAverageTraceLength() {
		final int total = mAutomatonStatistics.size();
		if (total == 0) {
			return 0;
		}
		int result = 0;
		for (final AutomatonStatisticsEntry stats : mAutomatonStatistics) {
			result += stats.mTraceLength;
		}
		return result / total;
	}

	private Object getTotalNumberOfSingleTraceAcceptance() {
		int result = 0;
		for (final AutomatonStatisticsEntry stats : mAutomatonStatistics) {
			if (stats.mAcceptsSingleTrace) {
				++result;
			}
		}
		return result;
	}

	private long getAverageErrorAutomatonConstructionTime(final Function<AutomatonStatisticsEntry, Long> stats2long) {
		final int total = mAutomatonStatistics.size();
		if (total == 0) {
			return 0L;
		}
		final long time = getTotalErrorAutomatonConstructionTime(stats2long);
		return time / total;
	}

	private long getTotalErrorAutomatonConstructionTime(final Function<AutomatonStatisticsEntry, Long> stats2long) {
		long time = 0L;
		for (final AutomatonStatisticsEntry stats : mAutomatonStatistics) {
			time += stats2long.apply(stats);
		}
		return time;
	}

	private static <LETTER extends IIcfgTransition<?>> NestedWordAutomaton<LETTER, IPredicate>
			constructStraightLineAutomaton(final IUltimateServiceProvider services,
					final IRun<LETTER, IPredicate, ?> errorTrace, final VpAlphabet<LETTER> alphabet,
					final PredicateFactoryForInterpolantAutomata predicateFactory) {
		final IInterpolantGenerator ig = new IInterpolantGenerator() {
			@Override
			public boolean isPerfectSequence() {
				throw new UnsupportedOperationException();
			}

			@Override
			public Word<? extends IAction> getTrace() {
				return errorTrace.getWord();
			}

			@Override
			public IPredicateUnifier getPredicateUnifier() {
				throw new UnsupportedOperationException();
			}

			@Override
			public IPredicate getPrecondition() {
				return createPredicate();
			}

			@Override
			public IPredicate getPostcondition() {
				return createPredicate();
			}

			@Override
			public Map<Integer, IPredicate> getPendingContexts() {
				throw new UnsupportedOperationException();
			}

			@Override
			public IPredicate[] getInterpolants() {
				final IPredicate[] list = new IPredicate[errorTrace.getLength()];
				for (int i = 0; i < list.length; ++i) {
					list[i] = createPredicate();
				}
				return list;
			}

			@Override
			public InterpolantComputationStatus getInterpolantComputationStatus() {
				throw new UnsupportedOperationException();
			}

			private IPredicate createPredicate() {
				return new IPredicate() {
					@Override
					public Set<IProgramVar> getVars() {
						throw new UnsupportedOperationException();
					}

					@Override
					public String[] getProcedures() {
						throw new UnsupportedOperationException();
					}

					@Override
					public Term getFormula() {
						throw new UnsupportedOperationException();
					}

					@Override
					public Term getClosedFormula() {
						throw new UnsupportedOperationException();
					}
				};
			}
		};
		return new StraightLineInterpolantAutomatonBuilder<>(services, alphabet, ig, predicateFactory,
				StraightLineInterpolantAutomatonBuilder.InitialAndAcceptingStateMode.ONLY_FIRST_INITIAL_LAST_ACCEPTING)
						.getResult();
	}

	/**
	 * Statistics per error automaton construction.
	 * 
	 * @author Christian Schilling (schillic@informatik.uni-freiburg.de)
	 */
	private static class AutomatonStatisticsEntry {
		private final long mConstructionTime;
		private final int mTraceLength;
		private final long mDifferenceTime;
		private final boolean mAcceptsSingleTrace;

		public AutomatonStatisticsEntry(final long constructionTime, final long differenceTime, final int traceLength,
				final boolean acceptsSingleTrace) {
			mDifferenceTime = differenceTime;
			mConstructionTime = constructionTime;
			mTraceLength = traceLength;
			mAcceptsSingleTrace = acceptsSingleTrace;
		}
	}
}