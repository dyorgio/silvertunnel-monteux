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

package cf.monteux.silvertunnel.netlib.layer.tor.directory;

import cf.monteux.silvertunnel.netlib.tool.SimpleHttpClientCompressed;
import cf.monteux.silvertunnel.netlib.tool.ConvenientStreamReader;
import cf.monteux.silvertunnel.netlib.tool.ConvenientStreamWriter;
import cf.monteux.silvertunnel.netlib.tool.SimpleHttpClient;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipException;

import org.apache.http.conn.util.InetAddressUtils;
import cf.monteux.silvertunnel.netlib.api.NetLayer;
import cf.monteux.silvertunnel.netlib.api.util.IpNetAddress;
import cf.monteux.silvertunnel.netlib.api.util.TcpipNetAddress;
import cf.monteux.silvertunnel.netlib.layer.control.ControlNetLayer;
import cf.monteux.silvertunnel.netlib.layer.control.ControlParameters;
import cf.monteux.silvertunnel.netlib.layer.tor.api.Fingerprint;
import cf.monteux.silvertunnel.netlib.layer.tor.api.Router;
import cf.monteux.silvertunnel.netlib.layer.tor.api.TorNetLayerStatus;
import cf.monteux.silvertunnel.netlib.layer.tor.common.TCPStreamProperties;
import cf.monteux.silvertunnel.netlib.layer.tor.common.TorConfig;
import cf.monteux.silvertunnel.netlib.layer.tor.util.NetLayerStatusAdmin;
import cf.monteux.silvertunnel.netlib.layer.tor.util.Parsing;
import cf.monteux.silvertunnel.netlib.layer.tor.util.TorException;
import cf.monteux.silvertunnel.netlib.util.StringStorage;
import cf.monteux.silvertunnel.netlib.util.TempfileStringStorage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class maintains a list of the currently known Tor routers. It has the
 * capability to update stats and find routes that fit to certain criteria.
 * 
 * @author Lexi Pimenidis
 * @author Tobias Koelsch
 * @author Andriy Panchenko
 * @author Michael Koellejan
 * @author Johannes Renner
 * @author hapke
 * @author Tobias Boese
 */
public final class Directory
{
	/** */
	private static final Logger LOG = LoggerFactory.getLogger(Directory.class);

	/**
	 * Number of retries to find a route on one recursive stap before falling
	 * back and changing previous node.
	 */
	public static final int RETRIES_ON_RECURSIVE_ROUTE_BUILD = 10;

	/** key to locally cache the authority key certificates. */
	private static final String STORAGEKEY_AUTHORITY_KEY_CERTIFICATES_TXT = "authority-key-certificates.txt";
	/** key to locally cache the consensus. */
	private static final String STORAGEKEY_DIRECTORY_CACHED_CONSENSUS_TXT = "directory-cached-consensus.txt";
	/** key to locally cache the router descriptors. */
	private static final String DIRECTORY_CACHED_ROUTER_DESCRIPTORS = "directory-router-descriptors.cache";

	/** local cache. */
	private final StringStorage stringStorage;
	/** lower layer network layer, e.g. TCP/IP to connect to directory servers. */
	private NetLayer lowerDirConnectionNetLayer;
	/**
	 * collection of all valid Tor server. (all routers that are valid or were
	 * valid in the past)
	 */
	private final Map<Fingerprint, Router> allFingerprintsRouters = Collections.synchronizedMap(new HashMap<Fingerprint, Router>());
	/** the last valid consensus. */
	private DirectoryConsensus directoryConsensus;
    /** List of Guards. */
    private GuardList guardList;
	/** cache: number of running routers in the consensus. */
	private int numOfRunningRoutersInDirectoryConsensus = 0;
	/**
	 * cache of a combination of fingerprintsRouters+directoryConsensus: valid
	 * routers + status.
	 * 
	 * key=identity key
	 */
	private Map<Fingerprint, Router> validRoutersByFingerprint = new HashMap<Fingerprint, Router>();
	/**
	 * Map that has class C address as key, and a HashSet with fingerprints of
	 * Nodes that have IP-Address of that class.
	 */
	private final Map<String, HashSet<Fingerprint>> addressNeighbours;
	/**
	 * HashMap where keys are CountryCodes, and values are HashSets with
	 * fingerprints of Nodes having an IP-address in the specific country.
	 */
	private final Map<String, HashSet<Fingerprint>> countryNeighbours;
	/** HashSet excluded by config nodes. */
	private final HashSet<Fingerprint> excludedNodesByConfig;
	/** SecureRandom generator. */
	private final SecureRandom rnd;

	private volatile boolean updateRunning = false;

	private AuthorityKeyCertificates authorityKeyCertificates;

	private final NetLayerStatusAdmin statusAdmin;

	private static final long ONE_DAY_IN_MS = 1L * 24L * 60L * 60L * 1000L;

	private static final Pattern IPCLASSC_PATTERN = Parsing.compileRegexPattern("(.*)\\.");

