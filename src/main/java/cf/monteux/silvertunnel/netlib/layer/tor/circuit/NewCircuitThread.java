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

package cf.monteux.silvertunnel.netlib.layer.tor.circuit;

import cf.monteux.silvertunnel.netlib.layer.tor.common.TCPStreamProperties;
import cf.monteux.silvertunnel.netlib.layer.tor.common.TorEventService;
import cf.monteux.silvertunnel.netlib.layer.tor.directory.Directory;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Create Circuits in background.
 * 
 * @author hapke
 */
public class NewCircuitThread extends Thread
{
	/** */
	private static final Logger logger = LogManager.getLogger(NewCircuitThread.class);

	/** {@link TLSConnectionAdmin}. */
	private final TLSConnectionAdmin fnh;
	/** the {@link Directory} used for creating a route. */
	private final Directory dir;
	/** the Stream properties. */
	private final TCPStreamProperties spFinal;
	/** the {@link TorEventService}. */
	private final TorEventService torEventService;

	/**
	 * Create a new Thread which creates a new Circuit.
	 * 
	 * @param fnh the {@link TLSConnectionAdmin} object
	 * @param dir the tor {@link Directory} object
	 * @param spFinal the {@link TCPStreamProperties} object
	 * @param torEventService the {@link TorEventService}
	 */
	public NewCircuitThread(final TLSConnectionAdmin fnh, 
							final Directory dir,
							final TCPStreamProperties spFinal, 
							final TorEventService torEventService)
	{
		this.fnh = fnh;
		this.dir = dir;
		this.spFinal = spFinal;
		this.torEventService = torEventService;
	}

	@Override
	public final void run()
	{
		try
		{
			new Circuit(fnh, dir, spFinal, torEventService, null); // TODO : check if this (null) is ok and working correctly with CircuitHistory
		}
		catch (final Exception e)
		{
			logger.warn("unexpected", e);
		}
	}
}
