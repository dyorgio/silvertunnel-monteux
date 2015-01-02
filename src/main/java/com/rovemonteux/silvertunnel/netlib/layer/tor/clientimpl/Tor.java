/*
 * OnionCoffee - Anonymous Communication through TOR Network
 * Copyright (C) 2005-2007 RWTH Aachen University, Informatik IV
 * 
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * version 2 as published by the Free Software Foundation.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA
 */
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
/*
 * silvertunnel-ng.org Netlib - Java library to easily access anonymity networks
 * Copyright (c) 2013 silvertunnel-ng.org
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

package com.rovemonteux.silvertunnel.netlib.layer.tor.clientimpl;

import java.io.IOException;
import java.security.Security;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;

import com.rovemonteux.silvertunnel.netlib.api.NetAddress;
import com.rovemonteux.silvertunnel.netlib.api.NetLayer;
import com.rovemonteux.silvertunnel.netlib.api.NetLayerStatus;
import com.rovemonteux.silvertunnel.netlib.api.util.Hostname;
import com.rovemonteux.silvertunnel.netlib.api.util.IpNetAddress;
import com.rovemonteux.silvertunnel.netlib.layer.tor.api.Router;
import com.rovemonteux.silvertunnel.netlib.layer.tor.api.TorNetLayerStatus;
import com.rovemonteux.silvertunnel.netlib.layer.tor.circuit.Circuit;
import com.rovemonteux.silvertunnel.netlib.layer.tor.circuit.CircuitAdmin;
import com.rovemonteux.silvertunnel.netlib.layer.tor.circuit.CircuitsStatus;
import com.rovemonteux.silvertunnel.netlib.layer.tor.circuit.HiddenServicePortInstance;
import com.rovemonteux.silvertunnel.netlib.layer.tor.circuit.TLSConnection;
import com.rovemonteux.silvertunnel.netlib.layer.tor.circuit.TLSConnectionAdmin;
import com.rovemonteux.silvertunnel.netlib.layer.tor.common.TCPStreamProperties;
import com.rovemonteux.silvertunnel.netlib.layer.tor.common.TorConfig;
import com.rovemonteux.silvertunnel.netlib.layer.tor.common.TorEventService;
import com.rovemonteux.silvertunnel.netlib.layer.tor.directory.Directory;
import com.rovemonteux.silvertunnel.netlib.layer.tor.hiddenservice.HiddenServiceProperties;
import com.rovemonteux.silvertunnel.netlib.layer.tor.stream.ClosingThread;
import com.rovemonteux.silvertunnel.netlib.layer.tor.stream.ResolveStream;
import com.rovemonteux.silvertunnel.netlib.layer.tor.stream.StreamThread;
import com.rovemonteux.silvertunnel.netlib.layer.tor.stream.TCPStream;
import com.rovemonteux.silvertunnel.netlib.layer.tor.util.NetLayerStatusAdmin;
import com.rovemonteux.silvertunnel.netlib.layer.tor.util.TorException;
import com.rovemonteux.silvertunnel.netlib.layer.tor.util.TorNoAnswerException;
import com.rovemonteux.silvertunnel.netlib.util.StringStorage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * MAIN CLASS. keeps track of circuits, tls-connections and the status of
 * servers. Provides high level access to all needed functionality, i.e.
 * connecting to some remote service via Tor.
 * 
 * @author Lexi Pimenidis
 * @author Tobias Koelsch
 * @author Vinh Pham
 * @author Andriy Panchenko
 * @author Michael Koellejan
 * @author hapke
 * @author Tobias Boese
 * @author Rove Monteux
 */

public class Tor implements NetLayerStatusAdmin
{
	/** */
	private static final Logger LOG = LoggerFactory.getLogger(Tor.class);

	private static final int TOR_CONNECT_MAX_RETRIES = 10;
	private static final long TOR_CONNECT_MILLISECONDS_BETWEEN_RETRIES = 10;
	private Directory directory;
	private TLSConnectionAdmin tlsConnectionAdmin;
	private TorBackgroundMgmtThread torBackgroundMgmtThread;
	/**
	 * Absolute time in milliseconds: until this date/time the init is in
	 * progress.
	 * 
	 * Used to delay connects until Tor has some time to build up circuits and
	 * stuff.
	 */
	private long startupPhaseWithoutConnects;

