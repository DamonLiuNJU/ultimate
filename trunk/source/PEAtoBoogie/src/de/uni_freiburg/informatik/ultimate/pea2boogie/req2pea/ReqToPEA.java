/*
 * Copyright (C) 2013-2015 Jochen Hoenicke (hoenicke@informatik.uni-freiburg.de)
 * Copyright (C) 2015 University of Freiburg
 *
 * This file is part of the ULTIMATE PEAtoBoogie plug-in.
 *
 * The ULTIMATE PEAtoBoogie plug-in is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * The ULTIMATE PEAtoBoogie plug-in is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with the ULTIMATE PEAtoBoogie plug-in. If not, see <http://www.gnu.org/licenses/>.
 *
 * Additional permission under GNU GPL version 3 section 7:
 * If you modify the ULTIMATE PEAtoBoogie plug-in, or any covered work, by linking
 * or combining it with Eclipse RCP (or a modified version of Eclipse RCP),
 * containing parts covered by the terms of the Eclipse Public License, the
 * licensors of the ULTIMATE PEAtoBoogie plug-in grant you additional permission
 * to convey the resulting work.
 */
package de.uni_freiburg.informatik.ultimate.pea2boogie.req2pea;

import java.io.FileInputStream;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.github.jhoenicke.javacup.runtime.Symbol;

import de.uni_freiburg.informatik.ultimate.core.model.services.ILogger;
import de.uni_freiburg.informatik.ultimate.core.model.services.IUltimateServiceProvider;
import de.uni_freiburg.informatik.ultimate.lib.pea.PhaseEventAutomata;
import de.uni_freiburg.informatik.ultimate.lib.pea.modelchecking.J2UPPAALConverter;
import de.uni_freiburg.informatik.ultimate.lib.pea.reqcheck.PatternToPEA;
import de.uni_freiburg.informatik.ultimate.lib.srparse.ReqParser;
import de.uni_freiburg.informatik.ultimate.lib.srparse.pattern.PatternType;
import de.uni_freiburg.informatik.ultimate.pea2boogie.PeaResultUtil;

public class ReqToPEA {
	private final ILogger mLogger;
	private final IUltimateServiceProvider mServices;
	private final PeaResultUtil mResult;

	public ReqToPEA(final IUltimateServiceProvider services, final ILogger logger) {
		mLogger = logger;
		mServices = services;
		mResult = new PeaResultUtil(mLogger, mServices);
	}

	public List<PatternType> genPatterns(final String reqFileName) throws Exception {
		final FileInputStream fis = new FileInputStream(reqFileName);
		try {
			final ReqParser parser = new ReqParser(mServices, mLogger, fis, reqFileName);
			final Symbol goal = parser.parse();
			final PatternType[] patterns = (PatternType[]) goal.value;

			return Arrays.asList(patterns);
		} finally {
			fis.close();
		}
	}

	public Map<PatternType, PhaseEventAutomata> genPEA(final List<PatternType> patterns,
			final Map<String, Integer> id2bounds) {
		final Map<PatternType, PhaseEventAutomata> req2automata = new LinkedHashMap<>();
		final PatternToPEA peaTrans = new PatternToPEA(mLogger);
		for (final PatternType pat : patterns) {

			final PhaseEventAutomata pea;
			try {
				pea = pat.transformToPea(peaTrans, id2bounds);
			} catch (final Exception ex) {
				final String reason = ex.getMessage() == null ? ex.getClass().toString() : ex.getMessage();
				mResult.transformationError(pat, reason);
				continue;
			}
			if (pea.getInit().length == 0) {
				mResult.transformationError(pat, "No initial phase");
				continue;
			}
			final PhaseEventAutomata old = req2automata.put(pat, pea);
			if (old != null) {
				throw new AssertionError();
			}
		}

		return req2automata;
	}

	public void genPEAforUPPAAL(final PatternType[] patterns, final String xmlFilePath,
			final Map<String, Integer> id2bounds) {

		PhaseEventAutomata pea = null;

		final PatternToPEA peaTrans = new PatternToPEA(mLogger);

		for (final PatternType pat : patterns) {
			if (pea == null) {
				pea = pat.transformToPea(peaTrans, id2bounds);

			} else {
				final PhaseEventAutomata pea2 = pat.transformToPea(peaTrans, id2bounds);
				if (pea2 == null) {
					continue;
				}
				pea = pea.parallel(pea2);

			}
		}
		final J2UPPAALConverter uppaalConverter = new J2UPPAALConverter();
		uppaalConverter.writePEA2UppaalFile(xmlFilePath, pea);
	}
}