    /**
     * Get the list of Guards.
     * @return a guardList object
     */
    public GuardList getGuardList() {
        return guardList;
    }
	/**
	 * Initialize directory to prepare later network operations.
	 */
	public Directory(final StringStorage stringStorage, 
	                 final NetLayer lowerDirConnectionNetLayer, 
					 final NetLayerStatusAdmin statusAdmin)
	{
		// save parameters
		this.stringStorage = stringStorage;
		this.lowerDirConnectionNetLayer = lowerDirConnectionNetLayer;
		this.statusAdmin = statusAdmin;

		// configure special timeout parameters for download of directory information
		final ControlParameters cp = ControlParameters.createTypicalFileTransferParameters();
		cp.setConnectTimeoutMillis(TorConfig.DIR_CONNECT_TIMEOUT_MILLIS);
		cp.setOverallTimeoutMillis(TorConfig.DIR_OVERALL_TIMEOUT_MILLIS);
		cp.setInputMaxBytes(TorConfig.DIR_MAX_FILETRANSFER_BYTES);
		cp.setThroughputTimeframeMinBytes(TorConfig.DIR_THROUGPUT_TIMEFRAME_MIN_BYTES);
		cp.setThroughputTimeframeMillis(TorConfig.DIR_THROUGPUT_TIMEFRAME_MILLIS);
		this.lowerDirConnectionNetLayer = new ControlNetLayer(lowerDirConnectionNetLayer, cp);

		// rest
		addressNeighbours = new HashMap<String, HashSet<Fingerprint>>();
		countryNeighbours = new HashMap<String, HashSet<Fingerprint>>();
		rnd = new SecureRandom();
		excludedNodesByConfig = new HashSet<Fingerprint>(TorConfig.getAvoidedNodeFingerprints());
        guardList = new GuardList(this);
	}

	/**
	 * Add s to addressNeighbours and countryNeighbours.
	 * 
	 * @param r
	 */
	private void addToNeighbours(final Router r)
	{
		HashSet<Fingerprint> neighbours;
		final String ipClassCString = Parsing.parseStringByRE(r.getAddress().getHostAddress(), IPCLASSC_PATTERN, "");

		// add it to the addressNeighbours
		neighbours = addressNeighbours.get(ipClassCString);
		if (neighbours == null)
		{
			// first entry for this ipClassCString
			neighbours = new HashSet<Fingerprint>();
			addressNeighbours.put(ipClassCString, neighbours);
		}
		neighbours.add(r.getFingerprint());

		// add it to the country neighbors
		neighbours = countryNeighbours.get(r.getCountryCode());
		if (neighbours == null)
		{
			// first entry for this s.countryCode
			neighbours = new HashSet<Fingerprint>();
			countryNeighbours.put(r.getCountryCode(), neighbours);
		}
		neighbours.add(r.getFingerprint());
	}

	/**
	 * 
	 * @return true if directory was loaded and enough routers are available
	 */
	public boolean isDirectoryReady()
	{
		if (numOfRunningRoutersInDirectoryConsensus > 0)
		{
			final long minDescriptors = Math.max(Math.round(TorConfig.getMinDescriptorsPercentage() * numOfRunningRoutersInDirectoryConsensus),
													TorConfig.getMinDescriptors());
			return validRoutersByFingerprint.size() > Math.max(minDescriptors, TorConfig.getRouteMinLength());
		}
		else
		{
			// consensus or router details not yet loaded
			return false;
		}
	}

	private static final int MIN_NUM_OF_DIRS = 5;
	private static final int MIN_NUM_OF_CACHE_DIRS = MIN_NUM_OF_DIRS;

	/**
	 * @return all routers that can be used; cached dirs are preferred if they
	 *         are known
	 */
	private Collection<Router> getDirRouters()
	{
		// filter
		Collection<Router> cacheDirs;
		Collection<Router> authorityDirs;
		synchronized (allFingerprintsRouters)
		{
			cacheDirs = new ArrayList<Router>(allFingerprintsRouters.size());
			authorityDirs = new ArrayList<Router>();
			for (final Router r : allFingerprintsRouters.values())
			{
				if (TorConfig.isCountryAllowed(r.getCountryCode()) && r.isValid())
				{
					if (r.isDirv2Authority()) // is this a authority dir?
					{
						authorityDirs.add(r);
					}
					else if (r.isDirv2V2dir()) // is this a dir?
					{
						cacheDirs.add(r);
					}
				}
			}
		}

		// prefer non-authorities
		if (cacheDirs.size() >= MIN_NUM_OF_CACHE_DIRS)
		{
			LOG.debug("using non-authorities");
			return cacheDirs;
		}

		// try authorities
		if (authorityDirs.size() + cacheDirs.size() >= MIN_NUM_OF_DIRS)
		{
			LOG.debug("using authorities");
			final Collection<Router> result = cacheDirs;
			result.addAll(authorityDirs);
			return result;
		}
		LOG.debug("using hard-coded authorities");
		// try predefined/hard-coded authorities
		return AuthorityServers.getAuthorityRouters();
	}