	/**
	 * lower layer network layer, e.g. TLS over TCP/IP to connect to TOR onion
	 * routers
	 */
	private final NetLayer lowerTlsConnectionNetLayer;
	/** lower layer network layer, e.g. TCP/IP to connect to directory servers */
	private final NetLayer lowerDirConnectionNetLayer;
	/** storage that can be used, e.g. to cache directory information */
	private final StringStorage stringStorage;
	private final TorEventService torEventService = new TorEventService();

	private boolean gaveMessage = false;
	private boolean startUpInProgress = true;

	private NetLayerStatus status = TorNetLayerStatus.NEW;

	/**
	 * Initialize Tor with all defaults.
	 * 
	 * @exception IOException
	 */
	public Tor(final NetLayer lowerTlsConnectionNetLayer, final NetLayer lowerDirConnectionNetLayer, StringStorage stringStorage) throws IOException
	{
		this.lowerTlsConnectionNetLayer = lowerTlsConnectionNetLayer;
		this.lowerDirConnectionNetLayer = lowerDirConnectionNetLayer;
		this.stringStorage = stringStorage;
		initLocalSystem(false);
		initDirectory();
		initRemoteAccess();
	}

	private void initLocalSystem(final boolean noLocalFileSystemAccess) throws IOException
	{
		// install BC, if not already done
		if (Security.getProvider("BC") == null)
		{
			Security.addProvider(new org.bouncycastle.jce.provider.BouncyCastleProvider());
			// Security.insertProviderAt(new
			// org.bouncycastle.jce.provider.BouncyCastleProvider(),2);
		}
		// logger and config
		LOG.info("Tor implementation of silvertunnel-monteux.org is starting up");
		// determine end of startup-Phase
		startupPhaseWithoutConnects = System.currentTimeMillis() + TorConfig.getStartupDelay() * 1000L;
		// init event-handler
	}

	private void initDirectory() throws IOException
	{
		directory = new Directory(stringStorage, lowerDirConnectionNetLayer, this);
	}

	private void initRemoteAccess() throws IOException
	{
		// establish handler for TLS connections
		tlsConnectionAdmin = new TLSConnectionAdmin(lowerTlsConnectionNetLayer);
		// initialize thread to renew every now and then
		torBackgroundMgmtThread = new TorBackgroundMgmtThread(this);
	}

	/**
	 * @return read-only view of the currently valid Tor routers
	 */
	public Collection<Router> getValidTorRouters()
	{
		final Collection<Router> resultBase = directory.getValidRoutersByFingerprint().values();
		final Collection<Router> result = new ArrayList<Router>(resultBase.size());

		// copy all routers to the result collection
		for (final Router r : resultBase)
		{
			result.add(r.cloneReliable());
		}

		return result;
	}

