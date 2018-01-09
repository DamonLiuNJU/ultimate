/*
 * Copyright (C) 2016 Daniel Dietsch (dietsch@informatik.uni-freiburg.de)
 * Copyright (C) 2016 Yu-Wen Chen (yuwenchen1105@gmail.com
 * Copyright (C) 2016 Alexander Nutz (nutz@informatik.uni-freiburg.de)
 * Copyright (C) 2016 University of Freiburg
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
package de.uni_freiburg.informatik.ultimate.plugins.analysis.abstractinterpretationv2.domain.transformula.vp;

import java.util.Map.Entry;

import de.uni_freiburg.informatik.ultimate.core.lib.results.StatisticsResult;
import de.uni_freiburg.informatik.ultimate.core.model.preferences.IPreferenceProvider;
import de.uni_freiburg.informatik.ultimate.core.model.services.ILogger;
import de.uni_freiburg.informatik.ultimate.core.model.services.IUltimateServiceProvider;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.absint.IAbstractDomain;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.absint.IAbstractInterpretationResult;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.absint.IAbstractPostOperator;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.absint.IAbstractStateBinaryOperator;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.absint.vpdomain.EqConstraintFactory;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.absint.vpdomain.EqNode;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.absint.vpdomain.EqNodeAndFunctionFactory;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.absint.vpdomain.WeqSettings;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.cfg.CfgSmtToolkit;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.cfg.IIcfgSymbolTable;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.cfg.structure.IIcfgTransition;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.cfg.structure.IcfgLocation;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.smt.managedscript.ManagedScript;
import de.uni_freiburg.informatik.ultimate.plugins.analysis.abstractinterpretationv2.Activator;

/**
 * Domain of variable partition.
 *
 *
 * @author Yu-Wen Chen (yuwenchen1105@gmail.com)
 * @author Alexander Nutz (nutz@informatik.uni-freiburg.de)
 */
public class VPDomain<ACTION extends IIcfgTransition<IcfgLocation>>
		implements IAbstractDomain<EqState, ACTION> {

	public static final boolean DEBUG = true;

	private final EqPostOperator<ACTION> mPost;
	private final VPMergeOperator mMerge;
	private final ILogger mLogger;

	private final ManagedScript mManagedScript;
	private final IIcfgSymbolTable mSymboltable;
	private final boolean mDebugMode = true;

	private final EqConstraintFactory<EqNode> mEqConstraintFactory;
	private final EqNodeAndFunctionFactory mEqNodeAndFunctionFactory;
	private final EqStateFactory mEqStateFactory;
	private final CfgSmtToolkit mCsToolkit;
	private final IUltimateServiceProvider mServices;

	private final VPDomainBenchmark mBenchmark;

	public VPDomain(final ILogger logger, final IUltimateServiceProvider services, final CfgSmtToolkit csToolkit) {
		mLogger = logger;
		mManagedScript = csToolkit.getManagedScript();
		mMerge = new VPMergeOperator();
		mSymboltable = csToolkit.getSymbolTable();
		mCsToolkit = csToolkit;
		mServices = services;


		final IPreferenceProvider ups = mServices.getPreferenceProvider(Activator.PLUGIN_ID);

		mEqNodeAndFunctionFactory = new EqNodeAndFunctionFactory(services, mManagedScript);
		mEqConstraintFactory = new EqConstraintFactory<>(mEqNodeAndFunctionFactory, mServices, mManagedScript,
				prepareWeqSettings(ups), mDebugMode);
		mEqStateFactory = new EqStateFactory(mEqNodeAndFunctionFactory, mEqConstraintFactory, mSymboltable,
				mManagedScript);

		mPost = new EqPostOperator<>(mServices, mLogger, mCsToolkit, mEqNodeAndFunctionFactory, mEqConstraintFactory,
				mEqStateFactory);

		mBenchmark = new VPDomainBenchmark();
	}

	private WeqSettings prepareWeqSettings(final IPreferenceProvider ups) {
		final WeqSettings settings = new WeqSettings();
		settings.setUseFullWeqccDuringProjectaway(ups.getBoolean(VPDomainPreferences.LABEL_USE_WEQ_IN_PROJECT));
		return settings;
	}

	@Override
	public IAbstractStateBinaryOperator<EqState> getWideningOperator() {
		return mMerge;
	}

	@Override
	public IAbstractPostOperator<EqState, ACTION> getPostOperator() {
		return mPost;
	}

	private final class VPMergeOperator implements IAbstractStateBinaryOperator<EqState> {
		@Override
		public EqState apply(final EqState first, final EqState second) {
			return first.union(second);
		}
	}

	public ManagedScript getManagedScript() {
		return mManagedScript;
	}

	public ILogger getLogger() {
		return mLogger;
	}

	public IIcfgSymbolTable getSymbolTable() {
		return mSymboltable;
	}

	@Override
	public EqState createTopState() {
		return mEqStateFactory.getTopState();
	}

	@Override
	public EqState createBottomState() {
		return mEqStateFactory.getBottomState();
	}

	public boolean isDebugMode() {
		return mDebugMode;
	}

	public EqStateFactory getEqStateFactory() {
		return mEqStateFactory;
	}

	@Override
	public boolean useHierachicalPre() {
		return true;
	}

	@Override
	public <LOC> void afterFixpointComputation(
			final IAbstractInterpretationResult<EqState, ACTION, LOC> result) {

		/*
		 * report VPDomainBenchmark
		 */
		mBenchmark.setLocationsCounter(result.getLoc2SingleStates().keySet().size());
		for (final Entry<LOC, EqState> l2s : result.getLoc2SingleStates().entrySet()) {
			mBenchmark.reportStatsForLocation(l2s.getValue().getConstraint()::getStatistics);
		}

		mBenchmark.setTransitionsCounter(
				mPost.getTransformulaConverterCache().getAllTransitionRelations().size());
		for (final EqTransitionRelation tr : mPost.getTransformulaConverterCache().getAllTransitionRelations()) {
			mBenchmark.reportStatsForTransitionRelation(tr::getStatistics);
		}

		mServices.getResultService().reportResult(Activator.PLUGIN_ID,
				new StatisticsResult<>(Activator.PLUGIN_ID, "ArrayEqualityDomainStatistics", mBenchmark));

		/*
		 * report EqConstraintFactory's Benchmark
		 */
		mServices.getResultService().reportResult(Activator.PLUGIN_ID,
				new StatisticsResult<>(Activator.PLUGIN_ID, "EqConstraintFactoryStatistics",
						mEqConstraintFactory.getBenchmark()));
	}


}
