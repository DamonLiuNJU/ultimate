/*
 * Copyright (C) 2016 Daniel Dietsch (dietsch@informatik.uni-freiburg.de)
 * Copyright (C) 2016 University of Freiburg
 *
 * This file is part of the ULTIMATE Model Checker Utils Library.
 *
 * The ULTIMATE Model Checker Utils Library is free software: you can
 * redistribute it and/or modify it under the terms of the GNU Lesser General
 * Public License as published by the Free Software Foundation, either
 * version 3 of the License, or (at your option) any later version.
 *
 * The ULTIMATE Model Checker Utils Library is distributed in the hope that it
 * will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty
 * of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with the ULTIMATE Model Checker Utils Library. If not,
 * see <http://www.gnu.org/licenses/>.
 *
 * Additional permission under GNU GPL version 3 section 7:
 * If you modify the ULTIMATE Model Checker Utils Library, or any covered work,
 * by linking or combining it with Eclipse RCP (or a modified version of
 * Eclipse RCP), containing parts covered by the terms of the Eclipse Public
 * License, the licensors of the ULTIMATE Model Checker Utils Library grant you
 * additional permission to convey the resulting work.
 */
package de.uni_freiburg.informatik.ultimate.smtsolver.external;

import java.io.IOException;
import java.util.Formatter;

import de.uni_freiburg.informatik.ultimate.core.model.services.ILogger;
import de.uni_freiburg.informatik.ultimate.smtinterpol.LogProxy;

/**
 * This wrapper allows you to use an Ultimate {@link ILogger} instead of SMTInterpols {@link LogProxy}.
 *
 * @author Daniel Dietsch (dietsch@informatik.uni-freiburg.de)
 */
public class SmtInterpolLogProxyWrapper implements LogProxy {
	
	private final ILogger mLogger;
	
	public SmtInterpolLogProxyWrapper(final ILogger logger) {
		mLogger = logger;
	}
	
	@Override
	public void setLoglevel(final int level) {
		// we ignore changes to our log level from the outside
	}
	
	@Override
	public int getLoglevel() {
		if (mLogger.isDebugEnabled()) {
			return LOGLEVEL_DEBUG;
		} else if (mLogger.isInfoEnabled()) {
			return LOGLEVEL_INFO;
		} else if (mLogger.isWarnEnabled()) {
			return LOGLEVEL_WARN;
		} else if (mLogger.isErrorEnabled()) {
			return LOGLEVEL_ERROR;
		} else if (mLogger.isFatalEnabled()) {
			return LOGLEVEL_FATAL;
		} else {
			return LOGLEVEL_OFF;
		}
	}
	
	@Override
	public boolean isFatalEnabled() {
		return mLogger.isFatalEnabled();
	}
	
	@Override
	public void fatal(final String msg, final Object... params) {
		if (params.length == 0) {
			fatal(msg);
		}
		fatal(convert(msg, params));
	}
	
	private String convert(final String msg, final Object... params) {
		// I do not think that this is correct, but I do it as I see it in SMTInterpols DefaultLogger
		final StringBuilder sb = new StringBuilder();
		final Formatter formatter = new Formatter(sb);
		formatter.format(msg, params);
		formatter.close();
		return sb.toString();
	}
	
	@Override
	public void fatal(final Object msg) {
		mLogger.fatal(msg);
	}
	
	@Override
	public void outOfMemory(final String msg) {
		// we log OOM as fatal
		mLogger.fatal(msg);
	}
	
	@Override
	public boolean isErrorEnabled() {
		return mLogger.isErrorEnabled();
	}
	
	@Override
	public void error(final String msg, final Object... params) {
		if (params.length == 0) {
			error(msg);
		}
		error(convert(msg, params));
	}
	
	@Override
	public void error(final Object msg) {
		mLogger.error(msg);
	}
	
	@Override
	public boolean isWarnEnabled() {
		return mLogger.isWarnEnabled();
	}
	
	@Override
	public void warn(final String msg, final Object... params) {
		if (params.length == 0) {
			warn(msg);
		}
		warn(convert(msg, params));
	}
	
	@Override
	public void warn(final Object msg) {
		mLogger.warn(msg);
	}
	
	@Override
	public boolean isInfoEnabled() {
		return mLogger.isInfoEnabled();
	}
	
	@Override
	public void info(final String msg, final Object... params) {
		if (params.length == 0) {
			info(msg);
		}
		info(convert(msg, params));
	}
	
	@Override
	public void info(final Object msg) {
		mLogger.info(msg);
	}
	
	@Override
	public boolean isDebugEnabled() {
		return mLogger.isDebugEnabled();
	}
	
	@Override
	public void debug(final String msg, final Object... params) {
		if (params.length == 0) {
			debug(msg);
		}
		debug(convert(msg, params));
	}
	
	@Override
	public void debug(final Object msg) {
		mLogger.debug(msg);
	}
	
	@Override
	public boolean isTraceEnabled() {
		// we do not support the trace level
		return false;
	}
	
	@Override
	public void trace(final String msg, final Object... params) {
		// we do not support the trace level
	}
	
	@Override
	public void trace(final Object msg) {
		// we do not support the trace level
	}
	
	@Override
	public boolean canChangeDestination() {
		return false;
	}
	
	@Override
	public void changeDestination(final String newDest) throws IOException {
		// we can never change the destination
	}
	
	@Override
	public String getDestination() {
		// we just say we log to stdout because we support many more destinations and they are not controlled by the
		// logger itself
		return "stdout";
	}
	
	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + (mLogger == null ? 0 : mLogger.hashCode());
		return result;
	}
	
	@Override
	public boolean equals(final Object obj) {
		if (this == obj) {
			return true;
		}
		if (obj == null) {
			return false;
		}
		if (getClass() != obj.getClass()) {
			return false;
		}
		final SmtInterpolLogProxyWrapper other = (SmtInterpolLogProxyWrapper) obj;
		if (mLogger == null) {
			if (other.mLogger != null) {
				return false;
			}
		} else if (!mLogger.equals(other.mLogger)) {
			return false;
		}
		return true;
	}
}