	/**
	 * makes a connection to a remote service.
	 * 
	 * @param sp
	 *            hostname, port to connect to and other stuff
	 * @param torNetLayer
	 * @return some socket-thing
	 */
	public TCPStream connect(final TCPStreamProperties sp, final NetLayer torNetLayer) throws Throwable
	{
		if (sp.getHostname() == null && sp.getAddr() == null)
		{
			throw new IOException("Tor: no hostname and no address provided");
		}

		// check, if tor is still in startup-phase
		checkStartup();

		// check whether the address is hidden
		if (sp.getHostname() != null && sp.getHostname().endsWith(".onion"))
		{
			return HiddenServiceClient.connectToHiddenService(directory, torEventService, tlsConnectionAdmin, torNetLayer, sp);
		}

		// connect to exit server
		int retry = 0;
		String hostnameAddress = null;
		final int minIdleCircuits = Math.min(2, TorConfig.getMinimumIdleCircuits());
		for (; retry <= TOR_CONNECT_MAX_RETRIES; retry++)
		{
			// check precondition
			waitForIdleCircuits(minIdleCircuits);

			// action
			final Circuit[] circuits = CircuitAdmin.provideSuitableCircuits(tlsConnectionAdmin, directory, sp, torEventService, false);
			if (circuits == null || circuits.length < 1)
			{
				LOG.debug("no valid circuit found: wait for new one created by the TorBackgroundMgmtThread");
				try
				{
					Thread.sleep(TorBackgroundMgmtThread.INTERVAL_S * 1000L);
				}
				catch (final InterruptedException e)
				{
					LOG.debug("got IterruptedException : {}", e.getMessage(), e);
				}
				continue;
			}
			
			
			
			if (TorConfig.isVeryAggressiveStreamBuilding())
			{

				for (int j = 0; j < circuits.length; ++j)
				{
					// start N asynchronous stream building threads
					try
					{
						final StreamThread[] streamThreads = new StreamThread[circuits.length];
						for (int i = 0; i < circuits.length; ++i)
						{
							streamThreads[i] = new StreamThread(circuits[i], sp);
						}
						// wait for the first stream to return
						int chosenStream = -1;
						int waitingCounter = TorConfig.queueTimeoutStreamBuildup * 1000 / 10;
						while (chosenStream < 0 && waitingCounter >= 0)
						{
							boolean atLeastOneAlive = false;
							for (int i = 0; i < circuits.length && chosenStream < 0; ++i)
							{
								if (!streamThreads[i].isAlive())
								{
									if (streamThreads[i].getStream() != null && streamThreads[i].getStream().isEstablished())
									{
										chosenStream = i;
									}
								}
								else
								{
									atLeastOneAlive = true;
								}
							}
							if (!atLeastOneAlive)
							{
								break;
							}

							final long SLEEPING_MS = 10;
							try
							{
								Thread.sleep(SLEEPING_MS);
							}
							catch (final InterruptedException e)
							{
								LOG.debug("got IterruptedException : {}", e.getMessage(), e);
							}

							--waitingCounter;
						}
						// return one and close others
						if (chosenStream >= 0)
						{
							final TCPStream returnValue = streamThreads[chosenStream].getStream();
							new ClosingThread(streamThreads, chosenStream);
							return returnValue;
						}
					}
					catch (final Exception e)
					{
						LOG.warn("Tor.connect(): " + e.getMessage());
						return null;
					}
				}

			}
			else
			{
				// build serial N streams, stop if successful
				for (int i = 0; i < circuits.length; ++i)
				{
					try
					{
						return new TCPStream(circuits[i], sp);
					}
					catch (final TorNoAnswerException e)
					{
						LOG.warn("Tor.connect: Timeout on circuit:" + e.getMessage());
					}
					catch (final TorException e)
					{
						LOG.warn("Tor.connect: TorException trying to reuse existing circuit:" + e.getMessage(), e);
					}
					catch (final IOException e)
					{
						LOG.warn("Tor.connect: IOException " + e.getMessage());
					}
				}
			}

			hostnameAddress = (sp.getAddr() != null) ? "" + sp.getAddr() : sp.getHostname();
			LOG.info("Tor.connect: not (yet) connected to " + hostnameAddress + ":" + sp.getPort() + ", full retry count=" + retry);
			try
			{
				Thread.sleep(TOR_CONNECT_MILLISECONDS_BETWEEN_RETRIES);
			}
			catch (final InterruptedException e)
			{
				LOG.debug("got IterruptedException : {}", e.getMessage(), e);
			}
		}
		hostnameAddress = (sp.getAddr() != null) ? "" + sp.getAddr() : sp.getHostname();
		throw new IOException("Tor.connect: unable to connect to " + hostnameAddress + ":" + sp.getPort() + " after " + retry + " full retries with "
				+ sp.getConnectRetries() + " sub retries");
	}

	/**
	 * initializes a new hidden service.
	 * 
	 * @param service
	 *            all data needed to init the things
	 */
	public void provideHiddenService(final NetLayer torNetLayerToConnectToDirectoryService,
									 final HiddenServiceProperties service,
									 final HiddenServicePortInstance hiddenServicePortInstance) throws IOException, TorException
	{
		// check, if tor is still in startup-phase
		checkStartup();

		// action
		HiddenServiceServer.getInstance().provideHiddenService(directory, 
		                                                       torEventService, 
		                                                       tlsConnectionAdmin,
		                                                       torNetLayerToConnectToDirectoryService, 
		                                                       service, 
		                                                       hiddenServicePortInstance);
	}

	/**
	 * shut down everything.
	 * 
	 * @param force
	 *            set to true, if everything shall go fast. For graceful end,
	 *            set to false
	 */
	public void close(final boolean force)
	{
		LOG.info("TorJava ist closing down");
		// shutdown mgmt
		torBackgroundMgmtThread.close();
		// shut down connections
		tlsConnectionAdmin.close(force);
		// shutdown directory
		directory.close();
		// close hidden services
		// TODO close hidden services
		// kill logger
		LOG.info("Tor.close(): CLOSED");
	}

	/** synonym for close(false). */
	public void close()
	{
		close(false);
	}