	/**
	 * Poll some known servers, is triggered by TorBackgroundMgmt and directly
	 * after starting.<br>
	 * TODO : Test if things do not break if suddenly servers disappear from the
	 * directory that are currently being used<br>
	 * TODO : Test if servers DO disappear from the directory
	 * 
	 * @return 0 = no update, 1 = v1 update, 2 = v2 update, 3 = v3 update
	 */
	public int refreshListOfServers()
	{
		// Check if there's already an update running
		synchronized (this)
		{
			if (updateRunning)
			{
				LOG.debug("Directory.refreshListOfServers: update already running...");
				return 0;
			}
			updateRunning = true;

			try
			{
				updateNetworkStatusNew();
				// Finish, if some nodes were found
				if (isDirectoryReady())
				{
					updateRunning = false;
					return 3;
				}
				return 0;
			}
			catch (final Exception e)
			{
				LOG.warn("Directory.refreshListOfServers", e);
				return 0;

			}
			finally
			{
				updateRunning = false;
			}
		}
	}
	private final int MIN_LENGTH_OF_CONSENSUS_STR = 100;
	/**
	 * Update the DirectoryConsensus.
	 */
	private void updateDirectoryConsensus()
	{
		//
		// handle consensus
		//
		statusAdmin.updateStatus(TorNetLayerStatus.CONSENSUS_LOADING);

		// pre-check
		final Date now = new Date();
		if (directoryConsensus != null && !directoryConsensus.needsToBeRefreshed(now))
		{
			LOG.debug("no consensus update necessary ...");
		}
		else
		{
			final AuthorityKeyCertificates authorityKeyCertificates = getAuthorityKeyCertificates();

			//
			// first initialization attempt: use cached consensus
			//
			LOG.debug("consensus first initialization attempt: try to use document from local cache ...");
			DirectoryConsensus newDirectoryConsensus = null;
			if (directoryConsensus == null || directoryConsensus.getFingerprintsNetworkStatusDescriptors().size() == 0)
			{
				// first initialization: try to load consensus from cache
				final String newDirectoryConsensusStr = stringStorage.get(STORAGEKEY_DIRECTORY_CACHED_CONSENSUS_TXT);
				if (newDirectoryConsensusStr != null && newDirectoryConsensusStr.length() > MIN_LENGTH_OF_CONSENSUS_STR)
				{
					try
					{
						newDirectoryConsensus = new DirectoryConsensus(newDirectoryConsensusStr, authorityKeyCertificates, now);
						if (newDirectoryConsensus == null || !newDirectoryConsensus.isValid(now))
						{
							// cache result was not acceptable
							newDirectoryConsensus = null;
							LOG.debug("consensus from local cache (is too small and) could not be used");
						}
						else
						{
							LOG.debug("use consensus from local cache");
						}
					}
					catch (final TorException e)
					{
						newDirectoryConsensus = null;
						LOG.debug("consensus from local cache is not valid (e.g. too old) and could not be used");
					}
					catch (final Exception e)
					{
						newDirectoryConsensus = null;
						LOG.debug("error while loading consensus from local cache: {}", e, e);
					}
				}
				else
				{
					newDirectoryConsensus = null;
					LOG.debug("consensus from local cache (is null or invalid and) could not be used");
				}
			}

			//
			// ordinary update: load consensus from Tor network
			//
			LOG.debug("load consensus from Tor network");
			if (newDirectoryConsensus == null)
			{
				// all v3 directory servers
				final List<Router> dirRouters = new ArrayList<Router>(getDirRouters());

				// Choose one randomly
				while (dirRouters.size() > 0)
				{
					final int index = rnd.nextInt(dirRouters.size());
					final Router dirRouter = dirRouters.get(index);
					if (LOG.isDebugEnabled())
					{
						LOG.debug("Directory.updateNetworkStatusNew: Randomly chosen dirRouter to fetch consensus document: " 
								+ dirRouter.getFingerprint()
								+ " (" + dirRouter.getNickname() + ")");
					}
					try
					{
						// TODO : implement https://gitweb.torproject.org/torspec.git/blob/HEAD:/proposals/139-conditional-consensus-download.txt download network status from server
						final String path = "/tor/status-vote/current/consensus";

						String newDirectoryConsensusStr;
                        try
                        {
                            newDirectoryConsensusStr = SimpleHttpClientCompressed.getInstance().get(lowerDirConnectionNetLayer, dirRouter.getDirAddress(), path);
                        }
                        catch (ZipException e)
                        {
                            LOG.debug("got ZipException while downloading DirectoryConsensus trying to fetch it uncompressed.");
                            newDirectoryConsensusStr = SimpleHttpClient.getInstance().get(lowerDirConnectionNetLayer, dirRouter.getDirAddress(), path);
                        }

						// Parse the document
						newDirectoryConsensus = new DirectoryConsensus(newDirectoryConsensusStr, authorityKeyCertificates, now);
						if (!newDirectoryConsensus.needsToBeRefreshed(now))
						{
							// result is acceptable
							LOG.debug("use new consensus");
							// save the directoryConsensus for later
							// Tor-startups
							stringStorage.put(STORAGEKEY_DIRECTORY_CACHED_CONSENSUS_TXT, newDirectoryConsensusStr);
							break;
						}
						newDirectoryConsensus = null;
					}
					catch (final Exception e)
					{
						LOG.warn("Directory.updateNetworkStatusNew Exception", e);
						dirRouters.remove(index);
						newDirectoryConsensus = null;
					}
				}
			}

			// finalize consensus update
			if (newDirectoryConsensus != null)
			{
				directoryConsensus = newDirectoryConsensus;
			}
		}
		// final check whether a new or at least an old consensus is available
		if (directoryConsensus == null)
		{
			LOG.error("no old or new directory consensus available");
			return;
		}
	}
	/**
	 * Update the list of Routers.
	 * @throws TorException
	 */
	private void updateRouterList() throws TorException
	{
		//
		// update router descriptors
		//
		statusAdmin.updateStatus(TorNetLayerStatus.ROUTER_DESCRIPTORS_LOADING);
		if (directoryConsensus != null)
		{
			// update router details
			fetchDescriptors(allFingerprintsRouters, directoryConsensus);

			// merge directoryConsensus&fingerprintsRouters ->
			// validRoutersBy[Fingerprint|Name]
			final Map<Fingerprint, Router> newValidRoutersByfingerprint = new HashMap<Fingerprint, Router>();
			final Map<Fingerprint, Router> newExitnodeRouters = new HashMap<Fingerprint, Router>();
			final Map<Fingerprint, Router> newFastRouters = new HashMap<Fingerprint, Router>();
			final Map<Fingerprint, Router> newGuardRouters = new HashMap<Fingerprint, Router>();
			final Map<Fingerprint, Router> newStableRouters = new HashMap<Fingerprint, Router>();
			final Map<Fingerprint, Router> newStableAndFastRouters = new HashMap<Fingerprint, Router>();
			int newNumOfRunningRoutersInDirectoryConsensus = 0;
			for (final RouterStatusDescription networkStatusDescription : directoryConsensus.getFingerprintsNetworkStatusDescriptors().values())
			{
				// one server of consensus
				final Fingerprint fingerprint = networkStatusDescription.getFingerprint();
				final Router r = allFingerprintsRouters.get(fingerprint);
				if (r != null && r.isValid())
				{
					// valid server with description
					r.updateServerStatus(networkStatusDescription);
					newValidRoutersByfingerprint.put(fingerprint, r);
					addToNeighbours(r);
					if (r.isDirv2Exit() || r.isExitNode())
					{
						newExitnodeRouters.put(fingerprint, r);
					}
					if (r.isDirv2Fast())
					{
						newFastRouters.put(fingerprint, r);
					}
					if (r.isDirv2Guard())
					{
						newGuardRouters.put(fingerprint, r);
					}
					if (r.isDirv2Stable())
					{
						newStableRouters.put(fingerprint, r);
					}
					if (r.isDirv2Fast() && r.isDirv2Stable())
					{
						newStableAndFastRouters.put(fingerprint, r);
					}
				}
				if (networkStatusDescription.getRouterFlags().isRunning())
				{
					newNumOfRunningRoutersInDirectoryConsensus++;
				}
			}
			validRoutersByFingerprint = newValidRoutersByfingerprint;
			// TODO : exchange to incremental updating the list (now we have to wait until all routers are parsed)
			numOfRunningRoutersInDirectoryConsensus = newNumOfRunningRoutersInDirectoryConsensus;

			if (LOG.isDebugEnabled())
			{
				LOG.debug("updated torServers, new size=" + validRoutersByFingerprint.size());
				LOG.debug("number of exit routers : " + newExitnodeRouters.size());
				LOG.debug("number of fast routers : " + newFastRouters.size());
				LOG.debug("number of stable routers : " + newStableRouters.size());
				LOG.debug("number of stable&fast routers : " + newStableAndFastRouters.size());
				LOG.debug("number of guard routers : " + newGuardRouters.size());
			}
			// write server descriptors to local cache
			try
			{
				long startWriteCache = System.currentTimeMillis();
				FileOutputStream fileOutputStream = new FileOutputStream(
				                     TempfileStringStorage.getTempfileFile(DIRECTORY_CACHED_ROUTER_DESCRIPTORS));
                ConvenientStreamWriter convenientStreamWriter = new ConvenientStreamWriter(fileOutputStream);
                convenientStreamWriter.writeInt(validRoutersByFingerprint.size());
				for (Router router : validRoutersByFingerprint.values())
				{
                    router.save(convenientStreamWriter);
				}
				fileOutputStream.close();
				LOG.debug("wrote router descriptors to local cache in {} ms", System.currentTimeMillis() - startWriteCache);
			}
			catch (Exception exception)
			{
				LOG.warn("Could not cache routers due to exception {}", exception, exception);
			}
		}		
	}
	/**
	 * Get a V3 network-status consensus, parse it and initiate downloads of
	 * missing descriptors.
	 *
	 * @throws TorException
	 */
	private synchronized void updateNetworkStatusNew() throws TorException
	{
		updateDirectoryConsensus();
		updateRouterList();
	}

