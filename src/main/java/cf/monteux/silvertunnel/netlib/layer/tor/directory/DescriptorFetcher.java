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
package cf.monteux.silvertunnel.netlib.layer.tor.directory;

import java.util.List;

import cf.monteux.silvertunnel.netlib.api.NetLayer;
import cf.monteux.silvertunnel.netlib.api.util.TcpipNetAddress;
import cf.monteux.silvertunnel.netlib.layer.tor.api.Router;
import cf.monteux.silvertunnel.netlib.tool.SimpleHttpClientCompressed;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

//TODO : modify DescriptorFetcher to download the and split (and process) the descriptors during download

/**
 * Descriptor-Fetcher Class. 
 * Implements the different ways of fetching the server descriptors.
 * 
 * @author hapke
 * @author Tobias Boese
 */
public final class DescriptorFetcher
{
	/** */
	private static final Logger logger = LogManager.getLogger(DescriptorFetcher.class);
	/** How many digests can be downloaded at once? */
	public static final int MAXIMUM_ALLOWED_DIGESTS = 96;
	/**
	 * Download the descriptors for the given digests.
	 * 
	 * @param nodesDigestsToLoad the digests of the router descriptor in hex-notation (as list)
	 * @param directoryServer which server should be used for fetching the descriptor(s)
	 * @param dirConnectionNetLayer which {@link NetLayer} should be used for communication
	 * @return the descriptors as single String; null in the case of an error
	 */
	public static String downloadDescriptorsByDigest(final List<String> nodesDigestsToLoad,
	                                                 final RouterStatusDescription directoryServer,
	                                                 final NetLayer dirConnectionNetLayer)
	{
		if (nodesDigestsToLoad == null || nodesDigestsToLoad.isEmpty())
		{
			logger.warn("executing downloadDescriptorsByDigest without descriptors doesnt make sense.");
			return null;
		}
		if (nodesDigestsToLoad.size() > MAXIMUM_ALLOWED_DIGESTS)
		{
			logger.error("only {} digests can be downloaded at once", MAXIMUM_ALLOWED_DIGESTS);
			return null;
		}
		StringBuilder builder = new StringBuilder();
		for (String digest : nodesDigestsToLoad)
		{
			builder.append(digest).append('+');
		}
		
		// download descriptor(s)
		try
		{
			final String path = "/tor/server/d/" + builder.substring(0, builder.length() - 1);
			final TcpipNetAddress hostAndPort = new TcpipNetAddress(directoryServer.getIp(), directoryServer.getDirPort());

			final String httpResponse = SimpleHttpClientCompressed.getInstance().get(dirConnectionNetLayer, hostAndPort, path);
			return httpResponse;

		}
		catch (final Exception e)
		{
			if (logger.isDebugEnabled())
			{
				logger.debug("downloadSingleDescriptor() from "
					+ directoryServer.getNickname() + " failed: "
					+ e.getMessage(), e);
			}
			return null;
		}
	}

	/**
	 * Download all descriptors.
	 * 
	 * @param directoryServer the chosen Server which should be contacted for fetching the descriptors
	 * @param dirConnectionNetLayer the {@link NetLayer} to be used for contacting the server
	 * @return the descriptors as String; null in the case of an error
	 */
	public static String downloadAllDescriptors(final Router directoryServer,
	                                            final NetLayer dirConnectionNetLayer)
	{
		// download descriptor(s)
		try
		{
			final String path = "/tor/server/all";

			final String httpResponse = SimpleHttpClientCompressed.getInstance().get(dirConnectionNetLayer,
			                                                                         directoryServer.getDirAddress(), 
			                                                                         path);
			return httpResponse;

		}
		catch (final Exception e)
		{
			if (logger.isDebugEnabled())
			{
				logger.debug("downloadAllDescriptors() from "
					+ directoryServer.getNickname() + " failed: "
					+ e.getMessage(), e);
			}
			return null;
		}
	}
}
