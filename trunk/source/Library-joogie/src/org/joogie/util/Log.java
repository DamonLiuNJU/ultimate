/*
 * Joogie translates Java bytecode to the Boogie intermediate verification language
 * Copyright (C) 2011 Martin Schaef and Stephan Arlt
 * 
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */

package org.joogie.util;

import org.apache.log4j.Logger;

/**
 * Log
 * 
 * @author arlt
 */
public class Log {

	private static Logger sLogger;

	public static void init(final Logger logger) {
		if (logger != null) {
			throw new IllegalStateException("Logger already initialized");
		}
		assert logger != null;
		sLogger = logger;
	}

	/**
	 * Singleton method
	 * 
	 * @return Logger object
	 */
	public static Logger v() {
		if (null == sLogger) {
			throw new IllegalStateException("Logger not initialized");
		}
		return sLogger;
	}

	/**
	 * Log a message object with the DEBUG Level.
	 * 
	 * @param o
	 *            the message object to log
	 */
	public static void debug(Object o) {
		v().debug(o);
	}

	/**
	 * Log a message object with the INFO Level.
	 * 
	 * @param o
	 *            the message object to log
	 */
	public static void info(Object o) {
		v().info(o);
	}

	/**
	 * Log a message object with the ERROR Level.
	 * 
	 * @param o
	 *            the message object to log
	 */
	public static void error(Object o) {
		v().error(o);
	}

	/**
	 * C-tor
	 */
	private Log() {
		// do nothing
	}
}