	private final int MIN_LENGTH_OF_AUTHORITY_KEY_CERTS_STR = 100;
	private AuthorityKeyCertificates getAuthorityKeyCertificates()
	{
		// get now+1 day
		final Date now = new Date();
		final Date minValidUntil = new Date(now.getTime() + ONE_DAY_IN_MS);

		if (authorityKeyCertificates == null)
		{
			// loading is needed - try to load authority key certificates from
			// cache first
			LOG.debug("getAuthorityKeyCertificates(): try to load from local cache ...");
			final String authorityKeyCertificatesStr = stringStorage.get(STORAGEKEY_AUTHORITY_KEY_CERTIFICATES_TXT);
			if (authorityKeyCertificatesStr != null && authorityKeyCertificatesStr.length() > MIN_LENGTH_OF_AUTHORITY_KEY_CERTS_STR)
			{
				// parse loaded result
				try
				{
					final AuthorityKeyCertificates newAuthorityKeyCertificates = new AuthorityKeyCertificates(authorityKeyCertificatesStr,
																												minValidUntil);

					// no exception thrown: certificates are OK
					if (newAuthorityKeyCertificates.isValid(minValidUntil))
					{
						LOG.debug("getAuthorityKeyCertificates(): successfully loaded from local cache");
						authorityKeyCertificates = newAuthorityKeyCertificates;
						return authorityKeyCertificates;
					}
					else
					{
						// do not use outdated or invalid certificates from
						// local cache
						LOG.debug("getAuthorityKeyCertificates(): loaded from local cache - but not valid: try (re)load from remote site now");
					}

				}
				catch (final TorException e)
				{
					LOG.warn("getAuthorityKeyCertificates(): could not parse from local cache: try (re)load from remote site now", e);
				}
			}
			else
			{
				LOG.debug("getAuthorityKeyCertificates(): no data in cache: try (re)load from remote site now");
			}
		}

		if (authorityKeyCertificates == null || !authorityKeyCertificates.isValid(minValidUntil))
		{
			// (re)load is needed
			LOG.debug("getAuthorityKeyCertificates(): load and parse authorityKeyCertificates...");
			final List<String> authServerIpAndPorts = new ArrayList<String>(AuthorityServers.getAuthorityIpAndPorts());
			Collections.shuffle(authServerIpAndPorts);
			String httpResponse = null;
			for (final String authServerIpAndPort : authServerIpAndPorts)
			{
				// download authority key certificates
				try
				{
					final TcpipNetAddress hostAndPort = new TcpipNetAddress(authServerIpAndPort);
					final String path = "/tor/keys/all";
                    try
                    {
                        httpResponse = SimpleHttpClientCompressed.getInstance().get(lowerDirConnectionNetLayer, hostAndPort, path);
                    }
                    catch (ZipException e)
                    {
                        LOG.debug("got ZipException trying to get data uncompressed");
                        httpResponse = SimpleHttpClient.getInstance().get(lowerDirConnectionNetLayer, hostAndPort, path);
                    }
					// parse loaded result
					final AuthorityKeyCertificates newAuthorityKeyCertificates = new AuthorityKeyCertificates(httpResponse, minValidUntil);

					// no exception thrown: certificates are OK
					if (newAuthorityKeyCertificates.isValid(minValidUntil))
					{
						LOG.debug("getAuthorityKeyCertificates(): successfully loaded from {}", authServerIpAndPort);
						// save in cache
						stringStorage.put(STORAGEKEY_AUTHORITY_KEY_CERTIFICATES_TXT, httpResponse);
						// use as result
						authorityKeyCertificates = newAuthorityKeyCertificates;
						return authorityKeyCertificates;
					}
					else
					{
						LOG.debug("getAuthorityKeyCertificates(): loaded from {} - but not valid: try next",  authServerIpAndPort);
					}
				}
				catch (final TorException e)
				{
					LOG.warn("getAuthorityKeyCertificates(): could not parse from " + authServerIpAndPort + " result=" + httpResponse
							+ ", try next", e);
				}
				catch (final Exception e)
				{
					if (LOG.isDebugEnabled())
					{
						LOG.debug("getAuthorityKeyCertificates(): error while loading from {}, try next", authServerIpAndPort, e);
					}
				}
			}
			LOG.error("getAuthorityKeyCertificates(): could NOT load and parse authorityKeyCertificates");
			// use outdated certificates if no newer could be retrieved
		}

		return authorityKeyCertificates;
	}
	
