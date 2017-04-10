/*
 * SilverTunnel-Monteux Netlib - Java library to easily access anonymity networks
 * Copyright (c) 2009-2012 silvertunnel.org
 * Copyright (c) 2017 Rove Monteux
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

package cf.monteux.silvertunnel.netlib.util;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.regex.Pattern;

import cf.monteux.silvertunnel.netlib.layer.tor.common.TorConfig;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * This class stores String in text files in the temp directory of the operating
 * system. Filenames are: st-[key]
 * 
 * @author hapke
 * @author Rove Monteux
 */
public final class TempfileStringStorage implements StringStorage
{
	private static final Logger logger = LogManager.getLogger("TorTest");

	private static final Pattern KEY_PATTERN = Pattern.compile("[a-z0-9\\_\\-\\.]+");

	static
	{
		logger.debug("TempfileStringStorage directory={}", TorConfig.getTempDirectory());
	}

	private static TempfileStringStorage instance = new TempfileStringStorage();

	/**
	 * @return singleton instance
	 */
	public static TempfileStringStorage getInstance()
	{
		return instance;
	}

	protected TempfileStringStorage()
	{
	}

	/**
	 * Store a value.
	 * 
	 * @param key
	 *            a valid key (see interface doc for details)
	 * @param value
	 *            a not null ASCII String; non-ASCII characters are not
	 *            guaranteed to be stored correctly
	 * @throws IllegalArgumentException
	 */
	@Override
	public synchronized void put(final String key, final String value)
			throws IllegalArgumentException
	{
		// parameter check
		if (key == null)
		{
			throw new IllegalArgumentException("key=null");
		}
		if (!KEY_PATTERN.matcher(key).matches())
		{
			throw new IllegalArgumentException("invalid characters in key="
					+ key);
		}
		if (value == null)
		{
			throw new IllegalArgumentException("value=null");
		}

		// action
		try
		{
			FileUtil.writeFile(getTempfileFile(key), value);
		}
		catch (final Exception e)
		{
			logger.warn("could not write value for key=" + key, e);
		}
	}

	/**
	 * Retrieve a value.
	 * 
	 * @param key
	 *            a valid key (see interface doc for details)
	 * @return the values; null if no value found
	 */
	@Override
	public synchronized String get(final String key)
	{
		// parameter check
		if (key == null)
		{
			throw new IllegalArgumentException("key=null");
		}
		if (!KEY_PATTERN.matcher(key).matches())
		{
			throw new IllegalArgumentException("invalid characters in key="
					+ key);
		}

		// action
		try
		{
			return FileUtil.readFile(getTempfileFile(key));
		}
		catch (final FileNotFoundException e)
		{
			return null;
		}
		catch (final Exception e)
		{
			logger.warn("could not read value for key=" + key, e);
			return null;
		}
	}

	// /////////////////////////////////////////////////////
	// internal helper methods
	// /////////////////////////////////////////////////////

	/**
	 * Create the file(path) for a key.
	 * 
	 * @throws IOException
	 */
	public static File getTempfileFile(final String key) throws IOException
	{
		final String prefix = TorConfig.FILENAME_PREFIX + key;

		// do not use: File.createTempFile(prefix, "");
		// it add the process id to the file name
		// which prevents file exchange between multiple startups
		return new File(TorConfig.getTempDirectory(), prefix);
	}
}
