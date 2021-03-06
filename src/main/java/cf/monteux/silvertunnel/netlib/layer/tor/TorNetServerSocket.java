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
/*
 * SilverTunnel-Monteux Netlib - Java library to easily access anonymity networks
 * Copyright (c) 2014-2015 Rove Monteux
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

package cf.monteux.silvertunnel.netlib.layer.tor;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.Scanner;

import cf.monteux.silvertunnel.netlib.api.NetServerSocket;
import cf.monteux.silvertunnel.netlib.api.NetSocket;
import cf.monteux.silvertunnel.netlib.layer.tor.circuit.Circuit;
import cf.monteux.silvertunnel.netlib.layer.tor.circuit.HiddenServiceInstance;
import cf.monteux.silvertunnel.netlib.layer.tor.circuit.HiddenServicePortInstance;
import cf.monteux.silvertunnel.netlib.layer.tor.stream.TCPStream;
import cf.monteux.silvertunnel.netlib.layer.tor.util.TorException;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * NetServerSocket of Layer over Tor network to provide a hidden service.
 * 
 * @author hapke
 * @author Rove Monteux
 */
public class TorNetServerSocket implements NetServerSocket,
		HiddenServicePortInstance
{
	/** */
	private static final Logger logger = LogManager.getLogger(TorNetServerSocket.class);

	private static final int SERVER_QUEUE_MAX_SIZE = 10;
	private final BlockingQueue<TCPStream> streams = new ArrayBlockingQueue<TCPStream>(
			SERVER_QUEUE_MAX_SIZE, false);
	/** info used for toString(). */
	private final String info;
	private final int port;
	private boolean closed = false;
	private HiddenServiceInstance hiddenServiceInstance;
	private String hostname;
	private String shortHostname;

	/**
	 * Create a new TorNetServerSocket.
	 * 
	 * @param info
	 *            info used for toString()
	 * @param port
	 *            listening port
	 */
	public TorNetServerSocket(String info, int port)
	{
		this.info = info;
		this.port = port;
	}

	@Override
	public String toString()
	{
		return "TorNetServerSocket(info=" + info + ", port=" + port + ")";
	}

	// /////////////////////////////////////////////////////
	// methods to implement TorNetServerSocket
	// /////////////////////////////////////////////////////

	@Override
	public NetSocket accept() throws IOException
	{
		logger.info("accept() called");

		TCPStream nextStream = null;
		try
		{
			nextStream = streams.take();
		}
		catch (final InterruptedException e)
		{
			logger.warn("waiting interrupted", e);
		}
		logger.info("accept() got stream from queue nextStream=" + nextStream);

		return new TorNetSocket(nextStream,
				"TorNetLayer accepted server connection");
	}

	@Override
	public void close() throws IOException
	{
		closed = true;
	}

	// /////////////////////////////////////////////////////
	// methods to implement HiddenServicePortInstance
	// /////////////////////////////////////////////////////

	@Override
	public int getPort()
	{
		return port;
	}

	@Override
	public boolean isOpen()
	{
		return !closed;
	}

	/**
	 * Create a new (TCP)Stream and assign it to the circuit+streamId specified.
	 * 
	 * @param circuit
	 * @param streamId
	 */
	@Override
	public void createStream(Circuit circuit, int streamId)
			throws TorException, IOException
	{
		logger.debug("addStream() called");
		final TCPStream newStream = new TCPStream(circuit, streamId);
		try
		{
			streams.put(newStream);
		}
		catch (final InterruptedException e)
		{
			logger.warn("waiting interrupted", e);
		}
	}

	@Override
	public HiddenServiceInstance getHiddenServiceInstance()
	{
		return hiddenServiceInstance;
	}

	@Override
	public void setHiddenServiceInstance(
			HiddenServiceInstance hiddenServiceInstance)
	{
		this.hiddenServiceInstance = hiddenServiceInstance;
		try {
			this.hostname = new Scanner(new File("service"+File.separator+"hostname")).useDelimiter("\\Z").next();
			this.shortHostname = this.hostname.replaceAll(".onion", "");
		} catch (FileNotFoundException e) {
			logger.error(e.getMessage());
			e.printStackTrace();
		}
	}

	@Override
	public String getHostname() {
		return this.hostname;
	}

	@Override
	public String getShortHostname() {
		return this.shortHostname;
	}
}