	/** split into single server descriptors. */
	private static final Pattern ROUTER_DESCRIPTORS_PATTERN = Pattern.compile("^(router.*?END SIGNATURE-----)", 
	                                                                          Pattern.DOTALL 
	                                                                        + Pattern.MULTILINE 
	                                                                        + Pattern.CASE_INSENSITIVE
	                                                                        + Pattern.UNIX_LINES);

	/**
	 * parse multiple router descriptors from one String.
	 * 
	 * @param routerDescriptors
	 * @return the result; if multiple entries with the same fingerprint are in
	 *         routerDescriptors, the last will be considered
	 */
	protected Map<Fingerprint, Router> parseRouterDescriptors(final String routerDescriptors)
	{
		final long timeStart = System.currentTimeMillis();
		final Map<Fingerprint, Router> result = new HashMap<Fingerprint, Router>();

		final Matcher m = ROUTER_DESCRIPTORS_PATTERN.matcher(routerDescriptors);

		final ExecutorService executor = Executors.newFixedThreadPool(5); // TODO : make threadpool configurable
		
		final Collection<RouterParserCallable> allTasks = new ArrayList<RouterParserCallable>();

		while (m.find())
		{
			allTasks.add(new RouterParserCallable(m.group(1)));
		}
		List<Future<Router>> results = null;
		try
		{
			results = executor.invokeAll(allTasks);
		}
		catch (InterruptedException exception)
		{
			LOG.warn("error while parsing the router descriptors in parallel", exception);
		}
		if (results != null && !results.isEmpty())
		{
			for (Future<Router> item : results)
			{
				Router router = null;
				try
				{
					router = item.get();
				}
				catch (InterruptedException exception)
				{
					LOG.warn("error while parsing the router descriptors in parallel", exception);
				}
				catch (ExecutionException exception)
				{
					LOG.warn("error while parsing the router descriptors in parallel", exception);
				}
				if (router != null)
				{
					result.put(router.getFingerprint(), router);
				}
			}
		}
		if (LOG.isDebugEnabled())
		{
			LOG.debug("parseRouterDescriptors took " + (System.currentTimeMillis() - timeStart) + " ms");
		}
		return result;
	}
	/** Minimum length of the descriptors. */
	private static final int ALL_DESCRIPTORS_STR_MIN_LEN = 1000;
	/** How many routers are allowed to be fetched separately? */
	private static final int THRESHOLD_TO_LOAD_SINGE_ROUTER_DESCRITPTORS = DescriptorFetcher.MAXIMUM_ALLOWED_DIGESTS;

