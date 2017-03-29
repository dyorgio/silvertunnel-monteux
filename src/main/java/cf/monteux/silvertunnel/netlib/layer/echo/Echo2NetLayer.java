/*
 * silvertunnel.org Netlib - Java library to easily access anonymity networks
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

package cf.monteux.silvertunnel.netlib.layer.echo;

import java.io.IOException;
import java.util.Map;

import cf.monteux.silvertunnel.netlib.api.NetAddress;
import cf.monteux.silvertunnel.netlib.api.NetAddressNameService;
import cf.monteux.silvertunnel.netlib.api.NetLayer;
import cf.monteux.silvertunnel.netlib.api.NetLayerStatus;
import cf.monteux.silvertunnel.netlib.api.NetServerSocket;
import cf.monteux.silvertunnel.netlib.api.NetSocket;

/**
 * Echo output to input.
 * 
 * Used for educational purposes to demonstrate the NetSocket/NetLayer concept.
 * 
 * @author hapke
 */
public class Echo2NetLayer implements NetLayer
{
	public Echo2NetLayer()
	{
	}

	/** @see NetLayer#createNetSocket(Map, NetAddress, NetAddress) */
	@Override
	public NetSocket createNetSocket(final Map<String, Object> localProperties,
			final NetAddress localAddress, final NetAddress remoteAddress)
			throws IOException
	{
		return new Echo2NetSocket();
	}

	/** @see NetLayer#createNetServerSocket(Map, NetAddress) */
	@Override
	public NetServerSocket createNetServerSocket(
			final Map<String, Object> properties,
			final NetAddress localListenAddress)
	{
		throw new UnsupportedOperationException();
	}

	/** @see NetLayer#getStatus() */
	@Override
	public NetLayerStatus getStatus()
	{
		return NetLayerStatus.READY;
	}

	/** @see NetLayer#waitUntilReady() */
	@Override
	public void waitUntilReady()
	{
		// nothing to do
	}

	/** @see NetLayer#clear() */
	@Override
	public void clear() throws IOException
	{
		// nothing to do
	}

	/** @see NetLayer#getNetAddressNameService() */
	@Override
	public NetAddressNameService getNetAddressNameService()
	{
		throw new UnsupportedOperationException();
	}
}
