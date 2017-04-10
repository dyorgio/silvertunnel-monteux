/*
 * SilverTunnel-Monteux Netlib - Java library to easily access anonymity networks
 * Copyright (c) 2009-2012 silvertunnel.org
 *
 * This program is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the Free
 * Software Foundation; either version 2 of the License, or (at your option)
 * any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for
 * more details.
 *
 * You should have received a copy of the GNU General Public License along
 * with this program; if not, see <http://www.gnu.org/licenses/>.
 */

package cf.monteux.silvertunnel.netlib.api.util;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Represent and determine the version number of the current JVM.
 * 
 * @author hapke
 */
public enum JavaVersion
{
	JAVA_1_5("JAVA_1_5"), JAVA_1_6("JAVA_1_6"), JAVA_1_7("JAVA_1_7"), JAVA_1_8("JAVA_1_8"), UNKNOWN(
			"UNKNOWN");

	private final String title;

	/** */
	private static final Logger logger = LogManager.getLogger(JavaVersion.class);
	private static JavaVersion javaVersion;

	private JavaVersion(String title)
	{
		this.title = title;
	}

	@Override
	public String toString()
	{
		return title;
	}

	/**
	 * @return the version number of the current JVM
	 */
	public static JavaVersion getJavaVersion()
	{
		if (javaVersion == null)
		{
			// determine the version
			final String jv = System.getProperty("java.specification.version");
			logger.debug("system prop jv={}", jv);
			if ("1.5".equals(jv))
			{
				javaVersion = JavaVersion.JAVA_1_5;
			}
			else if ("1.6".equals(jv))
			{
				javaVersion = JavaVersion.JAVA_1_6;
			}
            else if ("1.7".equals(jv))
            {
                javaVersion = JavaVersion.JAVA_1_7;
            }
            else if ("1.8".equals(jv))
            {
                javaVersion = JavaVersion.JAVA_1_8;
            }
			else
			{
				javaVersion = JavaVersion.UNKNOWN;
			}

			logger.debug("determined Java Version: {}", javaVersion);
		}

		return javaVersion;
	}
}