	/**
	 * Trigger download of missing descriptors from directory caches.
	 * 
	 * @param fingerprintsRouters
	 *            will be modified/updated inside this method
	 * @param directoryConsensus
	 *            will be read
	 */
	private void fetchDescriptors(final Map<Fingerprint, Router> fingerprintsRouters,
	                              final DirectoryConsensus directoryConsensus)
																				throws TorException
	{
		final Set<Fingerprint> fingerprintsOfRoutersToLoad = new HashSet<Fingerprint>();

		for (final RouterStatusDescription networkStatusDescription : directoryConsensus.getFingerprintsNetworkStatusDescriptors().values())
		{
			// check one router of the consensus
			final Router r = fingerprintsRouters.get(networkStatusDescription.getFingerprint());
			if (r == null || !r.isValid())
			{
				// router description not yet contained or too old -> load it
				fingerprintsOfRoutersToLoad.add(networkStatusDescription.getFingerprint());
			}
		}

		//
		// load missing descriptors
		//

		// try to load from local cache
		String allDescriptors;
		if (fingerprintsRouters.size() == 0)
		{
			// try to load from local cache
			try
			{
				long startLoadCached = System.currentTimeMillis();
				FileInputStream fileInputStream = new FileInputStream(
				                           TempfileStringStorage.getTempfileFile(DIRECTORY_CACHED_ROUTER_DESCRIPTORS));
				ConvenientStreamReader convenientStreamReader = new ConvenientStreamReader(fileInputStream);
				int count = convenientStreamReader.readInt();
				final Map<Fingerprint, Router> parsedServers = new HashMap<Fingerprint, Router>(count);
				for (int i = 0; i < count; i++)
				{
					Router router = new RouterImpl(convenientStreamReader);
					parsedServers.put(router.getFingerprint(), router);
				}
				fileInputStream.close();
				final Set<Fingerprint> fingerprintsOfRoutersToLoadCopy = new HashSet<Fingerprint>(fingerprintsOfRoutersToLoad);
				for (final Fingerprint fingerprint : fingerprintsOfRoutersToLoadCopy)
				{
					// one searched fingerprint
					final Router r = parsedServers.get(fingerprint);
					if (r != null && r.isValid())
					{
						// found valid descriptor
						fingerprintsRouters.put(fingerprint, r);
						fingerprintsOfRoutersToLoad.remove(fingerprint);
					}
				}
				LOG.debug("loaded {} routers from local cache in {} ms", 
				          new Object[] {fingerprintsRouters.size(), System.currentTimeMillis() - startLoadCached});
			}
			catch (FileNotFoundException exception)
			{
				LOG.debug("no cached routers found");
			}
			catch (Exception exception)
			{
				LOG.warn("could not load cached routers due to exception {}", exception, exception);
			}
		}

		// load from directory server
		LOG.debug("load {} routers from dir server(s) - start", fingerprintsOfRoutersToLoad.size());
		int successes = 0;
		if (fingerprintsOfRoutersToLoad.size() <= THRESHOLD_TO_LOAD_SINGE_ROUTER_DESCRITPTORS)
		{
			// load the descriptors separately
			// TODO: implement it
			final int attempts = fingerprintsOfRoutersToLoad.size();
			LOG.debug("loaded {} of {} missing routers from directory server(s) with multiple requests", successes, attempts);
		}
		else
		{
			// load all description with one request (usually done during startup)
			final List<Router> dirRouters = new ArrayList<Router>(getDirRouters());
			while (dirRouters.size() > 0)
			{
				final int i = rnd.nextInt(dirRouters.size());
				final Router directoryServer = dirRouters.get(i);
				dirRouters.remove(i);
				if (directoryServer.getDirPort() < 1)
				{
					// cannot be used as directory server
					continue;
				}
				allDescriptors = DescriptorFetcher.downloadAllDescriptors(directoryServer, lowerDirConnectionNetLayer);

				// split into single server descriptors
				if (allDescriptors != null && allDescriptors.length() >= ALL_DESCRIPTORS_STR_MIN_LEN)
				{
					final Map<Fingerprint, Router> parsedServers = parseRouterDescriptors(allDescriptors);
					int attempts = 0;
					for (final Fingerprint fingerprint : fingerprintsOfRoutersToLoad)
					{
						// one searched fingerprint
						final Router r = parsedServers.get(fingerprint);
						attempts++;
						if (r != null)
						{
							// found searched descriptor
							fingerprintsRouters.put(fingerprint, r);
							successes++;
						}
					}
					if (LOG.isDebugEnabled())
					{
						LOG.debug("loaded " + successes + " of " 
								+ attempts + " missing routers from directory server \"" 
								+ directoryServer.getNickname()
								+ "\" with single request");
					}
					break;
				}
			}
		}
		LOG.debug("load routers from dir server(s), loaded {} routers - finished", successes);
	}

	/**
	 * Check whether the given route is compatible to the given restrictions.
	 * 
	 * @param route
	 *            a list of servers that form the route
	 * @param sp
	 *            the requirements to the route
	 * @param forHiddenService
	 *            set to TRUE to disregard exitPolicies
	 * @return the boolean result
	 */
	public boolean isCompatible(final Router[] route, final TCPStreamProperties sp, final boolean forHiddenService) throws TorException
	{
		// check for null values
		if (route == null)
		{
			throw new TorException("received NULL-route");
		}
		if (sp == null)
		{
			throw new TorException("received NULL-sp");
		}
		if (route[route.length - 1] == null)
		{
			throw new TorException("route contains NULL at position " + (route.length - 1));
		}
		// empty route is always wrong
		if (route.length < 1)
		{
			return false;
		}
		// route is too short
		if (route.length < sp.getMinRouteLength())
		{
			return false;
		}
		// route is too long
		if (route.length > sp.getMaxRouteLength())
		{
			return false;
		}

		// check compliance with sp.route
		final Fingerprint[] proposedRoute = sp.getProposedRouteFingerprints();
		if (proposedRoute != null)
		{
			for (int i = 0; (i < proposedRoute.length) && (i < route.length); ++i)
			{
				if (proposedRoute[i] != null)
				{
					if (!route[i].getFingerprint().equals(proposedRoute[i]))
					{
						return false;
					}
				}
			}
		}

		if ((!forHiddenService) && (sp.isExitPolicyRequired()))
		{
			// check for exit policies of last node
			return route[route.length - 1].exitPolicyAccepts(sp.getAddr(), sp.getPort());
		}
		else
		{
			return true;
		}
	}

