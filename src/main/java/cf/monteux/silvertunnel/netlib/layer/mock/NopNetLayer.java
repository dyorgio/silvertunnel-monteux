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

package cf.monteux.silvertunnel.netlib.layer.mock;

import java.io.IOException;
import java.util.Map;

import cf.monteux.silvertunnel.netlib.api.NetAddress;
import cf.monteux.silvertunnel.netlib.api.NetAddressNameService;
import cf.monteux.silvertunnel.netlib.api.NetLayer;
import cf.monteux.silvertunnel.netlib.api.NetLayerStatus;
import cf.monteux.silvertunnel.netlib.api.NetServerSocket;
import cf.monteux.silvertunnel.netlib.api.NetSocket;
import cf.monteux.silvertunnel.netlib.nameservice.mock.NopNetAddressNameService;

/**
 * Simple NetLayer that always fails to create a Net(Server)Socket.
 * 
 * @author hapke
 */
public class NopNetLayer implements NetLayer
{
	/** @see NetLayer#createNetSocket(Map, NetAddress, NetAddress) */
	@Override
	public synchronized NetSocket createNetSocket(
			Map<String, Object> localProperties, NetAddress localAddress,
			NetAddress remoteAddress) throws IOException
	{
		throw new IOException(
				"NopNetLayer.createNetSocket() always throws this IOException");
	}

	/** @see NetLayer#createNetServerSocket(Map, NetAddress) */
	@Override
	public NetServerSocket createNetServerSocket(
			Map<String, Object> properties, NetAddress localListenAddress)
			throws IOException
	{
		throw new IOException(
				"NopNetLayer.createNetServerSocket() always throws this IOException");
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

	/** @see NetLayer#getNetAddressNameService */
	@Override
	public NetAddressNameService getNetAddressNameService()
	{
		return NopNetAddressNameService.getInstance();
	}
}