    /**
     * Anonymously resolve a host name.
     *
     * @param hostname
     *            the host name
     * @return the resolved IP; null if no mapping found
     */
    public List<NetAddress> resolveAll(final String hostname) throws Throwable
    {
        return resolveInternal(hostname);
    }

    /**
     * Anonymously resolve a host name.
     *
     * @param hostname
     *            the host name
     * @return the resolved IP; null if no mapping found
     */
    public IpNetAddress resolve(final String hostname) throws Throwable
    {
        return (IpNetAddress) resolveInternal(hostname).get(0);
    }

	/**
	 * Anonymously do a reverse look-up.
	 * 
	 * @param addr
	 *            the IP address to be resolved
	 * @return the host name; null if no mapping found
	 */
	public String resolve(final IpNetAddress addr) throws Throwable
	{
		// build address (works only for IPv4!)
		final byte[] a = addr.getIpaddress();
		final StringBuffer sb = new StringBuffer();
		for (int i = 0; i < 4; ++i)
		{
			sb.append((a[3 - i]) & 0xff);
			sb.append('.');
		}
		sb.append("in-addr.arpa");
		// resolve address
		final List<NetAddress> o = resolveInternal(sb.toString());
		if (o.get(0) instanceof Hostname)
		{
			return ((Hostname) o.get(0)).getHostname();
		}
		else
		{
			return null;
		}
	}

	/**
	 * internal function to use the tor-resolve-functionality.
	 * 
	 * @param query
	 *            a hostname to be resolved, or for a reverse lookup:
	 *            A.B.C.D.in-addr.arpa
	 * @return either an IpNetAddress (normal query), or a String
	 *         (reverse-DNS-lookup)
	 */
	private List<NetAddress> resolveInternal(final String query) throws Throwable
	{
		try
		{
			// check, if tor is still in startup-phase
			checkStartup();
			// try to resolve query over all existing circuits
			// so iterate over all TLS-Connections
			for (final TLSConnection tls : tlsConnectionAdmin.getConnections())
			{
				// and over all circuits in each TLS-Connection
				for (final Circuit circuit : tls.getCircuits())
				{
					try
					{
						if (circuit.isEstablished())
						{
							// if an answer is given, we're satisfied
							final ResolveStream rs = new ResolveStream(circuit);
							final List<NetAddress> o = rs.resolve(query);
							rs.close();
							return o;
						}
					}
					catch (final Exception e)
					{
						// in case of error, do nothing, but retry with the next
						// circuit
						LOG.debug("got Exception : {}", e.getMessage(), e);
					}
				}
			}
			// if no circuit could give an answer (possibly there was no
			// established circuit?)
			// build a new circuit and ask this one to resolve the query
//			final ResolveStream rs = new ResolveStream(new Circuit(tlsConnectionAdmin, directory, new TCPStreamProperties(), torEventService));
			final TCPStreamProperties streamProperties = new TCPStreamProperties();
			final Circuit [] rsCircuit = CircuitAdmin.provideSuitableCircuits(tlsConnectionAdmin, 
																			  directory, 
																			  streamProperties, 
																			  torEventService, 
																			  false);
			final ResolveStream rs = new ResolveStream(rsCircuit[0]);
			final List<NetAddress> o = rs.resolve(query);
			rs.close();
			return o;
		}
		catch (final TorException e)
		{
			throw new IOException("Error in Tor: " + e.getMessage());
		}
	}

	@Override
	public void setStatus(final NetLayerStatus newStatus)
	{
		LOG.debug("TorNetLayer old status: {}", status);
		status = newStatus;
		LOG.info("TorNetLayer new status: {}", status);
	}

	/**
	 * Set the new status, but only, if the new readyIndicator is higher than
	 * the current one.
	 * 
	 * @param newStatus
	 */
	@Override
	public void updateStatus(final NetLayerStatus newStatus)
	{
		if (getStatus().getReadyIndicator() < newStatus.getReadyIndicator())
		{
			setStatus(newStatus);
		}
	}

	@Override
	public NetLayerStatus getStatus()
	{
		return status;
	}