	/**
	 * Exclude related nodes: family, class C and country (if specified in
	 * TorConfig).
	 * 
	 * @param r
	 *            node that should be excluded with all its relations
	 * @return set of excluded node names
	 */
	public Set<Fingerprint> excludeRelatedNodes(final Router r)
	{
		final HashSet<Fingerprint> excludedServerfingerprints = new HashSet<Fingerprint>();
		HashSet<Fingerprint> myAddressNeighbours, myCountryNeighbours;

		if (TorConfig.isRouteUniqueClassC())
		{
			myAddressNeighbours = getAddressNeighbours(r.getAddress().getHostAddress());
			if (myAddressNeighbours != null)
			{
				excludedServerfingerprints.addAll(myAddressNeighbours);
			}
		}
		else
		{
			excludedServerfingerprints.add(r.getFingerprint());
		}

		// exclude all country insider, if desired
		if (TorConfig.isRouteUniqueCountry())
		{
			myCountryNeighbours = countryNeighbours.get(r.getCountryCode());
			if (myCountryNeighbours != null)
			{
				excludedServerfingerprints.addAll(myCountryNeighbours);
			}
		}
		// exclude its family as well
		excludedServerfingerprints.addAll(r.getFamily());

		return excludedServerfingerprints;
	}

	/**
	 * Selecting a random node based on the ranking, excluded Servers, and
	 * fast/stable flag.
	 * 
	 * @param torRouters
	 *            a list of all Routers to choose from
	 * @param excludedServerFingerprints
	 *            a list of all Routers which should be excluded
	 * @param rankingInfluenceIndex
	 *            the ranking influence index
	 * @return a {@link Router}
	 */
	public Router selectRandomNode(final Map<Fingerprint, Router> torRouters,
										final HashSet<Fingerprint> excludedServerFingerprints,
										final float rankingInfluenceIndex,
										final boolean onlyFast,
										final boolean onlyStable)
	{
		Map<Fingerprint, Router> routersToChooseFrom = new HashMap<Fingerprint, Router>(torRouters);
		Set<Fingerprint> listOfExcludedRouters = new HashSet<Fingerprint>(excludedServerFingerprints);
		if (onlyFast)
		{
			for (Router router : routersToChooseFrom.values())
			{
				if (!router.isDirv2Fast())
				{
					listOfExcludedRouters.add(router.getFingerprint());
				}
			}
		}
		if (onlyStable)
		{
			for (Router router : routersToChooseFrom.values())
			{
				if (!router.isDirv2Stable())
				{
					listOfExcludedRouters.add(router.getFingerprint());
				}
			}
		}
		float rankingSum = 0;
		Router myServer;
		listOfExcludedRouters.addAll(excludedNodesByConfig);
		// At first, calculate sum of the rankings
		Iterator<Router> it = routersToChooseFrom.values().iterator();
		while (it.hasNext())
		{
			myServer = it.next();
			if ((!listOfExcludedRouters.contains(myServer.getFingerprint())) && myServer.isDirv2Running())
			{
				rankingSum += myServer.getRefinedRankingIndex(rankingInfluenceIndex);
			}
		}
		// generate a random float between 0 and rankingSum
		float serverRandom = rnd.nextFloat() * rankingSum;
		// select the server
		it = routersToChooseFrom.values().iterator();
		while (it.hasNext())
		{
			myServer = it.next();
			if ((!listOfExcludedRouters.contains(myServer.getFingerprint())) && myServer.isDirv2Running())
			{
				serverRandom -= myServer.getRefinedRankingIndex(rankingInfluenceIndex);
				if (serverRandom <= 0)
				{
					return myServer;
				}
			}
		}
		return null;
	}

	/**
	 * Find a router by the given IP address and onion port.
	 * 
	 * @param ipNetAddress {@link IpNetAddress} of the router
	 * @param onionPort port of the router
	 * @return the router; null if no valid matching router found
	 */
	public Router getValidRouterByIpAddressAndOnionPort(final IpNetAddress ipNetAddress, final int onionPort)
	{
		final TcpipNetAddress check = new TcpipNetAddress(ipNetAddress, onionPort);
		return getValidRouterByIpAddressAndOnionPort(check);
	}
	
	/**
	 * Find a router by the given IP address and onion port.
	 * 
	 * @param tcpipNetAddress {@link TcpipNetAddress} of the router to be searched
	 * @return the router; null if no valid matching router found
	 */
	public Router getValidRouterByIpAddressAndOnionPort(final TcpipNetAddress tcpipNetAddress)
	{
		for (final Router router : getValidRoutersByFingerprint().values())
		{
			if (router.getOrAddress().equals(tcpipNetAddress))
			{
				// router found
				return router;
			}
		}
		// not found
		return null;
	}
	/**
	 * Find a router by the given IP address and dir port.
	 * 
	 * @param tcpipNetAddress {@link TcpipNetAddress} of the router to be searched
	 * @return the router; null if no valid matching router found
	 */
	public Router getValidRouterByIpAddressAndDirPort(final TcpipNetAddress tcpipNetAddress)
	{
		for (final Router router : getValidRoutersByFingerprint().values())
		{
			if (router.getDirAddress().equals(tcpipNetAddress))
			{
				// router found
				return router;
			}
		}
		// not found
		return null;
	}

	/**
	 * @return all valid routers with HSDir flag (hidden server directory),
	 *         ordered by fingerprint
	 */
	public Router[] getValidHiddenDirectoryServersOrderedByFingerprint()
	{
		// copy all hidden server directory to list
		List<Router> routersList;
		synchronized (allFingerprintsRouters)
		{
			routersList = new ArrayList<Router>(allFingerprintsRouters.values());
		}
		for (final Iterator<Router> i = routersList.iterator(); i.hasNext();)
		{
			final Router r = i.next();
			if ((!r.isDirv2HSDir()) || r.getDirPort() < 1) // TODO : check if this logic still applies (see
															// https://gitweb.torproject.org/torspec.git/blob/HEAD:/proposals/185-dir-without-dirport.txt)
			{
				// no hidden server directory: remove it from the list
				i.remove();
			}
		}

		// copy list to array
		final Router[] routers = routersList.toArray(new Router[routersList.size()]);

		// order by fingerprint
		final Comparator<Router> comp = new Comparator<Router>()
		{
			@Override
			public int compare(final Router o1, final Router o2)
			{
				return o1.getFingerprint().compareTo(o2.getFingerprint());
			}
		};
		Arrays.sort(routers, comp);

		return routers;
	}

	/**
	 * Get three directory servers (HSDir) needed to retrieve a hidden service
	 * descriptor.
	 * 
	 * @param f
	 *            hidden service descriptor id fingerprint
	 * @return three consecutive routers that are hidden service directories
	 *         with router.fingerprint&gt;f
	 */
	public Collection<Router> getThreeHiddenDirectoryServersWithFingerprintGreaterThan(final Fingerprint f)
	{
		final Router[] routers = getValidHiddenDirectoryServersOrderedByFingerprint();

		final int REQUESTED_NUM_OF_ROUTERS = 3;
		int numOfRoutersToFind = Math.min(REQUESTED_NUM_OF_ROUTERS, routers.length);
		final Collection<Router> result = new ArrayList<Router>(numOfRoutersToFind);

		// find the first and the consecutive routers
		boolean takeNextRouters = false;
		for (int i = 0; i < 2 * routers.length; i++)
		{
			final Router r = routers[i % routers.length];

			// does it match?
			if (!takeNextRouters && r.getFingerprint().compareTo(f) >= 0)
			{
				// yes
				takeNextRouters = true;
			}

			// take as part of the result?
			if (takeNextRouters)
			{
				// yes
				result.add(r);
				numOfRoutersToFind--;
				if (numOfRoutersToFind <= 0)
				{
					// the end
					break;
				}
				continue;
			}
		}

		return result;
	}

	/**
	 * Return the set of neighbors by address of the specific IP in the dotted
	 * notation.
	 */
	private HashSet<Fingerprint> getAddressNeighbours(final String address)
	{
		final String ipClassCString = Parsing.parseStringByRE(address, IPCLASSC_PATTERN, "");
		final HashSet<Fingerprint> neighbours = addressNeighbours.get(ipClassCString);
		return neighbours;
	}

	/**
	 * should be called when TorJava is closing.
	 */
	public void close()
	{
	}

	/**
	 * for debugging purposes.
	 */
	void print()
	{
		if (LOG.isDebugEnabled())
		{
			for (final Router r : validRoutersByFingerprint.values())
			{
				LOG.debug(r.toString());
			}
		}
	}

	/**
	 * Get Map with all Routers which are valid and not excluded by Config.
	 * @return a Map with valid routers
	 */
	public Map<Fingerprint, Router> getValidRoutersByFingerprint()
	{
		HashMap<Fingerprint, Router> result = new HashMap<Fingerprint, Router>(validRoutersByFingerprint);
		Iterator<Entry<Fingerprint, Router>>itRouter = result.entrySet().iterator();
		while (itRouter.hasNext())
		{
			if (!TorConfig.isCountryAllowed(itRouter.next().getValue().getCountryCode()))
			{
				itRouter.remove();
			}
		}
		return result;
	}

    /**
     * Get a Router by its fingerprint.
     * @param fingerprint
     * @return
     */
    public Router getRouterByFingerprint(final Fingerprint fingerprint) {
        return allFingerprintsRouters.get(fingerprint);
    }
	/**
	 * Get Map with all Routers which are valid and not excluded by Config and matches the given flags.
	 * @return a Map with valid routers
	 */
	public Map<Fingerprint, Router> getValidRoutersByFlags(final RouterFlags flags)
	{
		HashMap<Fingerprint, Router> result = new HashMap<Fingerprint, Router>(validRoutersByFingerprint);
		Iterator<Entry<Fingerprint, Router>>itRouter = result.entrySet().iterator();
		while (itRouter.hasNext())
		{
			Router router = itRouter.next().getValue();
			if (!TorConfig.isCountryAllowed(router.getCountryCode()))
			{
				itRouter.remove();
			}
			else
			{
				if (!router.getRouterFlags().match(flags))
				{
					itRouter.remove();
				}
			}
		}
        LOG.debug("routers found for given flags (" + flags.toString() + ") {}", result.size());
		return result;
	}

	/**
	 * Is the requested destination a dir router?
	 * @param sp {@link TCPStreamProperties} containing the destination infos
	 * @return true if it is a directory server
	 */
	public boolean isDirServer(final TCPStreamProperties sp)
	{
		if (sp.getHostname() != null && InetAddressUtils.isIPv4Address(sp.getHostname()))
		{
			String [] octets = sp.getHostname().split("\\.");
			byte [] ip = new byte [4];
			ip[0] = (byte) Integer.parseInt(octets[0]);
			ip[1] = (byte) Integer.parseInt(octets[1]);
			ip[2] = (byte) Integer.parseInt(octets[2]);
			ip[3] = (byte) Integer.parseInt(octets[3]);

			final Router router = getValidRouterByIpAddressAndDirPort(new TcpipNetAddress(ip, sp.getPort()));
			if (router != null && (router.isDirv2HSDir() || router.isDirv2V2dir()))
			{
				return true;
			}
		}
		return false;
	}
}