	/**
	 * make sure that tor had some time to read the directory and build up some
	 * circuits.
	 */
	public void checkStartup()
	{
		// start up is proved to be over
		if (!startUpInProgress)
		{
			return;
		}

		// check if startup is over
		final long now = System.currentTimeMillis();
		if (now >= startupPhaseWithoutConnects)
		{
			startUpInProgress = false;
			return;
		}

		// wait for startup to be over
		final long sleep = startupPhaseWithoutConnects - System.currentTimeMillis();
		if (!gaveMessage)
		{
			gaveMessage = true;
			LOG.debug("Tor.checkStartup(): Tor is still in startup phase, sleeping for max. {} seconds",  sleep / 1000L);
			LOG.debug("Tor not yet started - wait until torServers available");
		}
		// try { Thread.sleep(sleep); }
		// catch(Exception e) {}

		// wait until server info and established circuits are available
		waitForIdleCircuits(TorConfig.getMinimumIdleCircuits());
		try
		{
			Thread.sleep(500);
		}
		catch (final Exception e)
		{ /* ignore it */
			LOG.debug("got Exception : {}", e.getMessage(), e);
		}
		LOG.info("Tor start completed!!!");
		startUpInProgress = false;
	}

	/**
	 * Wait until Tor has at least minExpectedIdleCircuits idle circuits.
	 * 
	 * @param minExpectedIdleCircuits
	 *            minimum expected idling circuits
	 */
	private void waitForIdleCircuits(final int minExpectedIdleCircuits)
	{
		// wait until server info and established circuits are available
		while (!directory.isDirectoryReady() || getCircuitsStatus().getCircuitsEstablished() < minExpectedIdleCircuits)
		{
			try
			{
				Thread.sleep(100);
			}
			catch (final Exception e)
			{ /* ignore it */
				LOG.debug("got Exception : {}", e.getMessage(), e);
			}
		}
	}

	/**
	 * returns a set of current established circuits (only used by
	 * TorJava.Proxy.MainWindow to get a list of circuits to display).
	 * 
	 */
	public HashSet<Circuit> getCurrentCircuits()
	{

		final HashSet<Circuit> allCircs = new HashSet<Circuit>();
		for (final TLSConnection tls : tlsConnectionAdmin.getConnections())
		{
			for (final Circuit circuit : tls.getCircuits())
			{
				// if (circuit.established && (!circuit.closed)){
				allCircs.add(circuit);
				// }
			}
		}
		return allCircs;
	}

	/**
	 * @return status summary of the Ciruits
	 */
	public CircuitsStatus getCircuitsStatus()
	{
		// count circuits
		int circuitsTotal = 0; // all circuits
		int circuitsAlive = 0; // circuits that are building up, or that are established
		int circuitsEstablished = 0; // established, but not already closed
		int circuitsClosed = 0; // closing down

		for (final TLSConnection tls : tlsConnectionAdmin.getConnections())
		{
			for (final Circuit c : tls.getCircuits())
			{
				String flag = "";
				++circuitsTotal;
				if (c.isClosed())
				{
					flag = "C";
					++circuitsClosed;
				}
				else
				{
					flag = "B";
					++circuitsAlive;
					if (c.isEstablished())
					{
						flag = "E";
						++circuitsEstablished;
					}
				}
//				if (LOG.isDebugEnabled())
//				{
//					LOG.debug("Tor.getCircuitsStatus(): " + flag + " rank " + c.getRanking() + " fails " + c.getStreamFails() + " of "
//							+ c.getStreamCounter() + " TLS " + tls.getRouter().getNickname() + "/" + c.toString());
//				}
			}
		}

		final CircuitsStatus result = new CircuitsStatus();
		result.setCircuitsTotal(circuitsTotal);
		result.setCircuitsAlive(circuitsAlive);
		result.setCircuitsEstablished(circuitsEstablished);
		result.setCircuitsClosed(circuitsClosed);

		return result;
	}

	/**
	 * Remove the current history. Close all circuits that were already be used.
	 */
	public void clear()
	{
		CircuitAdmin.clear(tlsConnectionAdmin);
	}

	// /////////////////////////////////////////////////////
	// getters and setters
	// /////////////////////////////////////////////////////

	public TorEventService getTorEventService()
	{
		return torEventService;
	}

	/**
	 * @return the {@link Directory} of all known Routers.
	 */
	public Directory getDirectory()
	{
		return directory;
	}

	public TLSConnectionAdmin getTlsConnectionAdmin()
	{
		return tlsConnectionAdmin;
	}

	public NetLayer getLowerTlsConnectionNetLayer()
	{
		return lowerTlsConnectionNetLayer;
	}

	public NetLayer getLowerDirConnectionNetLayer()
	{
		return lowerDirConnectionNetLayer;
	}
}
